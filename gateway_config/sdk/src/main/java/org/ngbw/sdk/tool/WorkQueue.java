
/*
 * WorkQueue.java
 */
package org.ngbw.sdk.tool;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.core.shared.TaskRunStage;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.database.StatisticsEvent;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskLogMessage;


/**
 * 
 * @author Terri Liebowitz Schwartz
 *
 */

public class WorkQueue 
{
	private static final Log log = LogFactory.getLog(WorkQueue.class);

	/*
		task must have jobhandle in it.
	*/
	public static  RunningTask postWork(Task task, Tool tool, String chargeNumber, Long predictedSus)
        throws Exception
    {

        RunningTask rt = new RunningTask(
								task, 
								tool.getToolResource().getId(), 
								Workbench.getInstance().getProperties().getProperty("application.instance"),
								chargeNumber, 
								predictedSus);
		TaskLogMessage newMessage = new TaskLogMessage(task);
		newMessage.setStage(TaskRunStage.QUEUE);
		newMessage.setMessage("Added to COSMIC2 run queue.");
		task.logMessages().add(newMessage);
		task.setStage(TaskRunStage.QUEUE);

		// This saves both running task and task in a single transaction.  Also creates and stores
		// a new Statistics record.
        rt.save();

        return rt;
    }

	/*
		This is called by submitJobD
	*/
	public static RunningTask updateWork(RunningTask rt, Tool tool, RenderedCommand command )
        throws Exception, ClassNotFoundException, IllegalArgumentException, InvocationTargetException,
        IOException, SQLException
    {
        String description = OutputDescription.serialize(command.getOutputFileMap(), tool.getToolConfig());
        String commandLine = StringUtils.join(command.getCommand(), " ");

        rt.setOutputDesc(description);
        rt.setCommandline(commandLine);
        rt.setSprops(SchedulerProperties.properties2String(command.getSchedulerProperties()));

		StatisticsEvent se = new StatisticsEvent(rt.getJobhandle()); 
		se.setEventName("PARAMETERS_VALIDATED");

		rt.setStatisticsEvent(se); 
        rt.save();

        return rt;
    }

	/*
		Called by submitJobD after staging and initiating the job.  There are several cases to consider:

		1. SSHProcessWorker and LocalProcessWorker have already *finished* running the job by the time
		this is called.  They will have deleted the RunningTask record.  We check for that with rt.isNew()
		which is set in rt.delete().

		2.  With other process workers, the job may have already finished and issued curl command to 
		servlet which calls WorkQueue.markDone.  In that case we don't want to set rt status *back* to
		SUBMITTED.  RunningTask.changeStatus() takes care of that, quietly ignoring requests to set
		status back to an  an earlier state.   We do need to set remoteJobId though.  LoadResults doesn't
		process job until we set remoteJobId.


		TODO: For ease of querying we may want to add remoteJobId and datesubmitted to statistics record ?
	*/
	public static void submitted(RunningTask rt, String remoteJobId) throws Exception
	{
		boolean changedStatus;
		if (!rt.isNew())
		{
			/* 
				Once we've set remoteJobId and something (maybe curl command) has set status = TERMINATED
				loadResults can process this record.  LoadResults requires remoteJobId != null to avoid
				processing SSHProcessWorker jobs.
			*/
			rt.setRemoteJobId(remoteJobId);
			StatisticsEvent se = new StatisticsEvent(rt.getJobhandle());
			se.setEventName("SUBMITTED_TO_RESOURCE");
			se.setEventValue(remoteJobId);
			rt.setStatisticsEvent(se);
			rt.save();

			/*
				Putting remote job id in job_stats and in job_events.  I added it to job_stats
				to simplify queries and to be able to continue using the same basic queries we
				have been using all along.  It's also in job_events since I think it might be nice
				to be able to look at a list of all events for a job and see the timing of things 
				that happened.
			*/
			Statistics s = Statistics.find(rt.getJobhandle());
			s.setRemoteJobId(remoteJobId);
			s.setDateSubmitted(new Date());
			s.save();


			/*
				This won't actually change the status if status has already been set to TERMINATED or
				if LoadResults has processed and removed the runningtask record.  However, if things
				are running with the kind of timing I'd expect, this will set rt.status = SUBMITTED
				before the remote job completes, and later, either checkJobs or curl command will 
				change status to TERMINATED and loadResults will then process the record.
			*/
			RunningTask.changeStatus(rt.getJobhandle(), RunningTask.STATUS_SUBMITTED);

			String msg = "Submitted to " + rt.getResource() + " as job '" + remoteJobId + "'.";
			TaskUpdater.logProgress(rt, TaskRunStage.SUBMITTED, msg); 
			log.debug(msg);
		}
	}


	// This is called by checkJobs (run in checkJobsD daemon)
    public static void markDone(List<RunningTask> finished_tasks) throws Exception
    {
        for (RunningTask rt: finished_tasks)
        {
            markDone(rt);
        }
    }

	/*
    	This is called by the method above, and also by TaskUpdate.java (TaskUpdate.action servlet)
		or in the rest api, via CipresAdmin.updateJob(), when remote job script does a curl upon completion.  
		It's possible that RT is already gone.  For example, if 
		 	1. checkJobs selects jobs with rt.status = SUBMITTED, and composes qstat command 
		 	2. meanwhile, curl command marks one of the jobs done
		 	3. then, loadResults gets results and deletes RT (or fatal error and deletes RT).
		 	4. then, checkJobs goes to mark job done and finds RT gone..
	*/
    public static void markDone(RunningTask rt) throws Exception
    {
        log.debug("Job Terminated Running. Marking running task done: " + rt.rtToString());
        if (RunningTask.changeStatus(rt.getJobhandle(), RunningTask.STATUS_TERMINATED) == false)
        {
            log.debug("RunningTask changeStatus denied for " + rt.rtToString() +
                ", must already be TERMINATED, or gone.");
        } 
    }


    public static void fail(RunningTask rt, TaskRunStage stage, String message) throws Exception
	{
		try
		{
			// Changes to Statistics, Task, RunningTask and addition of StatisticsEvent all happen 
			// in one transaction in rt.delete().
			StatisticsEvent se = new StatisticsEvent(rt.getJobhandle()); 
			se.setTaskStage(stage.toString());
			se.setEventName("FAILED");
			se.setEventValue(message);
			rt.setStatisticsEvent(se);	// will be stored in rt.delete();

			// If job hasn't been submitted to remote system then zero out it's predicted sus.
			Statistics statistics = Statistics.find(rt.getJobhandle());
			if (rt.getRemoteJobId() == null && statistics.getSuPredicted() != 0L)
			{
				statistics.setSuPredicted(0);
			}
			rt.setStatistics(statistics);

			TaskUpdater.logFatal(rt, stage, message, false); // will be stored in rt.delete();
		}
		catch(Exception e)
		{
			log.debug("", e);
		}
		try
		{
			log.debug("Job Failed. About to delete running task " + rt.getJobhandle());
			rt.delete();
		}
		catch(Exception e)
		{
			// todo: probably don't need to log here
			//log.error("update running task error");
			throw e;
		}	
	}

    public static void finish(RunningTask rt) throws Exception
	{
		try
		{
			StatisticsEvent se = new StatisticsEvent(rt.getJobhandle()); 
			se.setEventName("FINISHED");
			rt.setStatisticsEvent(se); // will be stored in rt.delete()

			/*
				last parameter (false), tells TaskUpdater not to save the task.
				It will be saved in rt.delete() as part of same transaction.
			*/
			TaskUpdater.logProgress(rt, TaskRunStage.COMPLETED, 
				"Output files retrieved.  Task is finished." , false); 
		}
		catch(Exception e)
		{
			log.debug("", e);
		}
		try
		{
			log.debug("Job Finished, results stored. About to delete running task " + rt.getJobhandle());
			rt.delete();
		}
		catch(Exception e)
		{
			throw e;
		}
	}

	// Called right before we try to send qdel to remote host
	public static void noteCancellationAttempt(String jobhandle, String taskStage) throws Exception
	{
		StatisticsEvent se = new StatisticsEvent(jobhandle);
		se.setTaskStage(taskStage);
		se.setEventName("CANCEL");
		se.save();
	}

	public static boolean tryAgain(RunningTask rt, String rtstatus, int maxAttempts)
	{
		long attempts;
		try
		{
			attempts = RunningTask.incrementAttempts(rt.getJobhandle(), rtstatus);

			StatisticsEvent se = new StatisticsEvent(rt.getJobhandle());
			se.setEventName("INCREMENT_ATTEMPTS_FOR_STATUS_" + rtstatus);
			se.setEventValue(Long.toString(attempts));
			se.save();
			if (attempts > maxAttempts)
			{
				return false;
			}
			return true;
		}	
		catch (Exception e)
		{
			log.error("Unable to increment attempts for " + rt.rtToString(), e);
			/* An exception most likely indicates a problem connecting to the db.  Might as well
			allow more retries */
			return true;
		}
	}
	
}




