package org.ngbw.utils;
import java.util.List;
import java.util.concurrent.*;
import java.io.File;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.tool.BaseProcessWorker;
import org.ngbw.sdk.tool.TaskMonitor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.core.shared.UserRole;
import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.ConnectionSource;
import org.ngbw.sdk.database.DriverConnectionSource;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.UserDataDirItem;

/*
	For the rest service, we don't store tasks indefinitely.  This deletes all
	rest tasks over a certain age.  Will most likely run this as a cron job.
	See sdk/scripts/DeleteTasksD.

	Also deletes UserDataItem records over the same age.

	Caller should check and report exit status.  Exit status of -1 means there was
	an error.  Once an error occurs no further records are deleted.
*/
public class DeleteTasks
{
	private static final Log log = LogFactory.getLog(DeleteTasks.class.getName());
	private static String m_maxDays;
	private static Boolean m_debug;
	private static Boolean m_restOnly = true;

	public static void main(String[] args)
	{
		try
		{
			Workbench wb = Workbench.getInstance();
			log.debug("DELETE TASKS: workbench initialized.");

			//These 2 lines close the connection pool created in Workbench.getInstance() and open an 
			// unpooled connection source. 
			ConnectionManager.getConnectionSource().close();
			ConnectionManager.setConnectionSource(new DriverConnectionSource());

			m_debug = Boolean.valueOf(System.getProperty("debug"));
			m_restOnly = Boolean.valueOf(System.getProperty("restOnly"));
			m_maxDays = System.getProperty("age");
			if (m_maxDays == null)
			{
				throw new Exception("Missing system property age");
			}
			int age;
			try
			{
				age = Integer.valueOf(m_maxDays);
			}
			catch(NumberFormatException nfe)
			{
				throw new Exception("age must be an integer");
			}
			log.debug("age=" + age + ", debug=" + m_debug + ", restOnly=" + m_restOnly);
			List<Task> candidates = Task.findTasksByAge(age);
			if (candidates.isEmpty())
			{
				log.debug("Task candidates List is empty!");
			}
			else
			{
				log.debug("Task candidates has " + candidates.size() + " entries.");
			}
			for (Task task : candidates)
			{
				log.debug("Found task older than " + age + " days: " + task.getTaskId());
				long uid = task.getUserId();
				long tid = task.getTaskId();
				String jh = task.getJobHandle();
				String cdate = task.getCreationDate().toString();
				if (m_restOnly)
				{
					User user = new User(uid);
					if (!user.getRole().isRestEndUser())
					{
						log.debug("Skipping " + user.getRole() + " user task " + tid); 
						continue;
					}
				}
				if (m_debug == false)
				{
					task.delete();
					log.debug("Deleted task " + tid + ", jh=" + jh + ", uid=" + uid + ", date created = " + cdate);
				} else
				{
					log.debug("Would delete task " + tid + ", jh=" + jh + ", uid=" + uid + ", date created = " + cdate);
				}
			}
			List<UserDataItem> dataCandidates = UserDataItem.findDataItemsByAge(age);
			if (dataCandidates.isEmpty())
			{
				log.debug("dataCandidates List is empty!");
			}
			else
			{
				log.debug("dataCandidates has " + dataCandidates.size() + " entries.");
			}
			for (UserDataItem udi : dataCandidates)
			{
				long uid = udi.getUserId();
				long dataId = udi.getUserDataId();
				long sid = udi.getSourceDocumentId();
				String cdate = udi.getCreationDate().toString();
				log.debug("Found user data item older than " + age + " days: " + dataId);
				if (m_restOnly)
				{
					User user = new User(uid);
					if (!user.getRole().isRestEndUser())
					{
						log.debug("Skipping " + user.getRole() + " user data record " + uid); 
						continue;
					}
				}
				if (m_debug == false)
				{
					udi.delete();
					log.debug("Deleted user data record " + dataId + ", sourceDocument=" + sid + ", date created = " + cdate);
				}  else
				{
					log.debug("Would delete user data record " + dataId + ", sourceDocument=" + sid + ", date created = " + cdate);
				}
			}	

			List<UserDataDirItem> dataDirCandidates = UserDataDirItem.findDataItemsByAge(age);
			if (dataDirCandidates.isEmpty())
			{
				log.debug("dataDirCandidates List is empty!");
			}
			else
			{
				log.debug("dataDirCandidates has " + dataDirCandidates.size() + " entries.");
			}
			for (UserDataDirItem udi : dataDirCandidates)
			{
				long uid = udi.getUserId();
				long dataId = udi.getUserDataId();
				//long sid = udi.getSourceDocumentId();
				String cdate = udi.getCreationDate().toString();
				log.debug("Found user data item older than " + age + " days: " + dataId);
				if (m_restOnly)
				{
					User user = new User(uid);
					if (!user.getRole().isRestEndUser())
					{
						log.debug("Skipping " + user.getRole() + " user data record " + uid); 
						continue;
					}
				}
				// get folder id
				long folderid = udi.getEnclosingFolderId();
				// get user
				User user = new User ( udi.getUserId() );
				// get label
				String label_path = udi.getName();
				// delete same folder id data items
				List<UserDataDirItem> folderDataCandidates = UserDataDirItem.findItemsByUserFolderIdPath(user, folderid, label_path);
				if (m_debug == false)
				{
					udi.delete();
					log.debug("Deleted user data record " + dataId + ", date created = " + cdate);
					/*
					for (UserDataDirItem fudi : folderDataCandidates)
					{
						long fdataId = fudi.getUserDataId();
						String fcdate = fudi.getCreationDate().toString();
						fudi.delete();
						log.debug("Deleted user data record " + fdataId + ", date created = " + fcdate);
					}
					*/
					// delete parent dir
					udi.deleteDirectory();
				}  else
				{
					log.debug("Would delete user data dir record " + dataId + ", date created = " + cdate);
					/*
					for (UserDataDirItem fudi : folderDataCandidates)
					{
						long fdataId = fudi.getUserDataId();
						String fcdate = fudi.getCreationDate().toString();
						log.debug("Would delete same folder user data dir record " + fdataId + ", date created = " + fcdate);
					}
					*/
					String globusRoot = Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
    				try
    				{
						log.debug ( "folderid = " + folderid );
						Folder folder = new Folder ( folderid );
						File file = new File ( globusRoot + "/" + user.getUsername() + "/" +
						folder.getLabel() + "/" + label_path );
						log.debug ( "file = " + file );
						File path = new File ( file.getParent() );
						log.debug ( "would delete path = " + path );
    				}
    				catch(Exception e)
    				{
    					log.error("", e);
    				}
    			}
			}	

			System.exit(0);
		}
		catch (Exception e)
		{
			log.error("", e);
			System.exit(-1);
		}
		finally
		{
			ConnectionSource source = ConnectionManager.getConnectionSource();
			if (source != null)
			{
				try
				{
					source.close();
				}
				catch(Exception e)
				{
					log.error("", e);
				}
			}
		}
	}	
}
