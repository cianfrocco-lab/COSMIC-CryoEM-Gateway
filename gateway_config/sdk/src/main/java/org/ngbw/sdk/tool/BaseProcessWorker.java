/*
 * Todo:
 * Pass RunningTask object or RunningTask ID.
 */
package org.ngbw.sdk.tool;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.api.tool.FileHandler.FileAttributes;
import org.ngbw.sdk.api.tool.ToolRegistry;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.StatisticsEvent;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.User;


public abstract class BaseProcessWorker
{
    private static final Log logger = LogFactory.getLog(BaseProcessWorker.class.getName());
    protected static Properties wbProperties = Workbench.getInstance().getProperties();
    protected ToolRegistry toolRegistry = Workbench.getInstance().getServiceFactory().getToolRegistry();

    private static long MAX_FILE_SIZE;
    private static long MAX_DIR_SIZE;
    
    private static final String JOBINFO_TXT = "_JOBINFO.TXT";


    static
    {
        String file = wbProperties.getProperty("max.file.size");
        String dir = wbProperties.getProperty("max.dir.size");
        try
        {
            logger.debug("file=" + file + ", dir=" + dir);
            
            if (file != null)
            {
                MAX_FILE_SIZE = Long.valueOf(file.trim());
            }
            
            if (dir != null)
            {
                MAX_DIR_SIZE = Long.valueOf(dir.trim());
            }
        }
        catch ( NumberFormatException e )
        {
            logger.warn("Invalid max.file.size or max.dir.size property.");
        }
    }


    protected long m_taskId;
    protected long m_jobTimeout; // in minutes.
    protected String m_jobHandle;
    protected String m_commandLine;
    protected Task m_task;

    // todo: get these from ToolResource, not the running task
    protected String m_workingDir;
    protected String m_failedDir;
    protected String m_archiveDir;
    protected String m_manualDir;
    protected String m_chargeNumber;
    protected String m_url;
    protected String m_rc;

    /*
     * Anything the pise xml file for the tool put into the parameter file "scheduler.conf" will
     * show up as a property here in m_schedulerConf.
     */
    protected Properties m_schedulerConf;
    protected Double m_maxRunHours;

    protected Map<String, OutputDescription> m_outputDescr;
    protected String m_toolID;
    protected long m_userID;

    protected Tool m_tool;
    protected User m_user;
    protected String m_jobInfo;
    protected String m_userInfo;
    protected String m_outputInfo;
    protected long m_running_task_id;
    protected RunningTask runningTask;
    protected boolean m_noMoveDir = false;
    protected boolean m_local = false;
    protected FileHandler fh = null;


    protected BaseProcessWorker ( String jobhandle ) throws Exception
    {
        runningTask = RunningTask.find(jobhandle);
        initialize();
    }


    protected BaseProcessWorker ( RunningTask rt ) throws Exception
    {
        runningTask = rt;
        initialize();
    }
    
    
    private void initialize () throws Exception
    {
        // We get these properties from the running task, they're specific to this task.
        m_jobHandle = runningTask.getJobhandle();
        m_toolID = runningTask.getToolId();
        m_userID = runningTask.getUserId();
        m_user = new User(m_userID);
        m_tool = new Tool(m_toolID, runningTask.getResource(), toolRegistry);
        m_taskId = runningTask.getTaskId();
        m_commandLine = runningTask.getCommandline();
        m_outputDescr = OutputDescription.parse(runningTask.getOutputDesc());
        m_outputInfo = "Output=" + runningTask.getOutputDesc();
        
        if (runningTask.getSprops() != null)
        {
            m_schedulerConf = SchedulerProperties.string2Properties(runningTask.getSprops());
            m_maxRunHours = SchedulerProperties.getRunHours(m_schedulerConf, null);
        }
        
        // This is optional.
        String param;
        
        if ((param = runningTask.parameters().get(Tool.CHARGENUMBER)) != null)
        {
            m_chargeNumber = param;
        }

        // These are based on the workspace directory specified in the tool registry.
        m_failedDir = m_tool.getToolResource().getFailedDirectory(m_jobHandle);
        m_archiveDir = m_tool.getToolResource().getArchiveDirectory(m_jobHandle);
        m_manualDir = m_tool.getToolResource().getManualDirectory(m_jobHandle);
        m_workingDir = m_tool.getToolResource().getWorkingDirectory(m_jobHandle);

        // These are typically specified in the tool registry for the tool resource.
        if ((param = m_tool.getToolResource().getParameters().get(Tool.JOB_TIMEOUT)) != null)
        {
            m_jobTimeout = Long.parseLong(param);
        }
        
        if ((param = m_tool.getToolResource().getParameters().get(Tool.RC)) != null)
        {
            m_rc = param;
        }
        
        String callbackurl;
        String pstr = wbProperties.getProperty("use.rest.callback");
        
        if (pstr != null && pstr.trim().equals("true"))
        {
            callbackurl = wbProperties.getProperty("rest.callback.url");
        }
        else
        {
            callbackurl = wbProperties.getProperty("portal.callback.url");
        }
        
        m_url = "'" + callbackurl + "?taskId=" + m_taskId + "\\&jh=" + m_jobHandle + "'";

        initTaskInfo();
    }


    protected void initTaskInfo () throws Exception
    {
        String m_taskLabel = "Unknown. Task Deleted";
        String m_taskCreationDate = "Unknown. Task Deleted";

        m_task = runningTask.getTask();
        
        if (m_task != null)
        {
            m_taskLabel = m_task.getLabel();
            m_taskCreationDate = m_task.getCreationDate().toString();
        }
        
        m_jobInfo = "Task\\ label=" + m_taskLabel + "\n"
                + "Task\\ ID=" + m_taskId + "\n"
                + "Tool=" + m_toolID + "\n"
                + "created\\ on=" + m_taskCreationDate + "\n"
                + "JobHandle=" + m_jobHandle + "\n";
        
        m_jobInfo += "resource=" + runningTask.getResource();
        
        m_userInfo = "User\\ ID=" + m_userID + "\n"
                + "User\\ Name=" + m_user.getUsername() + "\n"
                + "email=" + m_user.getEmail() + "\n";
    }


    /* *******************************************************************************************
     * START SUBMIT, CANCEL, CHECK
	*********************************************************************************************/
    
    public String submit () throws Exception
    {
        fh = getFileHandler();
        String jobId = null;
        preamble();
        jobId = submitImpl();
        return jobId;
    }
    
    
    private void preamble () throws Exception
    {
        logger.info("BEGIN: preamble()::void");
        String msg = m_jobInfo + "\n" + m_userInfo + "\n" + m_outputInfo;
        logger.debug(String.format("%s content:\n%s", JOBINFO_TXT, msg));
        fh.writeFile(m_workingDir + "/" + JOBINFO_TXT, m_jobInfo + "\n" + m_userInfo + "\n" + m_outputInfo);
        logger.info("END: preamble()::void");
    }

    
    protected String submitImpl () throws Exception
    {
        throw new RuntimeException("Internal Error.  Process Worker's must override this method.");
    }


    // OVERRIDE - called by ProcessManager
    void cancelJob () throws Exception
    {
        // Derived classes overide this if they are able to cancel a scheduled or running job.
        // This is called by ProcessManager.cancelJob()
        logger.debug("cancelJob not implemented in this process worker.");
        return;
    }

    
    // OVERRIDE - called by ProcessManager
    List<RunningTask> checkJobsOnResource ( List<RunningTask> lrt ) throws Exception
    {
        throw new Exception("Derived class must override checkJobsImpl() method.");
    }
    
    
    // OVERRIDE
    // can't call this in ctor, derived class must be fully constructed.
    protected abstract FileHandler getFileHandler () throws Exception;


    /* *******************************************************************************************
     * END SUBMIT, CANCEL, CHECK 
     ********************************************************************************************/
    
    /* ******************************************************************************************
     * START GET RESULTS 
	********************************************************************************************/
    
    protected InputStream readStdOut ( FileHandler handler ) throws Exception
    {
        throw new RuntimeException("Internal Error.  Process Worker's must override this method.");
    }
    

    protected InputStream readStdErr ( FileHandler handler ) throws Exception
    {
        throw new RuntimeException("Internal Error.  Process Worker's must override this method.");
    }


    /* 
     * Todo: The implementation of readFilediffers from FileHandler.getStream() only in that it does
     * a number of retries. That implementation should be in a FileHandler base class instead of
     * here and in the specific process workers. The retry details should be obtained from the
     * registry for a specific resource and passed to the filehandler.
     */
    protected InputStream readFile ( String filename, FileHandler handler ) throws Exception
    {
        throw new RuntimeException("Internal Error.  Process Worker's must override this method.");
    }


    public void getResults ( String localPath ) throws Exception
    {
        String msg = null;

        // These errors are not fatal because we may have misconfigured tool registry and can fix it for the next time this runs.
        if (toolRegistry.getToolResource(runningTask.getResource()) == null)
        {
            msg = "Failed to load results because host where job ran (" + runningTask.getResource() + ") is no longer available."
                    + " Please contact " + wbProperties.getProperty("email.serviceAddr") + " for assistance in retrieving your results. ";
            throw new Exception(msg);
        }
        
        fh = getFileHandler();
        
        if (!findWorkingDir(localPath))
        {
            msg = "Failed to load results because job's working dir wasn't found."
                    + " Please contact " + wbProperties.getProperty("email.serviceAddr") + " for assistance in retrieving your results. ";
            throw new Exception(msg);
        }
        
        storeJobCharge();

        // If task was deleted we needed to get jobcharge but we don't need to go any further.
        if (m_task == null)
        {
            throw new FatalTaskException("Task has been deleted");
        }
        
        if (msg == null)
        {
            if (localPath == null && resultsTooLarge(fh))
            {
                // need just the part before the @
                String email = m_user.getEmail().split("@")[0];

                msg = "Error retrieving results: ****ALERT****, one or more results files are too large for us to return through the"
                        + " browser interface. Your results will be posted automatically at https://cloud.sdsc.edu/v1/AUTH_cipres/" + email
                        + " by a script that runs at 6 am, 12 am, 6 pm, and 12 pm Pacific Time. The results will be available for a period"
                        + " of 2 weeks only, and will be deleted after that.  You can contact "
                        + wbProperties.getProperty("email.serviceAddr")
                        + " to request the results be made available earlier, or for assistance in retrieving your results. ";

                archiveTaskDirectory(m_manualDir);
                throw new FatalTaskException(msg);
            }
        }
        
        readAndStore();
    }


    /*
     * This doesn't throw exception. We want loadResults to complete even if something goes wrong
     * here; too much has been going wrong. Can check statistics_event table to find out how many
     * and which jobs are failing to store charege and how many are doing ok. Then look at logs for
     * more detail about the failures.
     */
    protected void storeJobCharge () throws Exception
    {
		logger.debug("start of storeJobCharge");
        Statistics statistics = Statistics.find(m_jobHandle);
        
        if (!statistics.isSuComputedNull())
        {
            return; // we already did this on a previous attempt.
        }
        
        Long sus = null;
        
        try
        {
			logger.debug("trying to get new JobCharge");
            JobCharge jc = new JobCharge(this, m_maxRunHours);
			logger.debug("trying to get getCharge()");
            sus = jc.getCharge();
			logger.debug("sus from getCharge(): (" + sus + ")");
            
            logger.info("JobCharge - calculated jobcharge of " + sus);
            
            StatisticsEvent statisticsEvent = new StatisticsEvent(m_jobHandle);
            statisticsEvent.setEventName("CALCULATE_SUS_SUCCESS");
            statisticsEvent.setEventValue("" + sus);
            statisticsEvent.save();
            
            statistics.setSuComputed(sus);
            statistics.save();
        }
        catch ( Exception e )
        {
            logger.error("JobCharge - error calculating jobcharge", e);
            
            StatisticsEvent se = new StatisticsEvent(m_jobHandle);
            se.setEventName("CALCULATE_SUS_FAILURE");
            se.setEventValue(e.toString());
            se.save();
        }
    }


    private void readAndStore () throws Exception
    {
        InputStream stderr = null;
        InputStream stdout = null;
        try
        {
            stderr = readStdErr(fh);
            stdout = readStdOut(fh);
            storeTaskResults(stdout, stderr, 0, "", fh);
        }

        finally
        {
            try
            {
                if (stderr != null)
                {
                    stderr.close();
                }
            }
            catch ( Exception e )
            {
                logger.error("Eating this error on stream close:", e);
            }
            try
            {
                if (stdout != null)
                {
                    stdout.close();
                }
            }
            catch ( Exception e )
            {
                logger.error("Eating this error on stream close:", e);
            }
        }
    }


    protected void storeTaskResults ( String stdOut, String stdErr, int exitCode, FileHandler handler )
            throws IOException, SQLException, Exception
    {
        storeTaskResults(stdOut, stdErr, exitCode, null, handler);
    }


    protected void storeTaskResults ( String stdOut, String stdErr, int exitCode, String msg, FileHandler handler )
            throws IOException, SQLException, Exception
    {
        InputStream out = null;
        InputStream err = null;
        if (stdOut != null)
        {
            out = new ByteArrayInputStream(stdOut.getBytes());
        }
        if (stdErr != null)
        {
            err = new ByteArrayInputStream(stdErr.getBytes());
        }
        storeTaskResults(out, err, exitCode, msg, handler);
    }


    protected void storeTaskResults ( InputStream stdOut, InputStream stdErr, int exitCode, FileHandler handler )
            throws IOException, SQLException, Exception
    {
        storeTaskResults(stdOut, stdErr, exitCode, null, handler);
    }


    protected void storeTaskResults ( InputStream stdOut, InputStream stdErr, int exitCode, String msg, FileHandler handler )
            throws IOException, SQLException, Exception
    {
        storeProcessOutput(m_task, stdOut, stdErr, exitCode);
        storeOutputFiles(m_task, handler);
        logger.debug("Task results stored in database.");
    }


    /*
     * Stores the exit code from the "tool" and the contents of the standard out and standard error.
     * The exit code is only stored if it's non-zero, and the contents of standard out and standard
     * error are only stored if they contain anything.
     *
     * TODO: wherever we read stdout and stderr into memory we need to limit the size. Or we need to
     * rewrite this to use streams.
     */
    private void storeProcessOutput ( Task task, InputStream stdOut, InputStream stdErr, int exitCode ) throws IOException, SQLException
    {
        List<TaskOutputSourceDocument> processOutput = new ArrayList<TaskOutputSourceDocument>();

        if (exitCode != 0)
        {
            String message = "Process returned nonzero exit value typically indicating an error: " + String.valueOf(exitCode);
            processOutput.add(new TaskOutputSourceDocument("EXIT_VALUE", message.getBytes()));
        }

        if (stdOut != null)
        {
            processOutput.add(new TaskOutputSourceDocument("STDOUT", stdOut));
            //log.debug("Created TaskOutputSourceDocument for stdout."); 
        }
        if (stdErr != null)
        {
            processOutput.add(new TaskOutputSourceDocument("STDERR", stdErr));
            //log.debug("Created TaskOutputSourceDocument for stderr."); 
        }
        if (processOutput.size() > 0)
        {
            task.output().put("PROCESS_OUTPUT", processOutput);
            task.save();
            //log.debug("Stored task stdout/stderr as PROCESS_OUTPUT.");
        }
    }


    /*
     * Stores the files produced by the "tool". Only files that match a pattern from an
     * <code>OutputDescription</code> object are stored; all other files are ignored.
     */
    private void storeOutputFiles ( Task task, FileHandler handler ) throws IOException, SQLException, Exception
    {
        // Each entry is map of parameter name to OutputDescription (ie.a  wildcard pattern/ datatype/ entity type/ format)
        for (Map.Entry<String, OutputDescription> entry : m_outputDescr.entrySet())
        {
            String parameter = entry.getKey();
            OutputDescription description = entry.getValue();
            List<String> fileNames = handler.listFiles(m_workingDir);
            List<TaskOutputSourceDocument> documents = new ArrayList<TaskOutputSourceDocument>();

            List<String> matchingFiles = OutputDescription.filter(fileNames, description);
            for (String filename : matchingFiles)
            {
                try
                {
                    InputStream inStream = handler.getInputStream(m_workingDir + filename);
                    documents.add(new TaskOutputSourceDocument(filename, description.entityType, description.dataType,
                                                               description.format, inStream, false));
                }
                catch ( FileNotFoundException notFoundErr )
                {
                }
            }
            if (documents.size() > 0)
            {
                task.output().put(parameter, documents);
            }
            else
            {
                logger.warn("No files found for output param " + parameter);
            }
        }
        task.save();
    }


    /**
     * localPath = if not null, use a local file handler and use this directory as the parent of the
     * jobs working dir. Don't move or remove workingDir when done. Verify that workingDir exists
     * and return true or false. Derived classes need to check m_local and create local file
     * handlers regardless of what filehandler they normally use.
     * <p>
     * Assumes layout is what we currently use where we have a root workspace, with ARCHIVE and
     * FAILED dirs beneath it.
     * <p>
     * Otherwise, if localPath is null, verify working dir exists, otherwise look for it under
     * FAILED. If found under FAILED, make that the working dir and return true.
     */
    public boolean findWorkingDir ( String localPath ) throws Exception
    {
        // Get a file handler, either local or as specified in running task entry
        if (localPath != null)
        {
            localPath += "/";
            logger.debug("Making sure job's working dir exists under: " + localPath);
            logger.debug("m_archiveDir is " + m_archiveDir);
            m_local = true;

            m_workingDir = DefaultToolResource.getWorkingDirectory(localPath, m_jobHandle);
            m_archiveDir = DefaultToolResource.getArchiveDirectory(localPath, m_jobHandle);
            logger.debug("m_archiveDir is " + m_archiveDir);

            m_failedDir = DefaultToolResource.getFailedDirectory(localPath, m_jobHandle);
            logger.debug("m_failedDir is " + m_failedDir);
            fh = new LocalFileHandler();
            m_noMoveDir = true;
        }
        else
        {
            logger.debug("Making sure job's working dir " + m_workingDir + " exists.");
        }

        // Make sure workingDir exists (also look in FAILED and ARCHIVE)
        if (!fh.isDirectory(m_workingDir))
        {
            logger.debug(m_workingDir + " doesn't exist, looking for " + m_failedDir);
            if (fh.isDirectory(m_failedDir))
            {
                m_workingDir = m_failedDir;
                m_noMoveDir = true;
                logger.debug("Found job dir in FAILED directory.");
            }
            else if (fh.isDirectory(m_archiveDir))
            {
                logger.debug(m_failedDir + " doesn't exist either, but ARCHIVE does.");

                m_workingDir = m_archiveDir;
                m_noMoveDir = true;
                logger.debug("Found job dir in ARCHIVE directory.");
            }
            else
            {
                logger.debug(m_archiveDir + " doesn't exist either.");
                return false;
            }
        }
        return true;
    }


    public void setNoMoveDir ( boolean flag )
    {
        m_noMoveDir = flag;
    }


    /*
     * returns array where first element is cummulative size of all files and second element is size
     * of largest file.
     */
    long[] resultsSize ( FileHandler fh, String directory ) throws Exception
    {
        List<FileAttributes> attributeList = fh.list(directory);
        long[] retval = new long[2];

        // keep track of cummulative size of all files and single largest file
        long totalSize = 0;
        long largestSize = 0;

        for (FileAttributes attributes : attributeList)
        {
            if (attributes.isDirectory)
            {
                long subResults[] = resultsSize(fh, directory + "/" + attributes.filename);
                totalSize = totalSize + subResults[0];
                if (subResults[1] > largestSize)
                {
                    largestSize = subResults[1];
                }
            }
            else
            {
                totalSize = totalSize + attributes.size;

                if (attributes.size > largestSize)
                {
                    largestSize = attributes.size;
                }
            }
        }
        retval[0] = totalSize;
        retval[1] = largestSize;
        return retval;
    }


    boolean resultsTooLarge ( FileHandler fh ) throws Exception
    {
        logger.debug("MAX_FILE_SIZE=" + MAX_FILE_SIZE + ", MAX_DIR_SIZE=" + MAX_DIR_SIZE);
        if (MAX_FILE_SIZE == 0 && MAX_DIR_SIZE == 0)
        {
            return false;
        }
        long[] sizes = resultsSize(fh, m_workingDir);
        logger.debug("ResultsTooLarge found total dir size = " + sizes[0] + ", largest file size = " + sizes[1]);
        return ((MAX_DIR_SIZE != 0) && sizes[0] > MAX_DIR_SIZE) || ((MAX_FILE_SIZE != 0) && sizes[1] > MAX_FILE_SIZE);
    }


    public boolean archiveTaskDirectory ()
    {
        return archiveTaskDirectory(m_archiveDir);
    }


    protected boolean archiveTaskDirectory ( String toDir )
    {
        try
        {
            if (!m_noMoveDir)
            {
                if (fh.exists(m_workingDir))
                {
                    fh.moveDirectory(m_workingDir, toDir);
                    logger.debug("Working directory [ " + m_workingDir + " ] moved to directory [ " + toDir + " ].");
                }
            }
            return true;
        }
        catch ( Exception e )
        {
            logger.error("moveDirectory " + m_workingDir + " to " + toDir + " failed.", e);
        }
        return false;
    }


    protected boolean moveToFailedDirectory ()
    {
        if (fh != null)
        {
            return archiveTaskDirectory(m_failedDir);
        }
        return false;
    }


    /**
     * ******************************************************************************************
     * START Class Initialization Helpers 
	*******************************************************************************************
     */
    
    
}
