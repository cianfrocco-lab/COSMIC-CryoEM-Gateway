/*
 * DefaultToolResource.java
 */
package org.ngbw.sdk.tool;


import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.api.tool.ToolResource;
import org.ngbw.sdk.api.tool.ToolResourceType;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.common.util.FileUtils;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.TaskInputSourceDocument;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.Date;


/**
 *
 * @author Roland H. Niedner
 *
 */
public class DefaultToolResource implements ToolResource {

	private static Log log = LogFactory.getLog(DefaultToolResource.class.getName());
	public  static final String USERID_KEY = "ngbw_uid";
	private Class<FileHandler>  fileHandler;
	private Map<String, String> parameters;
	private String id;
	private String processWorkerName;
	private ToolResourceType type;

	public DefaultToolResource(String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}

	public ToolResourceType getType() {
		//return ToolResourceType.REMOTE_SHELL;
		return type;
	}

	public void setType(ToolResourceType type)
	{
		this.type = type;
	}

	public void setType(String type)
	{
		this.type = ToolResourceType.valueOf(type);
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public String getAccountGroup()
	{
		Map<String, String> parameters = getParameters();
		if (parameters != null)
		{
			return parameters.get("accountGroup");
		}
		return null;
	}

	public String getDefaultChargeNumber()
	{
		Map<String, String> parameters = getParameters();
		if (parameters != null)
		{
			return parameters.get("chargeNumber");
		}
		return null;
	}

	public Long getCoresPerNode()
	{
		Long l;
		String tmp;
		Map<String, String> parameters = getParameters();
		if (parameters != null)
		{
			tmp = parameters.get("coresPerNode");
			if (tmp != null)
			{
				tmp = tmp.trim();
				try
				{
					l = Long.parseLong(tmp);
					return l;
				}
				catch(Exception e)
				{
					log.error("coresPerNode has an invalid value");
				}
			}
		}
		return null;
	}

	public FileHandler getFileHandler()
	{
		return getFileHandler(-1);
	}

	public FileHandler getFileHandler(long userId)
	{
		FileHandler instance;
		try
		{
			instance = fileHandler.newInstance();
		} catch (InstantiationException e)
		{
			throw new RuntimeException(e);
		} catch (IllegalAccessException e)
		{
			throw new RuntimeException(e);
		}
		if(instance.isConfigured())
		{
			return instance;
		}
		HashMap<String, String> hm = new HashMap<String,String>(parameters);
		if (userId != -1)
		{
			hm.put(USERID_KEY, "" + userId);
		}
		if(instance.configure(hm) == false)
		{
			log.error("Error configuring file handler " + getFileHandlerName() +
				" with these parameters: ");
			for(String param : parameters.keySet())
			{
				log.error( param + " : " + parameters.get(param));
			}
			throw new RuntimeException("Cannot configure FileHandler!");
		}
		return instance;
	}

	public String getFileHandlerName()
	{
		return fileHandler.getName();
	}

	public void setFileHandlerClass(Class<FileHandler> fileHandler) {
		this.fileHandler = fileHandler;
		createWorkspace();
	}

	public String getProcessWorkerName() {
		return processWorkerName;
	}

	public void setProcessWorkerName(String processWorkerName) {
		this.processWorkerName = processWorkerName;
	}

	public boolean configure(Map<String, String> cfg)
	{
		this.parameters = cfg;
		return isConfigured();
	}

	public boolean isConfigured() {
		return this.parameters != null;
	}

	private void createWorkspace()
    {
        try
        {
            String workspace = getParameters().get("workspace");
            String create = getParameters().get("create");
            if (workspace != null && create != null && create.equals("1"))
            {
                log.debug("Creating workspace " + workspace);
                FileHandler fh = getFileHandler();
                if (!fh.exists(workspace))
                {
                    fh.createDirectory(workspace);
                    fh.createDirectory(workspace + "FAILED");
                    log.debug("Created workspace directory " + workspace);
                } else
                {
                    log.debug("Workspace directory " + workspace + " exists");
                }

            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error creating workspace directory.", e);
        }
    }


	public String disabled()
	{
		return Tool.readDisabledResourceFile(this.id);
	}

	public String getWorkspace()
	{
		String workspace = getParameters().get("workspace");
		if (workspace == null)
			workspace = "";
		return (workspace.endsWith("/") ? workspace : workspace + "/");
	}

	public void setWorkspace(String ws)
	{
		getParameters().put("workspace", ws);
	}



	public String getWorkingDirectory(String jobHandle)
	{
		return getWorkingDirectory(getWorkspace(), jobHandle);
	}

	public static String getWorkingDirectory(String workspace, String jobHandle)
	{
		return workspace + jobHandle + "/";
	}

	public String getFailedDirectory(String jobHandle)
	{
		return getFailedDirectory(getWorkspace(), jobHandle);
	}
	public static String getFailedDirectory(String workspace, String jobHandle)
	{
		return workspace + "FAILED/" + jobHandle + "/";
	}

	public String getArchiveDirectory(String jobHandle)
	{
		return getArchiveDirectory(getWorkspace(), jobHandle);
	}
	public static String getArchiveDirectory(String workspace, String jobHandle)
	{
		return workspace + "ARCHIVE/" + jobHandle + "/";
	}

	public String getManualDirectory(String jobHandle)
	{
		return getManualDirectory(getWorkspace(), jobHandle);
	}
	public static String getManualDirectory(String workspace, String jobHandle)
	{
		return workspace + "MANUAL/" + jobHandle + "/";
	}



	public boolean workingDirectoryExists(String jobhandle) throws Exception
	{
		FileHandler fh = null;
		try
		{
			fh = getFileHandler();
			return fh.exists(getWorkingDirectory(jobhandle));
		}
		finally
		{
			if (fh != null)
			{
				fh.close();
			}
		}
	}

	public BaseProcessWorker getProcessWorker(RunningTask rt) throws Exception
	{
		String workerName = getProcessWorkerName();
		Class<?> myClass = Class.forName(workerName);
		Constructor<?> ctor = myClass.getConstructor(new Class[]{RunningTask.class});
		BaseProcessWorker pw = (BaseProcessWorker)ctor.newInstance(new Object[]{rt});
		return pw;
	}

	public BaseProcessWorker getProcessWorker(String jobhandle) throws Exception
	{
		return getProcessWorker(RunningTask.find(jobhandle));
	}

	/*
		inputData maps name of file to be created to either
			- contents of file (as a string)   OR
			- a string of the form "TaskInputSourceDocument n", where n is the id of the source document in the db.
		Depending on which it is, we either write the string to the file or we get an input stream
		from the db record and write from that to the file.

		inputData map is created in TaskInitiate.convertTaskInput and TaskInitiate.stageInputData
	*/
	public void stageInput(String workingDirectory, Map<String, String> inputData)
		throws IOException, Exception
	{
        //log.debug ( "MONA: entered DefaultToolResource.stageInput()" );
		FileHandler fileHandler = null;

		try
		{
			fileHandler = getFileHandler();
			fileHandler.createDirectory(workingDirectory);

			for (String fileName : inputData.keySet())
			{
				String content = inputData.get(fileName);
				if (content == null)
				{
					throw new NullPointerException("No data for input file " + fileName);
				}
				String contentAsString= new String(content);
				if (contentAsString.startsWith("TaskInputSourceDocument"))
				{
					long documentID;
					try
					{
						int firstSpace = contentAsString.indexOf(' ');
						int secondSpace = contentAsString.indexOf(' ', firstSpace + 1);

						documentID = Long.parseLong(contentAsString.substring(firstSpace + 1, secondSpace));
                        //log.debug ( "MONA: documenID = " + documentID );
					}
					catch(Exception e)
					{
						log.warn("Error parsing TaskInputSourceDocument id: " + e.getMessage());
						throw e;
					}
					TaskInputSourceDocument document = new TaskInputSourceDocument(documentID);
                    //log.debug ( "MONA: document = " + document );
                    //log.debug ( "MONA: datatype = " + document.getDataType() );

                    if ( document.getDataType() != DataType.DIRECTORY )
                    {
					    InputStream is = document.getDataAsStream();
                        //log.debug ( "MONA: document = " + document );
                        //log.debug ( "MONA: is = " + is );
					    try
					    {
						    log.debug("Staging from db: " + fileName);
						    fileHandler.writeFile(workingDirectory + fileName, is);
						    log.debug("Finished staging " + fileName);
					    }
					    finally
					    {
                            if ( is != null )
						        is.close();
					    }
                    }
				} else
				{
					log.debug("Staging from memory: " + fileName);
					fileHandler.writeFile(workingDirectory + fileName, content);
					log.debug("Finished staging " + fileName);
				}
			}
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
