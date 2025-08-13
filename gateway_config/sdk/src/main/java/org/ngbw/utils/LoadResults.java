package org.ngbw.utils;

import java.util.concurrent.*;
import java.util.Date;
import java.util.List;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.database.RunningTask;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.NDC;
import java.util.Vector;
import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.DriverConnectionSource;
import org.ngbw.sdk.tool.TaskMonitor;


/**
	Standalone program to retrieve the results for all running_tasks that are done
	but that haven't had their results stored yet.

	System Properties:
		submitter = get results for this submitter id only (see submitter column in db, normally
		the url of the web site that submitted)

		recover = n where n is greater than 0, means only process jobs where we've already
		tried and failed, and n is the max number of attempts to make.  Job's retry count is 
		incremented on each failure.  When running with recover = n, after a failure we check 
		to see how many failure's there have been.  When it's >= n, we set 
		RunningTask.status = RunningTask.STATUS_FAILED and task.isOK = false.
		If we run with recover=n twice a day, then setting n = 20 means we'll keep trying for 10 days
		(except for the messed up situation where one iteration of this program takes more than 12 hours
		and is thus killed when we try to start the next iteration, or where one iteration takes more
		than a day ...) 

		local = if non null, this is the path to a local directory that will be used as the parent 
		of the working dir to recover or load results.  Only runs one pass, like recover. NOT
		IMPLEMENTED.

		age = only relevant if recover is true.  This is the maximum number days since submission.
		Won't recover results for older jobs.  NOT IMPLEMENTED.  Could be an alternative to using
		recover= n = retry count.

		TODO: this is not meant to be used with SSHProcessWorker jobs which should run to completion
		in submitJobD and should not be retried.   How do we omit those jobs from selection here?
		Answer: there's no remote_job_id in those runningtask records.
*/
public class LoadResults
{
	private static final Log log = LogFactory.getLog(LoadResults.class.getName());
	private static ThreadPoolExecutor threadPool = null;
	Vector<String> inProgressList = new Vector<String>();
	private static String m_submitter;
	private static String m_default_submitter;
	private static long m_poll_interval;
	private static int m_pool_size;
	private static int m_recover = 0;
	private static String  m_local;
	private static int m_age = 0;

	// this number is kind of arbitrary, see use of threshold in the code below.
	private static int threshold;

	public LoadResults() throws Exception
	{
		threadPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(m_pool_size);
	}

	public static void main(String[] args)
	{
		try
		{
			Workbench wb = Workbench.getInstance();
			m_default_submitter = wb.getProperties().getProperty("application.instance");
			String p;
			p = wb.getProperties().getProperty("loadResults.poll.seconds");
			if (p == null)
			{
				throw new Exception("Missing workbench property: loadResults.poll.seconds");
			}
			m_poll_interval = new Long(p);

			p = wb.getProperties().getProperty("loadResults.pool.size");
			if (p == null)
			{
				throw new Exception("Missing workbench property: loadResults.pool.size");
			}
			m_pool_size = new Integer(p);
			//threshold = thread_pool_size * 4;
			threshold = 0;

			m_submitter = System.getProperty("submitter");
			if (m_submitter == null)
			{
				throw new Exception("Missing system property submitter");
			}

			// Properties that control behavior when used in RecoverResults mode.
			try
			{
				m_recover = Integer.getInteger("recover");
			}
			catch(Exception e)
			{
			}
			m_local = System.getProperty("local");
			
			Integer i = Integer.getInteger("age");
			m_age = (i == null) ? 0 : i;


			LoadResults lr = new LoadResults();
			log.debug("LOAD RESULTS: for submitter=" + m_submitter + ", poll_interval in seconds=" 
				+ m_poll_interval + ", thread pool size=" + m_pool_size + ", max jobs queued = " + threshold
				+ ((m_recover > 0 )? ", Recovery Mode ": ", Normal Mode"));

			/* If previous process died, unlock it's records */
			RunningTask.unlockResultsReady(m_submitter, (m_recover > 0));

			/* 
				In recover mode keepWorking returns after one pass.  We want to give the threads plenty of time 
				to complete their work after keepWorking has queued up the jobs.
			*/
			lr.keepWorking();
			shutdownAndAwaitTermination(24, TimeUnit.HOURS);
			log.debug("LOAD RESULTS: exitting normally."); 
		}
		/*
			Main thread can catch an exception if, for example it isn't able to connect
			to the db.  If main thread exits but leaves the threadpool threads alive but
			idle the process sticks around and doesn't do anything.  In this case
			I'm choosing to kill the threads pretty quickly.  I'm afraid of things that
			will hang or won't resume cleanly so I think we're better off letting the
			whole process get restarted.

		*/
		catch (Exception e)
		{
			log.error("Caught Exception.  Calling shutdownAndAwaitTermination().", e);
			shutdownAndAwaitTermination(1, TimeUnit.MINUTES);
			log.debug("LOAD RESULTS: exitting due to exception in main()."); 
			return;
		}
	}

	static void shutdownAndAwaitTermination(int count, TimeUnit unit) 
	{
		threadPool.shutdown(); // Disable new tasks from being submitted
		try 
		{
			// Wait a while for existing tasks to terminate
			if (!threadPool.awaitTermination(count, unit)) 
			{
				threadPool.shutdownNow(); // Cancel currently executing tasks

				// Wait a while for tasks to respond to being cancelled
				if (!threadPool.awaitTermination(20 , TimeUnit.SECONDS))
				{
					log.error("ThreadPool did not terminate");
				}
			}
		} 
		catch (InterruptedException ie) 
		{
			// (Re-)Cancel if current thread also interrupted
			threadPool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}
 	
	
	private void keepWorking() throws Exception
	{
		while(true)
		{
			/*
			log.debug("Threads busy=" + threadPool.getActiveCount() + ", jobs in Q=" +
				threadPool.getQueue().size() + ", taskCount=" + threadPool.getTaskCount());
			*/

			int jobsQueued;
			if ((threshold == 0) || (jobsQueued = threadPool.getQueue().size()) < threshold)
			{
				int jobCount = scanAndProcess();
			} else
			{
				log.warn("Thread pool has " + jobsQueued + " jobs queued, not queuing more until queue drains.");
			}
			// In recovery mode we only scan the database once.  Otherwise if a resource is down we would keep
			// processing the same records over and over.
			if (m_recover > 0|| (m_local != null))
			{
				return;
			}
			Thread.sleep(1000 * m_poll_interval);
		}
	}

	private int scanAndProcess() throws Exception
	{
		// select tasks with specified submitter and status that aren't locked.
		List<RunningTask> list = 
			RunningTask.findResultsReady(m_submitter, (m_recover > 0), false);
		if (list.size() > 0)
		{
			String tmp = "";
			for (RunningTask rt : list)
			{
				tmp += rt.getJobhandle() + "-" + rt.getStatus() + "-" + (rt.getLocked() == null ? "" :  rt.getLocked().toString()) + ", ";
			}
			log.debug("Found " + list.size() + " tasks to process: " + tmp);
		}

		// At least on triton, if we try to read stderr and stdout right after we're notified that job completed
		// the read will fail, but later it's ok.  
		Thread.sleep(10 * 1000);

		for (RunningTask rt : list)
		{
			if (!inProgressList.contains(rt.getJobhandle()))
			{
				threadPool.execute(this.new ProcessRunningTask(rt.getJobhandle()));
				inProgressList.add(rt.getJobhandle());
			}
		}
		return list.size();
	}

	// NOT USED
	private boolean tooOld(Date submitted, int maxDays)
	{
		// want to do return the equivalent of the query: datediff(now(), DATE_SUBMITTED) >  maxDays
		// don't think this is accurate for daylight savings time and maybe other stuff, but is 
		// it ever off by more than 1 day?
		Date today = new Date();
		long diffInMillies = today.getTime() - submitted.getTime();
		long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
		return (diff > maxDays);
	}

	private class ProcessRunningTask implements Runnable 
	{
		long m_taskId;
		String m_jobhandle;

		ProcessRunningTask(String jobhandle) throws Exception
		{
			m_jobhandle = jobhandle;
		}

		public void run()
		{
			log.debug("started run() for task.");
			long startTime = System.currentTimeMillis();
			NDC.push("[jh =" + m_jobhandle + "]");
			boolean gotLock = false;
			try
			{
				gotLock = RunningTask.lock(m_jobhandle);
				if (!gotLock)
				{
					log.debug("Skipping " + m_jobhandle + ". Already locked.");
					return;
				}
				RunningTask rt = RunningTask.find(m_jobhandle);
				m_taskId = rt.getTaskId();
				log.debug("doing jobhandle: " + m_jobhandle +".");
				m_jobhandle = rt.getJobhandle();
				NDC.pop();
				NDC.push("[task=" + m_taskId +", job=" + m_jobhandle + "]");

				// It's possible someone else changed the status before we managed to lock the record.
				if (!rt.getStatus().equals(RunningTask.STATUS_TERMINATED))
				{
					log.debug("Skipping " + m_jobhandle + ". Status isn't " + 
						RunningTask.STATUS_TERMINATED + ", it's " + rt.getStatus());
					return;
				} 
				log.debug("Loading Results for " + m_jobhandle);
				TaskMonitor.getResults(rt,  m_local);
				log.debug("after TaskMonitor.getResults() for " + m_jobhandle);
			}
			catch(Exception e)
			{
				log.error("", e);
			}
			catch(Error t)
			{
				log.error("THREAD IS DYING.", t);
				throw t;
			}
			finally
			{
				try
				{
					if (gotLock)
					{
						RunningTask.unlock(m_jobhandle);
					}
				}
				catch(Exception e)
				{
					log.error("Error unlocking running task.", e);	
				}
				catch(Error t)
				{
					log.error("Error unlocking running task and THREAD IS DYING.", t);
					throw t;
				}
				long elapsedTime = System.currentTimeMillis() - startTime;
				log.debug("LoadResults took " + elapsedTime/1000 + " seconds, or " + (elapsedTime/60000) + " minutes.");
				inProgressList.remove(m_jobhandle);
				NDC.pop();
				NDC.remove();
			}
		}
	}

}
