package org.ngbw.web.actions;

import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.WorkbenchSession;
import org.apache.log4j.Logger;
import org.ngbw.sdk.common.util.BaseValidator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
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
@SuppressWarnings("serial")
//public class RemoteTailAction extends ActionSupport 
//public class RemoteTailAction extends NgbwSupport 
public class RemoteTailAction extends ManageTasks 
{
	private static final Logger logger = Logger.getLogger(RemoteTailAction.class.getName());
	private String contentType;
	InputStream is;
	private String line;
	private String inputPath;
	public String getInputPath() { return inputPath; }
	public void setInputPath(String inputPath) {this.inputPath = inputPath; }
	private Map<String, Object> userSession;
	private String CURRENT_SUBDIR = "CURRENT_SUBDIR";
/*
	public static final String ID = "id";
	// session attribute key constants
	public static final String WORKING_FOLDER = "workingFolder";
	public static final String PARENT_FOLDER = "parentFolder";
	mt = new ManageTasks();
*/

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
		//logger.debug("start of getTheData()");
		Task task = null;
		String[] taskId = (String[])getParameters().get(ID);
		//logger.debug("got taskId");
		if (taskId != null && taskId.length > 0) 
		{
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			//logger.debug("setCurrentTask done");
		} 
		if ((task = getCurrentTask()) == null) 
		{
			throw new Exception("No task selected.");
		}
		//logger.debug("before getWorkbenchSession()");
		WorkbenchSession session = getWorkbenchSession();
		//logger.debug("before getFileFromWorkingDirectory");
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
		//logger.debug("start of execute() inputPath: (" + inputPath + ")");
		try
		{
			if ( !BaseValidator.isSimplePathFilename( inputPath )) {
				logger.warn ( "path '" + inputPath + "' is not an acceptable filename.  Filenames are restricted due to security concerns." );
				throw new Exception (
					"inputPath " + inputPath +
					" has a string that contains " +
					"characters other than ascii letters, numbers, single period, underscore and backslash" );
			} else {
				logger.debug("path '" + inputPath + "' is valid");
			}
			userSession = getSession();
			String currentsubdir = (String) userSession.get(CURRENT_SUBDIR);
			//logger.debug("currentsubdir: (" + currentsubdir + ")");
			String workingdirectory = getTaskWorkingDirectoryPath();
			//logger.debug("workingdirectory: (" + workingdirectory + ")");
			//String fullpath = workingdirectory + currentsubdir + "/" + inputPath;
			String fullpath = currentsubdir + "/" + inputPath;
			//logger.debug("fullpath: (" + fullpath + ")");
			is = null;
			//is = getTheData(inputPath);
			is = getTheData(fullpath);
			//logger.debug("got is");
			InputStreamReader isr = new InputStreamReader(is);
			//logger.debug("got isr");
			BufferedReader br = new BufferedReader(isr);
			//logger.debug("got br");
			line = "";
			String newline = null;
			br.mark(10000000);
			long streamcount = br.lines().count();
			br.reset();
			long initialindex = 0;
			if (streamcount >= 20) {
				initialindex = streamcount - 20;
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
/*
*/
			while (newline == null) {
				//logger.debug("br.readline() null, newline: (" + newline + ")");
				TimeUnit.SECONDS.sleep(5);
				newline = br.readLine();
			}
			while (newline != null) {
				//logger.debug("newline: (" + newline + ")");
				if (currentline >= initialindex) {
					line = line + newline + "\n";
				}
				//logger.debug("got a line");
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

	public String getContentDisposition()
	{
		String retval = "filename=\"" + inputPath + "\"";
		return retval;
	}

	public String getContentType()
	{
		return contentType;
	}
}
