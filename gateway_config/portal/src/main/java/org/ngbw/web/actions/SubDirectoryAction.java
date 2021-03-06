package org.ngbw.web.actions;

import org.ngbw.sdk.database.Task;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.database.RunningTask;
import org.apache.log4j.Logger;
import org.ngbw.sdk.core.configuration.ServiceFactory;
import org.ngbw.sdk.tool.Tool;
import org.ngbw.sdk.common.util.BaseValidator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.lang.ProcessBuilder;
import java.lang.Process;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


import org.apache.struts2.interceptor.validation.SkipValidation;
//https://www.dineshonjava.com/the-valuestack-in-struts-2/
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.ActionSupport;


import java.io.InputStream;
import org.apache.log4j.Logger;

/**
 * Struts action class to handle file download based on current task, and request parameter "inputPath". 
 * 
 */
 /*
 	To Do: this doesn't need to inherit from ManageTasks it just needs to be able to 
	get the current task and the workbench session from the SESSION.

	And be careful, if a StreamResult attribute is found in the base class but not here,
	it may be called w/o the needed context and cause problems.  That's what happened
	when Paul added getContentLength to DataManager and why I had to add it here (or
	I could have made this not inherit from ManageTasks which is derived from
	DataManager).
 */
//comment
@SuppressWarnings("serial")
//comment
//public class RemoteTailAction extends ActionSupport 
//public class RemoteTailAction extends NgbwSupport 
//comment
public class SubDirectoryAction extends ManageTasks 
{
	public String randomstring;
	private static final Logger logger = Logger.getLogger(SubDirectoryAction.class.getName());
	private String contentType;
	//private ServiceFactory m_serviceFactory;
	InputStream is;
	private String line;
	private String inputPath;
	private String CURRENT_SUBDIR = "CURRENT_SUBDIR";
	//private String currentsubdir = "";
	public String getSubDirectory() {
		String currentsubdir = (String) userSession.get(CURRENT_SUBDIR);
		return currentsubdir;
	}
	public void setSubDirectory(String inputPath) throws Exception {
		appendCurrentSubDir(inputPath);
		//logger.debug("currentsubdir: (" + currentsubdir + ") this.currentsubdir: (" + this.currentsubdir + ")");
		//this.currentsubdir = this.currentsubdir + "/" + inputPath;
		//logger.debug("new this.currentsubdir: (" + this.currentsubdir + ")");
	}
	public String getInputPath() { return inputPath; }
	public void setInputPath(String inputPath) {this.inputPath = inputPath; }
	private ServiceFactory m_serviceFactory;
/*
	public static final String ID = "id";
	// session attribute key constants
	public static final String WORKING_FOLDER = "workingFolder";
	public static final String PARENT_FOLDER = "parentFolder";
	mt = new ManageTasks();
*/



	// https://struts.apache.org/getting-started/http-session.html
	private Map<String, Object> userSession ;
	// NgbwSupport.java has setSession and getSession...
	// but it's private.
	//public void setSession(Map<String, Object> session) {
	//	userSession = session;
	//}
	private void appendCurrentSubDir(String newsubdir) throws Exception {
		//userSession = session;
		logger.debug("newsubdir: (" + newsubdir + ")");
		String currentsubdir = (String) userSession.get(CURRENT_SUBDIR);
		if (currentsubdir == null || currentsubdir == "") {
			currentsubdir = newsubdir;
		} else {
			currentsubdir = currentsubdir + "/" + newsubdir;
		}
		if ( !BaseValidator.isSimplePathFilename( currentsubdir )) {
			logger.warn ( "path '" + currentsubdir + "' is not an acceptable filename.  Filenames are restricted due to security concerns." );
			throw new Exception (
                            "currentsubdir " + currentsubdir +
                            " has a string that contains " +
                                                        "characters other than ascii letters, numbers, single period, underscore and backslash" );
		} else {
			logger.debug ( "currentsubdir '" + currentsubdir + "' is an acceptable filename." );
		}
		userSession.put(CURRENT_SUBDIR, currentsubdir);
	}

	public InputStream getInputStream() 
	{
		return is;
	}

	public String getInputLine() throws Exception
	{
		execute();
		//ValueStack stack = ActionContext.getContext().getValueStack();
		//Map<String, Object> context = new HashMap<String, Object>();
		//logger.debug("start of getInputLine() line: (" + line + ")");
		//logger.debug("context : (" + context.toString() + ")");
		return line;
	}

	@SkipValidation
	private InputStream getTheData(String inputPath)  throws Exception
	{
		logger.debug("start of getTheData()");
		Task task = null;
		String[] taskId = (String[])getParameters().get(ID);
		logger.debug("got taskId");
		if (taskId != null && taskId.length > 0) 
		{
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			logger.debug("setCurrentTask done");
		} 
		if ((task = getCurrentTask()) == null) 
		{
			throw new Exception("No task selected.");
		}
		logger.debug("before getWorkbenchSession()");
		WorkbenchSession session = getWorkbenchSession();
		logger.debug("before getFileFromWorkingDirectory");
		//return getWorkbenchSession().getFileFromWorkingDirectory(task, inputPath);
		//WorkingDirectory wd = session.getWorkingDirectory(task);
		return session.getFileFromWorkingDirectory(task, inputPath);
	}

	public long getDataLength() 
	{
		try
		{
			return is.available();
		} catch (Exception e)
		{
			reportError(e, "Error retrieving size of data, returning zero.");
			return 0;
		}
	}

	//method for downloading file
	public String execute() throws Exception
	{
		if ( !BaseValidator.isSimplePathFilename( inputPath )) {
			logger.warn ( "path '" + inputPath + "' is not an acceptable filename.  Filenames are restricted due to security concerns." );
			throw new Exception (
                            "inputPath " + inputPath +
                            " has a string that contains " +
                                                        "characters other than ascii letters, numbers, period, underscore and backslash" );
		}
		logger.debug("start of execute() inputPath: (" + inputPath + ")");
		userSession = getSession();
		WorkbenchSession session = getWorkbenchSession();
		 if (session == null) {
			logger.debug("session is null!");
		}
		//if (session == null) {
		//	logger.debug("session is null!");
		//} else {
		//	logger.debug("session not null");
		//}
		Workbench workbench = session.getWorkbench();
		m_serviceFactory = workbench.getServiceFactory();
		//ServiceFactory m_serviceFactory = getWorkbenchSession().getWorkbench().getServiceFactory();
		try
		{
			setSubDirectory(inputPath);
			Task task = null;
			String[] taskId = (String[])getParameters().get(ID);
			logger.debug("got taskId");
			if (taskId != null && taskId.length > 0) 
			{
				setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
				logger.debug("setCurrentTask done");
			} 
			if ((task = getCurrentTask()) == null) 
			{
				throw new Exception("No task selected.");
			}
		}
		catch (Exception t)
		{
			logger.debug(t);
		}
		return SUCCESS;
	}

/*
			is = null;
			is = getTheData(inputPath);
			logger.debug("got is");
			InputStreamReader isr = new InputStreamReader(is);
			logger.debug("got isr");
			BufferedReader br = new BufferedReader(isr);
			logger.debug("got br");
			line = "";
			String newline = null;
			br.mark(10000000);
			long streamcount = br.lines().count();
			br.reset();
			long initialindex = 0;
			if (streamcount >= 10) {
				initialindex = streamcount - 10;
			} else {
				initialindex = 0;
			}
			long currentline = 0;
			//line = br.readLine();
			//logger.debug("execute() line: (" + line + ")");
			//while ((line = line + br.readLine()) != null) {
			//	logger.debug("got a line");
			//}
			//while (br.ready()) {
			//	line = line + br.readLine();
			//}
			//for (String newline : br.lines()) {
			//	line = line + newline;
			//}
			while (newline == null) {
				logger.debug("br.readline() null, newline: (" + newline + ")");
				TimeUnit.SECONDS.sleep(5);
				newline = br.readLine();
			}
			while (newline != null) {
				logger.debug("newline: (" + newline + ")");
				if (currentline >= initialindex) {
					line = line + newline + "\n";
				}
				logger.debug("got a line");
				if (currentline >= streamcount - 1) {
					break;
				}
				currentline = currentline + 1;
				newline = br.readLine();
			}
			is.close();
		}
		catch (Exception t)
		{
			// An error is expected here if the user tries to download the file as it's being deleted or after it's
			// been deleted.   
			logger.debug(t);
			//is = new ByteArrayInputStream(new byte[0]);
		}
		//contentType = "application/x-unknown";
		contentType = "text/html";
		return SUCCESS;
	}
*/

	public String getContentDisposition()
	{
		String retval = "filename=\"" + inputPath + "\"";
		return retval;
	}

	public String getContentType()
	{
		return contentType;
	}
        public String getSubDirectoryPath()
        {
        logger.info ( "entered getSubDirectoryPath()" );
                try
                {
			String workingdirectory = getTaskWorkingDirectoryPath();
        		logger.info ( "workingdirectory: (" + workingdirectory + ")" );
			String fullpath = workingdirectory + getSubDirectory();
			return fullpath;
                }
                catch (Throwable error)
                {
                        //logger.debug("Error getting path of working directory for selected task for currentsubdir: (" + currentsubdir + ")");
                        reportCaughtError(error, "Error getting path of working directory for selected task.");
                        return null;
                }
        }

/*
                        Task task = getCurrentTask();
                        String subpath = "";
                        String path = getWorkbenchSession().getWorkingDirectoryPath(task);
                        return path;
                }
                catch (Throwable error)
                {
                        reportCaughtError(error, "Error getting path of working directory for selected task.");
                        return null;
                }
        }
*/
        private Tool getToolObj() throws Exception
        {
                Tool tool = null;
		Task task = getCurrentTask();
                RunningTask rt = RunningTask.find(task.getJobHandle());
                if (rt != null)
                {
                        tool = new Tool(task.getToolId(), rt.getResource(), m_serviceFactory.getToolRegistry());
                } else
                {
                        tool = new Tool(task.getToolId(), m_serviceFactory.getToolRegistry());
                }
                return tool;
        }
        private FileHandler getFileHandler() throws Exception
        {
                return getToolObj().getToolResource().getFileHandler();
        }

        public List<FileHandler.FileAttributes> getWorkingDirectoryList()
        {
        	logger.info ( "entered getWorkingDirectoryList()" );
		FileHandler fileHandler = null;
                try
                {
			// get filehandler, return fileattributes list
			fileHandler = getFileHandler();
			String subdirectory = getSubDirectoryPath();
        		logger.info ("trying to list :(" + subdirectory + ")");
			return fileHandler.list(subdirectory);
                        //return getWorkbenchSession().listWorkingDirectory(task);
                }
                catch (Throwable error)
                {
                        logger.debug("Error getting list of working directory files for selected task.");
                        reportCaughtError(error, "Error getting list of working directory files for selected task.");
                        return null;
                }
        }
}
