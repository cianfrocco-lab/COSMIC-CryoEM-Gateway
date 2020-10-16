package org.ngbw.web.actions;

import org.ngbw.sdk.database.Task;
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

import org.apache.struts2.interceptor.validation.SkipValidation;


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
public class DownloadAction extends ManageTasks 
{
	private static final Logger logger = Logger.getLogger(DownloadAction.class.getName());
	private String contentType;
	InputStream is;
	private String inputPath;
	public String getInputPath() { return inputPath; }
	public void setInputPath(String inputPath) {this.inputPath = inputPath; }


	public InputStream getInputStream() 
	{
		return is;
	}

	@SkipValidation
	private InputStream getTheData(String inputPath)  throws Exception
	{
		Task task = null;
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) 
		{
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
		} 
		if ((task = getCurrentTask()) == null) 
		{
			throw new Exception("No task selected.");
		}
		return getWorkbenchSession().getFileFromWorkingDirectory(task, inputPath);
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
		try
		{
			if ( !BaseValidator.isSimplePathFilename( inputPath )) {
				logger.warn ( "path '" + inputPath + "' is not an acceptable filename.  Filenames are restricted due to security concerns." );
				throw new Exception (
					"inputPath " + inputPath +
					" has a string that contains " +
					"characters other than ascii letters, numbers, single period, underscore and backslash" );
			}
			is = getTheData(inputPath);
		}
		catch (Exception t)
		{
			// An error is expected here if the user tries to download the file as it's being deleted or after it's
			// been deleted.   
			logger.debug(t);
			is = new ByteArrayInputStream(new byte[0]);
		}
		contentType = "application/x-unknown";
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
