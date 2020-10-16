
/*
 * WorkingDirectory.java
 */
package org.ngbw.sdk.tool;


import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.core.configuration.ServiceFactory;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.Task;


/**
 * 
 * @author Terri Liebowitz Schwartz
 *
 */
public class WorkingDirectory 
{
	private static final Log log = LogFactory.getLog(WorkingDirectory.class);
	protected final ServiceFactory m_serviceFactory;
	protected final Task task;

	public WorkingDirectory(ServiceFactory serviceFactory, Task task)
	{
		this.m_serviceFactory = serviceFactory;
		this.task = task;
	}


	/**
		LIST WORKING DIRECTORY
		The following methods are used to implement the "List Working Directory" feature, that lets
		a user view the contents of the remote working dir of a running job. 
	*/
	/**
		If a tool specifies that it runs on a PSUEDO resource, the real resource is
		given in the RunningTask entry, so we need to create the tool (and then filehandler),
		based on the real resource from the running task entry.  The methods are exposed
		to the outside world by WorkbenchSession.
	*/
	private Tool getTool() throws Exception
	{
		Tool tool = null;
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
		return getTool().getToolResource().getFileHandler();
	}

	public boolean fileExists(String filename) throws Exception
	{
		FileHandler fileHandler = null;
		try
		{
			fileHandler = getFileHandler();
			return fileHandler.exists(getTool().getToolResource().getWorkingDirectory(task.getJobHandle()) + "/" + filename);
		}
		finally
		{
			if (fileHandler != null)
			{
				fileHandler.close();
			}
		}
	}

	public boolean workingDirectoryExists() throws Exception
	{
		FileHandler fileHandler = null;
		try
		{
			fileHandler = getFileHandler();
			return fileHandler.exists(getTool().getToolResource().getWorkingDirectory(task.getJobHandle()));
		}
		finally
		{
			if (fileHandler != null)
			{
				fileHandler.close();
			}
		}
	}

	public String getWorkingDirectoryPath() throws Exception {
		FileHandler fileHandler = null;
		try {
			fileHandler = getFileHandler();
			//return fileHandler.exists(getTool().getToolResource().getWorkingDirectory(task.getJobHandle()));
			String workingdir = getTool().getToolResource().getWorkingDirectory(task.getJobHandle());
			//Path workingdirpath = Paths.get(workingdir);
			return workingdir;
		} finally {
			if (fileHandler != null) {
				fileHandler.close();
			}
		}
	}


	/*
		Returns a list of filenames (just the filename, not the path) in working directory.  
		Todo: currently this skips over subdirs.   

		This will throw an IOException exception if the working directory is deleted (or possibly if
		a file is removed) while this method is executing.
	*/
	public List<FileHandler.FileAttributes> listWorkingDirectory() throws IOException, Exception
	{
		FileHandler fileHandler = null; 
		try
		{

			Tool tool = getTool();

			fileHandler = tool.getToolResource().getFileHandler();

			String workingDirectory = tool.getToolResource().getWorkingDirectory(task.getJobHandle());

			List<FileHandler.FileAttributes> list = fileHandler.list(workingDirectory);

			/*
			log.debug("In listWorkingDirectory, directory is " + workingDirectory + " and there are " + 
				list.size() + " files.");
			*/
			return list;
		}
		finally
		{
			if (fileHandler != null)
			{
				fileHandler.close();
			}
		}
	}

	/*
		TODO: web app is using this.  It should be streaming, not reading the whole thing into memory!

		This will throw a RunTime exception if the working directory is deleted (or if
		file is removed) while this method is executing.
	*/
	public InputStream getFileFromWorkingDirectory(String filename) throws IOException, Exception
	{
		Tool tool = getTool();
		FileHandler fileHandler = tool.getToolResource().getFileHandler();
		String workingDirectory = tool.getToolResource().getWorkingDirectory(task.getJobHandle());
		return fileHandler.readFile(workingDirectory + "/" + filename);
	}

	/*
		Here's how the cleanup works in the gridftp case, at least:
			- The filehandler has a GridFtpClient object that it normally opens
			and closes in each of it's methods, except that inorder to return an InputStream
			it must leave the connection open.  So, it returns a GridftpInputStream which client
			is responsible for closing.  GridftpInputStream.close() closes the GridFtpClient
			that is normally closed by the GridftpFileHandler.

			- If streamFileFromWorkingDirectory throws an exception it must first
			close the InputStream so that clients don't have to. 
	*/
	public InputStream streamFileFromWorkingDirectory(String filename) throws IOException, Exception
	{
		FileHandler fileHandler = null;
		InputStream is = null;
		try
		{
			Tool tool = getTool();
			fileHandler = tool.getToolResource().getFileHandler();
			String workingDirectory = tool.getToolResource().getWorkingDirectory(task.getJobHandle());

			is = fileHandler.getInputStream(workingDirectory + "/" + filename);
			return is;
		} 
		catch(Exception e)
		{
			if (is != null)
			{
				is.close();
			}
			throw e;
		}
		finally
		{
			if (fileHandler != null)
			{
				fileHandler.close();
			}
		}
	}
}
