package edu.sdsc.globusauth.controller;

/**
 * Created by cyoun on 10/12/16.
 * Updated by Mona Wong Feb 2020
 */

import java.util.ArrayList;
import java.io.IOException;
import java.sql.SQLException;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.database.OauthProfile;
import org.ngbw.sdk.database.TransferRecord;
import org.ngbw.web.actions.NgbwSupport;

import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;


public class ProfileManager extends NgbwSupport
{
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

        //log.debug ( "MONA: entered ProfileManager.updateTransferRecord()" );
        //log.debug ( "MONA: taskId = " + taskId );
        //log.debug ( "MONA: status = " + status );
        //log.info("updatetransferrecord: start");
        TransferRecord tr = TransferRecord.findTransferRecordByTaskId(taskId);
        //log.info("endpoint: "+tr.getSrcEndpointname());
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
    public Transfer2DataManager updateRecord
        ( TransferRecord tr, String destination_path, String destination_name )
    {
        log.debug ( "MONA: entered ProfileManager.updateRecord()" );
        //log.debug ( "MONA: tr = " + tr );
        log.debug ( "MONA: destination_path = " + destination_path );
        log.debug ( "MONA: destination_name = " + destination_name );
        //log.debug ( "MONA: tr.getSrcEndpointname = " + tr.getSrcEndpointname() );
        //log.debug ( "MONA: tr.getTaskId = " + tr.getTaskId() );
        //log.debug ( "MONA: tr.getStatus = " + tr.getStatus() );

        // Check incoming parameters
        if ( tr == null || destination_path == null ||
                destination_path.trim().equals ( "" ) )
            return ( null );

        Properties config =
            OauthUtils.getConfig ( OauthConstants.OAUTH_PORPS );
        String gateway_endpoint_name =
            config.getProperty ( OauthConstants.DATASET_ENDPOINT_NAME );
        log.debug ( "MONA: gateway_endpoint_name = " + gateway_endpoint_name );
        String globusRoot =
            Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
        log.debug ( "MONA: globusRoot = " + globusRoot );
        String status = tr.getStatus();
        log.debug ( "MONA: status = " + status );
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
            //destination_path = globusRoot + destination_path;
            log.debug ( "MONA: new destination_path = " + destination_path );
            log.debug ( "MONA: transferring TO gateway" );
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

            if ( old_tr != null && old_tr.getTaskId() != null )
            {
                // If the status has changed...
                if ( ! status.equals ( old_tr.getStatus() ) )
                {
                    // If transfer successfully, create database entries
                    if ( status.equals ( "SUCCEEDED" ) )
                        tdm.setupDataItems ( old_tr, destination_path );
                }

                // Now determine the status for the transfer record
                if ( (tdm.getFailedFilesMessages()).size() > 0 )
                {
                    if ( tdm.getNumFilesSaved() > 0 )
                        status = "PARTIAL";
                    else
                        status = "FAILED";
                }

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
            } // if ( old_tr != null && old_tr.getTaskId() != null )
        } // if ( destination_name.equals ( gateway_endpoint_name ) )

        // log.info("Update record (taskid): "+tr.getTaskId());
        try
        {
            log.debug ( "MONA: updating transfer record status = " + status );
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
