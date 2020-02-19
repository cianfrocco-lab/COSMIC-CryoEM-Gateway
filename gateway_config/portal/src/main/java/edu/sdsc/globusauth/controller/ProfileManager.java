package edu.sdsc.globusauth.controller;

/**
 * Created by cyoun on 10/12/16.
 */

import java.util.ArrayList;
import java.io.IOException;
import java.sql.SQLException;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import edu.sdsc.globusauth.controller.Transfer2DataManager;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.database.OauthProfile;
import org.ngbw.sdk.database.TransferRecord;


//public class ProfileManager extends HibernateUtil {
public class ProfileManager {

    private static final Log log = LogFactory.getLog ( ProfileManager.class );

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

        log.info("updatetransferrecord: start");
        TransferRecord tr = TransferRecord.findTransferRecordByTaskId(taskId);
        log.info("endpoint: "+tr.getSrcEndpointname());
        tr.setStatus(status);
        tr.setCompletionTime(completionTime);
        tr.setFilesTransferred(filesTransferred);
        tr.setFaults(faults);
        tr.setDirectories(directories);
        tr.setFiles(files);
        tr.setFilesSkipped(filesSkipped);
        tr.setByteTransferred(byteTransferred);
        tr.save();
    }

    /**
     * IMPORTANT : This function assumes that the incoming tr parameter
     *      current status value in the database is ACTIVE or INACTIVE.
     *      If this NOT the case, DO NOT use this function!
     **/
    public int updateRecord ( TransferRecord tr, String destination_path )
        throws IOException, SQLException
    {
        //log.debug ( "MONA: entered ProfileManager.updateRecord()" );
        //log.debug ( "MONA: tr = " + tr );
        //log.debug ( "MONA: destination_path = " + destination_path );
        //log.debug ( "MONA: tr.getSrcEndpointname = " + tr.getSrcEndpointname() );
        //log.debug ( "MONA: tr.getDestEndpointname = " + tr.getDestEndpointname() );
        //log.debug ( "MONA: tr.getTaskId = " + tr.getTaskId() );

        // Check incoming parameters
        if ( tr == null || destination_path == null ||
                destination_path.trim().equals ( "" ) )
            return ( 0 );

        String globusRoot =
            Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
        //log.debug ( "MONA: globusRoot = " + globusRoot );
        int saved = 0;
        String status = tr.getStatus();
        //log.debug ( "MONA: status = " + status );

        // If the transfer is to the COSMIC2 gateway, then we will create
        // the appropriate user data dir item; otherwise, the transfer is
        // from the COSMIC2 gateway so no need to create a user data dir item
        if (globusRoot != null) {
            if (destination_path.startsWith(globusRoot)) {

                // First get the old transfer record...
                TransferRecord old_tr = loadRecordByTaskId ( tr.getTaskId() );
                if ( old_tr != null && old_tr.getTaskId() != null )
                {
                    // If the status has changed...
                    if ( ! status.equals ( old_tr.getStatus() ) )
                    {
                        // If transfer successfully, create database entries
                        if ( status.equals ( "SUCCEEDED" ) )
                        {
                            Transfer2DataManager dataManager =
                                new Transfer2DataManager();
                            saved = dataManager.setupDataItems ( old_tr,
                                    destination_path );
                        }
                        else if ( status.equals ( "FAILED" ) )
                        {
                            updateTransferRecord ( tr.getTaskId(), "FAILED",
                                tr.getCompletionTime(),
                                tr.getFilesTransferred(), tr.getFaults(),
                                tr.getDirectories(), tr.getFiles(),
                                tr.getFilesSkipped(),
                                tr.getByteTransferred() );
                            saved = -1;
                        }
                    } // if ( ! status.equals ( old_tr.getStatus() ) )
                } // if ( old_tr != null && old_tr.getTaskId() != null )
            } // if (destination_path.startsWith(globusRoot))
        } // if (globusRoot != null)
        //log.debug ( "MONA: saved = " + saved );

        // Now update the transfer record
        // log.info("Update record (taskid): "+tr.getTaskId());
        if  ( saved != -1 )
            updateTransferRecord ( tr.getTaskId(), tr.getStatus(),
                tr.getCompletionTime(), tr.getFilesTransferred(),
                tr.getFaults(), tr.getDirectories(), tr.getFiles(),
                tr.getFilesSkipped(), tr.getByteTransferred() );

        return ( saved );
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
