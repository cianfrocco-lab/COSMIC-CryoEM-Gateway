package edu.sdsc.globusauth.controller;

/**
 * Created by cyoun on 10/12/16.
 * Updated by Mona Wong Feb 2020
 */

import java.util.ArrayList;
import java.io.IOException;
import java.sql.SQLException;
import java.io.*;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.database.OauthProfile;
import org.ngbw.sdk.database.TransferRecord;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.sdk.common.util.ProcessRunner;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.core.io.SSLConnectionManager;
import org.ngbw.sdk.common.util.SSHExecProcessRunner;
import org.ngbw.sdk.WorkbenchException;

import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;


public class ProfileManager extends NgbwSupport
{
    private static final Log log = LogFactory.getLog ( ProfileManager.class );

    private String[] getList ( String s )
    {
        if ( s == null || s.trim().equals ( "" ) )
            return null;
        else
            return ( s.split ( "\\|" ) );
    }

	private static final String sshCommand = "ssh -o StrictHostKeyChecking=no ";
        private static final String FILEHOST = "fileHost";
        private static final String LOGIN = "login";
	private String m_host;
	private String host;
	private String username;
	private File keyfile;
	private String connectString;
	private SSHExecProcessRunner pr;
	private ProcessRunner lpr;

	protected SSHExecProcessRunner getProcessRunner() //throws Exception
	{
		SSHExecProcessRunner runner;
        	runner = new SSHExecProcessRunner();
                HashMap<String, String> cfg = new HashMap<String, String>();
                cfg.put(LOGIN, m_host);
                if (runner.configure(cfg))
                {
                        return runner;
                }
                throw new WorkbenchException("Process Runner Not configured.");
        }

    public boolean isConfigured()
    {
        return m_host != null;
    }


    public boolean configure(Map<String, String> cfg)
    {
        if (cfg != null && cfg.containsKey(FILEHOST))
        {
            try {
                m_host = cfg.get(FILEHOST);
                log.debug("Configured host: " + m_host);

                this.host = SSLConnectionManager.getInstance().getHost(m_host);
                this.username = SSLConnectionManager.getInstance().getUsername(m_host);
                this.keyfile = SSLConnectionManager.getInstance().getKeyfile(m_host);
                this.connectString = "-i " + this.keyfile.getAbsolutePath()  + " " +
                this.username + "@" + this.host;
                log.debug("Configured SSHExecProcessRunner from ssl.properties for " + m_host +
                ", connectString=" + this.connectString);
                        }
                        catch(Exception e)
                        {
                                log.error("", e);
                        }
        } else
        {
            log.error("Missing parameter: " + FILEHOST);
        }

        return isConfigured();
    }

    public void addProfile(OauthProfile profile) throws IOException, SQLException
    {
        log.debug("Adding linkage for globus user " + profile.getUsername());
        profile.save();
    }

    public void updateLinkUsername(String identityId, String linkUsername) throws IOException, SQLException
    {
        OauthProfile profile = load(identityId);
        profile.setLinkUsername(linkUsername);
        profile.save();
    }

    public void update(String identityId, String firstname,
                       String lastname, String email, String institution)
            throws IOException, SQLException
    {
        OauthProfile profile = load(identityId);
        profile.setFirstname(firstname);
        profile.setLastname(lastname);
        profile.setEmail(email);
        profile.setInstitution(institution);
        profile.save();
    }

    public OauthProfile load(String identityId) throws IOException, SQLException
    {
        return OauthProfile.findOauthprofileByIdentityId(identityId);
    }

    public void addRecord(TransferRecord tr) throws IOException, SQLException {
        log.debug("Adding transfer recordr " + tr.getTaskId());
        tr.save();
    }

    public void updateTransferRecord(String taskId,
                                     String status,
                                     String completionTime,
                                     int filesTransferred,
                                     int faults,
                                     int directories,
                                     int files,
                                     int filesSkipped,
                                     long byteTransferred) throws IOException, SQLException {

        //log.debug ( "MONA: entered ProfileManager.updateTransferRecord()" );
        //log.debug ( "MONA: taskId = " + taskId );
        //log.debug ( "MONA: status = " + status );
        log.info("updatetransferrecord: start");
        TransferRecord tr = TransferRecord.findTransferRecordByTaskId(taskId);
        //log.info("endpoint: "+tr.getSrcEndpointname());
        tr.setStatus(status);
        //log.info("status: "+ status);
        tr.setCompletionTime(completionTime);
        //log.info("completionTime: "+ completionTime);
        tr.setFilesTransferred(filesTransferred);
        //log.info("filesTransferred: "+ filesTransferred);
        tr.setFaults(faults);
        //log.info("faults: "+ faults);
        tr.setDirectories(directories);
        //log.info("directories: "+ directories);
        tr.setDirectories(directories);
        tr.setFiles(files);
        //log.info("files: "+ files);
        tr.setFilesSkipped(filesSkipped);
        //log.info("filesSkipped: "+ filesSkipped);
        tr.setByteTransferred(byteTransferred);
        //log.info("byteTransferred: "+ byteTransferred);
        tr.save();
    }

    /**
     * IMPORTANT : This function assumes that the incoming tr parameter
     *      current status value in the database is ACTIVE or INACTIVE.
     *      If this NOT the case, DO NOT use this function!
     **/
    public Transfer2DataManager updateRecord
        ( TransferRecord tr, String destination_path, String destination_name ) throws IOException, InterruptedException, SQLException, Exception
    {
        //log.debug ( "MONA: entered ProfileManager.updateRecord()" );
        //log.debug ( "MONA: tr = " + tr );
        //log.debug ( "MONA: destination_path = " + destination_path );
        //log.debug ( "MONA: destination_name = " + destination_name );
        //log.debug ( "MONA: tr.getSrcEndpointname = " + tr.getSrcEndpointname() );
        //log.debug ( "MONA: tr.getTaskId = " + tr.getTaskId() );
        //log.debug ( "MONA: tr.getStatus = " + tr.getStatus() );

        // Check incoming parameters
        if ( tr == null || destination_path == null ||
                destination_path.trim().equals ( "" ) )
            return ( null );

	String[] cmdarray;
	int sleepcount;
	int sleepthreshold;
	Path toppath;
        Properties config =
            OauthUtils.getConfig ( OauthConstants.OAUTH_PORPS );
        String gateway_endpoint_name =
            config.getProperty ( OauthConstants.DATASET_ENDPOINT_NAME );
        //log.debug ( "MONA: gateway_endpoint_name = " + gateway_endpoint_name );
        String globusRoot =
            Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
        //log.debug ( "MONA: globusRoot = " + globusRoot );
        String status = tr.getStatus();
        //log.debug ( "MONA: status = " + status );
        Transfer2DataManager tdm = new Transfer2DataManager();

        if ( globusRoot == null )
        {
            log.error ( "System Error: Globus root directory not found!" );
            tdm.setSystemError ( true );
            ArrayList<String> error = new ArrayList<String>( 1 );
            error.add ( "A Globus system error has been encountered" );
            tdm.setUserSystemErrorMessages ( error );
            return ( tdm );
        }

        // If the transfer is to the COSMIC2 gateway, then we will create
        // the appropriate user data dir item; otherwise, the transfer is
        // from the COSMIC2 gateway so no need to create a user data dir item
        //if ( destination_path.startsWith ( globusRoot ) )
        if ( destination_name.equals ( gateway_endpoint_name ) )
        {
            destination_path = globusRoot + destination_path;
            //log.debug ( "MONA: new destination_path = " + destination_path );
            //log.debug ( "MONA: transferring TO gateway" );
            TransferRecord old_tr = null;

            // First get the old transfer record...
            try { old_tr = loadRecordByTaskId ( tr.getTaskId() ); }
            catch ( Exception e )
            {
                // If no old transfer record...this shouldn't happen but
                // is also not a problem since we can just save the the new
                // transfer info
                log.error
                    ( "System Warning: no transfer record with task id = " +
                    tr.getTaskId() + " found!" );
            }
            //log.debug ( "after loadRecordByTaskId" );

            if ( old_tr != null && old_tr.getTaskId() != null )
            {
                // If the status has changed...
                if ( ! status.equals ( old_tr.getStatus() ) )
                {
                    //log.debug ( "before setupDataItems" );
                    //log.debug ( "old_tr.getDirectoryNames(): " + old_tr.getDirectoryNames() );
                    //log.debug ( "old_tr.getEnclosingFolderId(): " + old_tr.getEnclosingFolderId() );
		    Folder folder = new Folder ( old_tr.getEnclosingFolderId() );
                    String folderstring = folder.getLabel();
		    User user = new User ( old_tr.getUserId() );
		    // Append user folder label to destination_path
		    String new_destination_path = destination_path + folder.getLabel() +
		                "/";
		    //log.debug ( "MONA : new destination_path = " + new_destination_path );
		    // Get the transferred directories and files
		    String dirs[] = getList ( old_tr.getDirectoryNames() );
		    //log.debug ( "MONA : dirs = " + Arrays.toString ( dirs ) );
		    String files[] = getList ( old_tr.getFileNames() );
		    //log.debug ( "after getList" );

                    // If transfer successfully, create database entries
                    if ( status.equals ( "SUCCEEDED" ) ) {
			//log.debug("status is SUCCEEDED, creating db entries");
			//log.debug("dirs.length: " + dirs.length);
			//log.debug("dirs[0]: " + dirs[0]);
			// harcode m_host.  This is the key used to pull info
			// from ssl.properties stanza
			// this should be a configurable in build.properties
			// or oauth_consumer.properties
			m_host = "expanse";
			// m_host is used inside getProcessRunner
			//SSHExecProcessRunner pr=null;
			pr = getProcessRunner();
			// instead of hardcoding elements of the path,
			// should put this in build.properties or oauth_consumer
			// propertie.  Or maybe stanza in ssl.properties
			String cmdstring = "chmod g+rwx '/expanse" + new_destination_path + "'";
			int exitCode = pr.run(cmdstring);
			if (exitCode != 0){
				//log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
				//log.debug("stdout: " + pr.getStdOut());
				//log.debug("stderr: " + pr.getStdErr());
				throw new IOException(cmdstring + " failed with exitCode " + exitCode);
			} else {
				//log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
				//log.debug("stdout: " + pr.getStdOut());
				//log.debug("stderr: " + pr.getStdErr());
			}
			//log.debug("Ran: " + cmdstring);
			pr.close();
			pr = getProcessRunner();
			cmdstring = "ls -ld '/expanse" + new_destination_path + "'";
			exitCode = pr.run(cmdstring);
			if (exitCode != 0){
				//log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
				//log.debug("stdout: " + pr.getStdOut());
				//log.debug("stderr: " + pr.getStdErr());
				throw new IOException(cmdstring + " failed with exitCode " + exitCode);
			} else {
				//log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
				//log.debug("stdout: " + pr.getStdOut());
				//log.debug("stderr: " + pr.getStdErr());
			}
			//log.debug("Ran: " + cmdstring);
			pr.close();
			//// https://www.codejava.net/java-se/file-io/execute-operating-system-commands-using-runtime-exec-methods
			//cmdstring = "ls -ld '" + new_destination_path + "'";
			//cmdstring = "echo \'" + new_destination_path + "\'"
			//	+ " '" + new_destination_path + "'";
			// instead of string, a string array is needed
			// if there is a space in the path, since ProcessRunner
			// run(String) splits the string on spaces into separate
			// args
			cmdarray = new String[]{"ls", "-ld", new_destination_path};
       		       	ProcessRunner lpr = new ProcessRunner(true);
       		       	//exitCode = lpr.run(cmdstring);
			sleepcount = 0;
			sleepthreshold = 10;
			exitCode = 1;
			while (exitCode != 0 && sleepcount < sleepthreshold){
       		       		exitCode = lpr.run(cmdarray);
       		     		//log.debug( "after ls -ld, with exitCode (" + exitCode + ")" );
       		     		//log.debug( "lpr.getStdOut(): (" + lpr.getStdOut() + ")" );
	            		//log.debug( "lpr.getStdErr(): (" + lpr.getStdErr() + ")" );
				//log.debug("after running cmdarray: " + cmdarray.toString());
				Thread.sleep(2000);
				sleepcount = sleepcount + 1;
			}
		    	for ( int i = 0; i <= dirs.length - 1; i++){
				//log.debug("dirs[" + i + "] : " + dirs[i]);
				//log.debug("dirs[" + i + "] : " + dirs[i].toString());
				toppath = Paths.get(dirs[i]);
				//log.debug("toppath: " + toppath);
				//log.debug("toppath.getName(0): " + toppath.getName(0));
				//DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
				//https://www.geeksforgeeks.org/path-iterator-method-in-java-with-examples/
				Iterator<Path> pathelements = toppath.iterator();
				int totalelements = toppath.getNameCount();
				int currentelementindex;
				for (currentelementindex = 0; currentelementindex < totalelements; currentelementindex = currentelementindex + 1){
					//log.debug("toppath.getName(currentelementindex): " + toppath.getName(currentelementindex));
					if (currentelementindex == totalelements - 1){
						//log.debug("last element: " + currentelementindex);
						pr = getProcessRunner();
						cmdstring = "/expanse/projects/cosmic2/expanse/gatewaydev/COSMIC-CryoEM-Gateway/remote_scripts/chmod.sh '/expanse" + new_destination_path + dirs[i] + "'";
						exitCode = pr.run(cmdstring);
						if (exitCode != 0){
							//log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
							//log.debug("stdout: " + pr.getStdOut());
							//log.debug("stderr: " + pr.getStdErr());
							throw new IOException(cmdstring + " failed with exitCode " + exitCode);
						} else {
							//log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
							//log.debug("stdout: " + pr.getStdOut());
							//log.debug("stderr: " + pr.getStdErr());
						}
						//log.debug("Ran: " + cmdstring);
						pr.close();
					} else {
						//log.debug("not last element: " + currentelementindex);
						pr = getProcessRunner();
						cmdstring = "chmod g+rwx '/expanse" + new_destination_path + toppath.getName(0) + "'";
						exitCode = pr.run(cmdstring);
						if (exitCode != 0){
							//log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
							//log.debug("stdout: " + pr.getStdOut());
							//log.debug("stderr: " + pr.getStdErr());
							throw new IOException(cmdstring + " failed with exitCode " + exitCode);
						} else {
							//log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
							//log.debug("stdout: " + pr.getStdOut());
							//log.debug("stderr: " + pr.getStdErr());
						}
						//log.debug("Ran: " + cmdstring);
						pr.close();
					}
				}
				//for (Path file: stream) {
				//	log.debug(file.getFileName());
				//}
				// instead of local ProcessRunner, try using
				// SSHExecProcessRunner.  Don't have a good
				// mapping of Globus endpoint to remote host...
				// m_host is used inside getProcessRunner
				//SSHExecProcessRunner pr=null;
				//testloop
				//cmdstring = "chmod g+rwx '/expanse" + new_destination_path + toppath.getName(0) + "'";
				exitCode = pr.run(cmdstring);
				//if (exitCode != 0){
				//	log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
				//	log.debug("stdout: " + pr.getStdOut());
				//	log.debug("stderr: " + pr.getStdErr());
				//	throw new IOException(cmdstring + " failed with exitCode " + exitCode);
				//} else {
				//	log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
				//	log.debug("stdout: " + pr.getStdOut());
				//	log.debug("stderr: " + pr.getStdErr());
				//}
				//log.debug("Ran: " + cmdstring);
				//pr.close();
				//pr = getProcessRunner();
				//cmdstring = "chmod g+rwx '/expanse" + new_destination_path + dirs[i].toString() + "'";
				// instead of doing the whole toppath, should
				// get the first directory in dirs[i] path,
				// then chmod.sh that
				//cmdstring = "/expanse/projects/cosmic2/expanse/gatewaydev/COSMIC-CryoEM-Gateway/remote_scripts/chmod.sh '/expanse" + new_destination_path + toppath.getName(0) + "'";
				// should use just chmod.sh, trusting it will
				// be in the path from remote bashrc
				//cmdstring = "/expanse/projects/cosmic2/expanse/gatewaydev/COSMIC-CryoEM-Gateway/remote_scripts/chmod.sh '/expanse" + new_destination_path + toppath.getName(0) + "/" + toppath.getName(1) + "'";
				//exitCode = pr.run(cmdstring);
				//if (exitCode != 0){
				//	log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
				//	log.debug("stdout: " + pr.getStdOut());
				//	log.debug("stderr: " + pr.getStdErr());
				//	throw new IOException(cmdstring + " failed with exitCode " + exitCode);
				//} else {
				//	log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
				//	log.debug("stdout: " + pr.getStdOut());
				//	log.debug("stderr: " + pr.getStdErr());
				//}
				//log.debug("Ran: " + cmdstring);
				//pr.close();
				cmdarray = new String[]{"ls", "-lR", new_destination_path + toppath.getName(0)};
				sleepcount = 0;
				sleepthreshold = 10;
				exitCode = 1;
				while (exitCode != 0 && sleepcount < sleepthreshold){
       		         		lpr = new ProcessRunner(true);
       		         		exitCode = lpr.run(cmdarray);
       		     			//log.debug( "after ls -ld , with exitCode (" + exitCode + ")" );
       		     			//log.debug( "lpr.getStdOut(): (" + lpr.getStdOut() + ")" );
	            			//log.debug( "lpr.getStdErr(): (" + lpr.getStdErr() + ")" );
					//log.debug("after running cmdarray: " + cmdarray.toString());
					Thread.sleep(2000);
					sleepcount = sleepcount + 1;
				}
				//log.debug("exited loop with exitCode: " + exitCode + " and sleepcount: " + sleepcount);
				//pr = getProcessRunner();
				//cmdstring = "/expanse/projects/cosmic2/expanse/gatewaydev/COSMIC-CryoEM-Gateway/remote_scripts/chmod.sh '/expanse" + new_destination_path + dirs[i].toString() + "'";
		                 //exitCode = pr.run(cmdstring);
               			 //if (exitCode != 0){
                       		//	 log.debug("cmdstring: " + cmdstring + " failed with exitCode " + exitCode);
             		        //	 log.debug("stdout: " + pr.getStdOut());
              		         //	 log.debug("stderr: " + pr.getStdErr());
                  		//	 throw new IOException(cmdstring + " failed with exitCode " + exitCode);
               		 	//} else {
                       		//	 log.debug("cmdstring: " + cmdstring + " succeeded with exitCode " + exitCode);
             		        //	 log.debug("stdout: " + pr.getStdOut());
              		         //	 log.debug("stderr: " + pr.getStdErr());
				//}

                		//log.debug("Ran: " + cmdstring);
                		//pr.close();
				//// https://www.codejava.net/java-se/file-io/execute-operating-system-commands-using-runtime-exec-methods
				//cmdstring = "ls -lR \"" + new_destination_path + dirs[i].toString() + "\"";
				//cmdstring = "ls -lR \'" + new_destination_path + dirs[i].toString() + "\'";
				cmdarray = new String[]{"ls", "-lR", new_destination_path + dirs[i].toString()};
				sleepcount = 0;
				sleepthreshold = 10;
				exitCode = 1;
				while (exitCode != 0 && sleepcount < sleepthreshold){
       		         		lpr = new ProcessRunner(true);
       		         		exitCode = lpr.run(cmdarray);
       		     			//log.debug( "after ls -lR , with exitCode (" + exitCode + ")" );
       		     			//log.debug( "lpr.getStdOut(): (" + lpr.getStdOut() + ")" );
	            			//log.debug( "lpr.getStdErr(): (" + lpr.getStdErr() + ")" );
					//log.debug("after running cmdarray: " + cmdarray.toString());
					Thread.sleep(2000);
					sleepcount = sleepcount + 1;
				}
				//log.debug("exited loop with exitCode: " + exitCode + " and sleepcount: " + sleepcount);
				//Path path = new Path(new_destination_path);
				File path = new File ( new_destination_path + dirs[i].toString() );
				if (path.isDirectory()) {
					//log.debug ( path.getAbsolutePath() + " is a directory, doing listFiles()");
					String[] starfile_ext = { "star" };
					Collection < File > globusfiles =
						FileUtils.listFiles ( path, starfile_ext, false );
					//log.debug ( "globusfiles = " + globusfiles );
				} else {
					//log.debug ( path.getAbsolutePath() + " not a directory, returning null");
				}
				
		   	 }
			//log.debug("after all chmods");
                        tdm.setupDataItems ( old_tr, destination_path );
			//log.debug("after setupDataItems");
			}
                }
                //log.debug ( "after setupDataItems" );

                // Now determine the status for the transfer record
                if ( (tdm.getFailedFilesMessages()).size() > 0 )
                {
                    if ( tdm.getNumFilesSaved() > 0 )
                        status = "PARTIAL";
                    else
                        status = "FAILED";
                }
                //log.debug ( "after getFailedFilesMessages" );

                /*
                log.debug ( "MONA: status = " + status );
                log.debug ( "MONA: failed directories messages size = " +
                    (tdm.getFailedDirectoriesMessages()).size() );
                log.debug ( "MONA: num directories saved = " +
                    tdm.getNumDirectoriesSaved() );
                */

                if ( (tdm.getFailedDirectoriesMessages()).size() > 0 )
                {
                    if ( tdm.getNumDirectoriesSaved() > 0 )
                        status = "PARTIAL";
                    else if ( status.equals ( "SUCCEEDED" ) )
                        status = "FAILED";
                }
                //log.debug ( "after getFailedDirectoriesMessages" );

            } // if ( old_tr != null && old_tr.getTaskId() != null )
        } // if ( destination_name.equals ( gateway_endpoint_name ) )

        // log.info("Update record (taskid): "+tr.getTaskId());
        try
        {
            //log.debug ( "MONA: updating transfer record status = " + status );
            updateTransferRecord ( tr.getTaskId(), status,
                tr.getCompletionTime(), tr.getFilesTransferred(),
                tr.getFaults(), tr.getDirectories(), tr.getFiles(),
                tr.getFilesSkipped(), tr.getByteTransferred() );
        }
        catch ( Exception e )
        {
            log.error ( "System Error: cannot update transfer record ID = " +
                tr.getTrId() );
            tdm.setSystemError ( true );
            ArrayList<String> error = new ArrayList<String>( 1 );
            error.add ( "Sorry, cannot update your transfer record" );
            tdm.setUserSystemErrorMessages ( error );
        }

        return ( tdm );
    }

    public List<String> loadRecord(long userId) throws IOException, SQLException {
        List<TransferRecord> trs = TransferRecord.findAllTaskIDByUserId(userId);
        List<String> taskids = new ArrayList<String>();
        for (TransferRecord tr: trs) {
            taskids.add(tr.getTaskId());
        }
        return taskids;
    }

    /**
     * Get the userId, status and enclosingFolderId from the transfer_record
     * table by taskId
     * @param taskId - Globus transfer task ID
     **/
    public TransferRecord loadRecordByTaskId ( String taskId ) throws IOException, SQLException
    {
        return TransferRecord.findTransferRecordByTaskId(taskId);
    }
}
