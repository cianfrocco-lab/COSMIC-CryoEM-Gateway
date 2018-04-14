/*
 * TaskInitiate.java
 */
package org.ngbw.sdk.tool;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.core.configuration.ServiceFactory;
import org.ngbw.sdk.core.shared.TaskRunStage;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.StatisticsEvent;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.common.util.BaseValidator;
import org.ngbw.sdk.common.util.SendError;


/**
 *
 * @author Terri Liebowitz Schwartz
 */
public class TaskInitiate
{
	private static final Log log = LogFactory.getLog(TaskInitiate.class);
	protected final ServiceFactory m_serviceFactory;
	private static int maxAttempts;

	static
	{
        //log.debug ( "MONA: entered TaskInitiate static" );
		String tmp = Workbench.getInstance().getProperties().getProperty("submitJob.retries");
		maxAttempts = Integer.parseInt(tmp);
	}

	public TaskInitiate(ServiceFactory serviceFactory)
	{
        //log.debug ( "MONA: entered TaskInitiate(1)" );
		m_serviceFactory = serviceFactory;
	}

	/*
		Note that throughout this code we set a local TaskRunStage variable to the stage
		we are about to attempt.  That way if the operation throws an exception, the
		exception handler will use that stage in the error message.  If no exception
		occurs we store a success message with that stage after completing the operation.

		Task stage and messages are stored via TaskUpdater methods.  In some cases we
		let the RunningTask save the updated task so that both are updated in the same
		transaction.
	*/


	/*
		Web application calls this after saving Task in the db.  Application should
		not modify the task ever again, once handing it off to queueTask.
		However application can call Job.cancel which will delete the task.
	*/
	public String queueTask(Task task, boolean loggedInViaIPlant, Long predictedSus)
		throws DisabledResourceException, Exception
	{
        //log.debug ( "MONA: entered TaskInitiate.queueTask" );
		String toolId, jobHandle;
		TaskRunStage stage = task.getStage();
		RunningTask rt = null;

		try
		{
			if (!stage.equals(TaskRunStage.READY))
			{
				throw new WorkbenchException("Task is not in ready to run stage, stage is " + task.getStage());
			}
			// Check for disabled resource
			Tool tool = new Tool(task.getToolId(), m_serviceFactory.getToolRegistry());
			User user = new User(task.getUserId());
			String disabledMessage;
			if ((disabledMessage = tool.disabled(loggedInViaIPlant, user)) != null)
			{
				throw new DisabledResourceException(disabledMessage);
			}
			stage = TaskRunStage.QUEUE;

			// If front end didn't compute this, do it now.  REST does, portal2 doesn't.
			if (predictedSus == null)
			{
				predictedSus = predictSus(tool, task);
			}

			task.setJobHandle(getNewJobHandle(task.getToolId()));
			rt = WorkQueue.postWork(task, tool, tool.getTgChargeNumber(loggedInViaIPlant, user), predictedSus);


			log.debug("RunningTask " + rt.getJobhandle() + " created.  Task is Queued for submission. " +
				"Check submitJobD, checkJobD and loadResultsD logs for further status of this job.");
			return rt.getJobhandle();
		}
		catch (DisabledResourceException dre)
		{
			/*
				- Portal User can submit the job again later.
				- REST users will get an Error return, not a jobhandle, and although
				the task is stored in our db, it won't be returned when they list their submitted jobs
				since it's stage is READY which is < SUBMITTED.
			*/
			throw dre;
		}
		catch(Exception e)
		{
			//log.error("", e);
			TaskUpdater.logFatal(task, stage, e);
			throw e;
		}
	}

	public Long predictSus(Tool tool, Task task)
	{
		try
		{
			Map<String, List<String>> inputParams = convertTaskInput(task);
			RenderedCommand rc = tool.validateCommand(task.toolParameters(), inputParams);
			Long l = tool.getPredictedSus(rc.getSchedulerProperties());
			return l;
		}
		catch(Exception e)
		{
			log.error("", e);
			log.debug("Returning null for predicted sus");
			return null;
		}
	}


	/*
		These pass data between runTask() and it's helper methods.
	*/
	Task task = null;
	Tool tool = null;
	BaseProcessWorker pw = null;
	TaskRunStage stage;
	Map<String, List<String>> parameters;
	RenderedCommand command;
	RunningTask rt = null;
	User user = null;

	/*
		submitJobD calls this.
		We don't have to worry about user not existing because we never delete user accounts,
			just mark them inactive.
		RunningTask.status = NEW when this is called.

		If submitJobD crashes after incrementing attempts, it will process the same job again when it's
		restarted up to maxAttempts (see cipres-*.properties, submitJob.retries).
		If we haven't staged the working dir yet, it can process the job.  Once the working dir exists, we won't try
		to stage/submit again.  At the moment submitJobD handles all runningtasks with attempts >=0 , we don't
		have a recoverSubmitJobD.

		TODO: split submitJob and loadResults into separate process for each remote resource so one messed up
		resource can't hurt the others?

		TODO: should check tool.disabled again.
	*/
	public boolean runTask(RunningTask runningTask)
		throws DisabledResourceException, Exception
	{
        //log.debug ( "MONA: entered TaskInitiate.runTask()" );
		boolean emailErrorMessage = false;
		rt = runningTask;
        //log.debug ( "MONA: rt = " + rt );
		stage = TaskRunStage.INPUTCHECK;

		try
		{
			task = rt.getTask();
            //log.debug ( "MONA: task = " + task );
			if (task == null)
			{
				throw new Exception("Task has been deleted.");
			}

			if (!WorkQueue.tryAgain(rt, RunningTask.STATUS_NEW, maxAttempts))
			{
				throw new FatalTaskException("Failed to submit job to remote host because the limit of " +
					maxAttempts + " retries has been exceeded.");

			}

			// Todo: whenever we're dealing with a running task record, create Tool using the resource,
			// it specifies in case the registry has been changed and specifies that the tool runs
			// on a different resource now.
			tool = new Tool(task.getToolId(), rt.getResource(),  m_serviceFactory.getToolRegistry());
            //log.debug ( "MONA: tool = " + tool );
			user = new User(rt.getUserId());
            //log.debug ( "MONA: user = " + user );

			/*
				Todo: if _JOBINFO.TXT has a remotejobid, just use that jobId and skip stageAndRun.
			*/

			if (tool.getToolResource().workingDirectoryExists(rt.getJobhandle()))
			{
				// For now though, just do this to make sure we don't accidentally start job more than once in the same directory.
				throw new Exception("Job working directory already exists.  This means there was a network or similar error, or a COSMIC2 process " +
					" was restarted while the job was being submitted.  There are no automatic retries in this situation." );
			}

			stage = TaskRunStage.COMMANDRENDERING;
			command = validateAndRender();
            //log.debug ( "MONA: command = " + command );
			String msg = "Command rendered successfully: " + StringUtils.join(command.getCommand(), ' ');
            //log.debug ( "MONA: msg = " + msg );
			//log.debug(msg);
			//TaskUpdater.logProgress(task, stage, msg);
			WorkQueue.updateWork(rt, tool, command); // add info to WorkQueue and statistics records.

			// 1. This is the only place where we could do retries, anything else would be a fatal error.
			// 2. If an exception occurs here, email cipresadmin.
			emailErrorMessage = true;
			String jobId = stageAndRun();
			emailErrorMessage = false;

            //log.debug ( "MONA: jobId = " + jobId );
			WorkQueue.submitted(rt, jobId);
			return true;
		}
		catch (Exception e)
		{
			if (task == null)
			{
				log.debug("Task has been deleted.");
				WorkQueue.fail(rt, stage, e.getMessage());
				return false;
			}
			log.error("", e);
			if (emailErrorMessage == true)
			{
				// email error message to cipresadmin
				SendError.send("Job submission error: " +  e.toString());
			}
			WorkQueue.fail(rt, stage, e.toString());
			TaskMonitor.notifyJobComplete(task, "Error submitting job: " +  e.getMessage());
			throw e;
		}
	}


	private RenderedCommand validateAndRender() throws Exception
	{
		/*
			For each InFile parameter, this gives us a map of parameter name to taskInputSourceDocumentID
			In theory this could create brand new sourceDocuments by converting format of supplied document,
			or whatever.
		*/
		parameters = convertTaskInput();

		/*
			renderCommand adds non input file parameters, i.e. task.toolParameters(), to "parameters", then
			calls piseCommandRenderer.render, which also modifies "parameters".  For example, it adds a dummy
			parameter for each param file that the pisexml generates.
		*/
		RenderedCommand command = renderCommand();
		return command;
	}



	/*
		Returns the remote jobId if any.
	*/
	public String stageAndRun() throws Exception
	{
        //log.debug ( "MONA: entered TaskInitiate.stageAndRun()" );
		stage = TaskRunStage.INPUTSTAGING;
		String jobId = null;

		try
		{
			TaskUpdater.logProgress(task, stage, "Staging input files to " + rt.getResource());
			String workingDir = stageInputData();

			StatisticsEvent se = new StatisticsEvent(rt.getJobhandle());
			se.setEventName("DATA_STAGED");
			se.setEventValue("");
			se.save();
            //log.debug ( "MONA: se = " + se );

			//String msg = "Input files staged successfully to " + workingDir;
			String msg = "Input files staged successfully";
			log.debug ( msg + " to " + workingDir );
			TaskUpdater.logProgress(task, stage, msg);

			stage = TaskRunStage.SUBMITTING;
			pw = tool.getToolResource().getProcessWorker(rt);
            //log.debug ( "MONA: pw = " + pw );

			return pw.submit();
		}
		catch (Exception e)
		{
			if (pw != null)
			{
				pw.moveToFailedDirectory();
			}
			throw e;
		}
	}

	/*
		Can throw JobValidationException (a type of RuntimeException)
	*/
	private RenderedCommand renderCommand() throws IOException, SQLException
	{
		// iterate over the task's non-file parameters and add  them to the file parameters.
		for (Map.Entry<String, String> param : task.toolParameters().entrySet())
		{
			List<String> value = new ArrayList<String>();

			value.add(param.getValue());

			parameters.put(param.getKey(), value);
		}
		// Pass all the parameters for which the user has supplied data to the command renderer.
		return tool.renderCommand(parameters);
	}

	/*
	 */
	private String stageInputData() throws IOException, SQLException, Exception
	{
		// This is a map of  parameter name to filename (to be created in the working directory)
        //log.debug ( "MONA: entered TaskInitiate.stageInputData()" );

		Map<String, List<String>> paramNameToFileName = command.getInputFileMap();
        //log.debug ( "MONA: paramNameToFileName = " + paramNameToFileName );

		// This will be a map of filename to the file's contents (usually it's a taskInputSourceDocument ID)
		Map<String, String> fileNameToContents= new TreeMap<String, String>();

		// Build the map of parameter name to file content.
		for (Map.Entry<String, List<String>> entry : paramNameToFileName.entrySet())
		{
            //log.debug ( "MONA: entry = " + entry );
			String parameter = entry.getKey();
            //log.debug ( "MONA: parameter = " + parameter );
			List<String> names = entry.getValue();
            //log.debug ( "MONA: names = " + names );

			// The value of the InFile parameter is the contents of the file OR the source document ID, prefixed
			// with "TaskInputSourceDocument " (see TaskInitiate.convertTaskInput()).
			List<String> value = parameters.get(parameter);
            //log.debug ( "MONA: value = " + value );
			if (value == null)
			{
				throw new NullPointerException("Value for parameter " + parameter + " is null!");
			}

            DataType type = null;
            String[] pieces = value.get ( 0 ).split ( " " );
            //log.debug ( "MONA: pieces = " + pieces );

            // Determine type of TaskInputSourceDocument
            if ( pieces != null &&
                pieces[0].equals ( "TaskInputSourceDocument" ) )
            {
                TaskInputSourceDocument tisd = new TaskInputSourceDocument
                    ( Long.parseLong ( pieces[1] ) );
                //log.debug ( "MONA: tisd = " + tisd );
                type = tisd.getDataType();
            }

			for (int index = 0 ; index < names.size() ; index += 1)
			{
				String fileName = names.get(index);
                //log.debug ( "MONA: fileName = " + fileName );
				if (fileName == null || fileName.isEmpty())
				{
					throw new NullPointerException("Filename for parameter " + parameter + " not found." +
							" This can happen if you deleted the data.");
				}

                // If a directory, then we'll get only the filename to check
                if ( type == DataType.DIRECTORY )
                {
                    File tmp = new File ( fileName );
                    //log.debug ( "MONA: tmp = " + tmp );
                    String tmpFilename = tmp.getName();
                    //log.debug ( "MONA: tmpFilename = " + tmpFilename );

				    if ( ! BaseValidator.isSimpleFilename ( tmpFilename ) )
				    {
					    log.warn ( "Label '" + fileName +
                            "' is not an acceptable filename.  Filenames are restricted due to security concerns." );

					    throw new Exception (
                            "User data item for parameter " + parameter +
                            " has a label that contains " +
							"characters other than ascii letters, numbers, period and underscore" );
                    }
				}

                else
                {
				    if (!BaseValidator.isSimpleFilename(fileName))
				    {
					    log.warn ( "Label '" + fileName +
                            "' is not an acceptable filename.  Filenames are restricted due to security concerns." );

					    throw new Exception (
                            "User data item for parameter " + parameter +
                            " has a label that contains " +
							"characters other than ascii letters, numbers, period and underscore" );
                    }
				}

				String doc = value.get(index);
                //log.debug ( "MONA: doc = " + doc );

				// Put the content or source doc ID in our filename <-> content map
				fileNameToContents.put(fileName, doc);
			}
		}

		// Add a file to the list of those to be staged. File is for debugging.
		fileNameToContents.put("_COMMANDLINE", (StringUtils.join(command.getCommand(), " ")));
		String workingDir = tool.getToolResource().getWorkingDirectory(task.getJobHandle());
        //log.debug ( "MONA: workingDir = " + workingDir );

		// We're using DefaultToolResource to stageInput and to submitJob.  TODO: Maybe we should also use it to cancelJob?
		// DefaultToolResource of course knows how to interpret the content, either as actual content or a source doc ID.
		tool.getToolResource().stageInput(workingDir, fileNameToContents);

		return workingDir;
	}

	private String getSourceDocumentLabel(TaskInputSourceDocument document)
	{
        //log.debug ( "MONA: entered TaskInitiate.getSourceDocumentLabel()" );
        //log.debug ( "MONA: document = " + document );
		try
		{
			/*
				Note that there are TaskInputSourceDocuments in the db, where the name field
				is not numeric (for instance it may be "infile").  I'm not sure how those
				records were created.  When we select a user data item for a task, we set
				the TaskInputSourceDocument.name = UserDataItem.getUserDataId(), which is the key,
				USERDATA_ID, for the UserDataItem and UserDataDirItem.
			*/
			String docId = document.getName();
			log.debug("Task Input Source Document name is " + docId);

            DataType docType = document.getDataType();

			Long udId = Long.valueOf(docId);
			//log.debug("Corresponding userDataItem ID=" + docId);
			FolderItem udi = null;

            if ( docType == DataType.DIRECTORY ) 
                udi = new UserDataDirItem ( udId );
            else
			    udi = new UserDataItem(udId);

			String label = udi.getLabel();
			log.debug("Label, to be used as filename in working dir, is: " + label);

			if (label != null)
				return label.trim();
		}
		catch(Exception e)
		{
			log.warn("Trying to find UserDataItem/UserDataDirItem for the TaskInputSourceDocument with id=" + document.getInputDocumentId() + ". " + e.toString());
			log.debug("This can happen if the UserDataItem has been deleted.");
		}

		return "";
	}

	private Map<String, List<String>> convertTaskInput() throws IOException, SQLException
	{
		return convertTaskInput(task);
	}

	/**
	 	The task.input() has a map with one entry for each parameter that corresponds to a file to be staged.
		In other words, each parameter in the tool xml that is of type "InFile" and specifies a filename
		with a <filename> element.  task.input() is a map of parameter name -> list of TaskInputSourceDocuments.  This method
		returns a map of parameter name -> list of strings that identifies the TaskInputSourceDocument in the database.
	 */
	private Map<String, List<String>> convertTaskInput(Task task) throws IOException, SQLException
	{
        //log.debug ( "MONA: entered TaskInitiate.convertTaskInput()" );
        //log.debug ( "MONA: task = " + task );
		String jobHandle = task.getJobHandle();
		Map<String, List<String>> params = new TreeMap<String, List<String>>();
        //log.debug ( "MONA: params = " + params );

		//task.input() returns a map of parameter name (with trailing "_") to a list of TaskInputSourceDocument records.
		Map<String, List<TaskInputSourceDocument>> inputData = task.input();
        //log.debug ( "MONA: inputData = " + inputData );

		log.debug(jobHandle + ": processing " + inputData.size() + " input parameter collection(s).");

		//Iterate over inputData, the map of param name to list of TaskInputSourceDocuments
		for (Map.Entry<String, List<TaskInputSourceDocument>> paramEntry : inputData.entrySet())
		{
            //log.debug ( "MONA: paramEntry = " + paramEntry );
			// param: This is the parameter name
			String param = paramEntry.getKey();
            //log.debug ( "MONA: param = " + param );

			// srcDocumentList: This is the list of source documents for the current parameter.
			List<TaskInputSourceDocument> srcDocumentList = paramEntry.getValue();
            //log.debug ( "MONA: srcDocumentList = " + srcDocumentList );

			for (TaskInputSourceDocument document : srcDocumentList)
			{
                //log.debug ( "MONA: document = " + document );
				String documentID = "";
				if (document != null)
				{
					String label = getSourceDocumentLabel(document);
					documentID = "TaskInputSourceDocument " + document.getInputDocumentId() + " " + label;
				}
				List<String> ids = params.get(param);
				if (ids == null)
				{
					ids = new ArrayList<String>();

					params.put(param, ids);
				}
				ids.add(documentID);
				log.debug(jobHandle + ": For parameter " + param + " added document: " + documentID);
			}
		}
		return params;
	}



	// protected methods


	/**
	 *
	 * @param toolId
	 * @return
	 */
	protected String getNewJobHandle(String toolId)
	{
		String uuid = UUID.randomUUID().toString().toUpperCase().replaceAll("-", "");

		return "NGBW-JOB-" + toolId + "-" + uuid;
	}
}
