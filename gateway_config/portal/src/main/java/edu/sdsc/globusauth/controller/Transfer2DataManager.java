/**
 * This class is intended to act as controller/manager bridging the CIPRES
 * framework and Choonhan Youn's Globus package 
 *
 * @author Mona Wong
 */

package edu.sdsc.globusauth.controller;

/*
import java.util.Calendar;
import java.util.Date;

//import org.ngbw.sdk.*;
import org.ngbw.sdk.database.GlobusTransferFolder;

import edu.sdsc.globusauth.model.OauthProfile;
import edu.sdsc.globusauth.model.User;
import edu.sdsc.globusauth.util.HibernateUtil;
import edu.sdsc.globusauth.util.StringUtils;
import edu.sdsc.globusauth.util.UserRole;

import org.hibernate.Session;
import org.hibernate.query.Query;

*/

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.GlobusFolder;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.WorkbenchSession;
//import org.ngbw.web.actions.DataManager;
//import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.web.controllers.FolderController;

import edu.sdsc.globusauth.model.TransferRecord;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;


//public class Transfer2DataManager extends NgbwSupport
public class Transfer2DataManager
{
    private static final Log log = LogFactory.getLog
        ( Transfer2DataManager.class );

    /**
     * If the incoming newTransferRecord.getStatus() == "SUCCEEDED", then
     * compare current status in database and if they differ, this means the
     * Globus transfer info needs to flow to the CIPRES framework so data
     * can show up in UI and be available for job submission.
     * @return - number of data items created; null if invalid incoming
     *      parameters or newTransferRecord.getStatus() != "SUCCEEDED"
     **/
    public int setupDataItems
        ( TransferRecord newTransferRecord, ProfileManager profileManager,
          String destination_path, WorkbenchSession wbs )
    {
        if ( newTransferRecord == null || profileManager == null ||
            destination_path == null || wbs == null || 
            destination_path.trim().equals ( "" ) ||
            ! newTransferRecord.getStatus().equals ( "SUCCEEDED" ) )
            return ( 0 );

        //NgbwSupport x = new NgbwSupport();
        log.debug ( "MONA : Transfer2DataManager.setupDataItems() 3" );
        //x.debug ( "MONA : destination_path = " + destination_path );
        //debug ( "MONA : wbs = " + wbs );
        //x.debug ( "MONA : newTransferRecord = " + newTransferRecord );
        //x.debug ( "MONA : newTransferRecord id = " + newTransferRecord.getId() );
        //x.debug ( "MONA : newTransferRecord userid = " + newTransferRecord.getUserId() );
        //x.debug ( "MONA : newTransferRecord status = " + newTransferRecord.getStatus() );
        //x.debug ( "MONA : newTransferRecord folder id = " + newTransferRecord.getEnclosingFolderId() );
        //x.debug ( "MONA : newTransferRecord task id = " + newTransferRecord.getTaskId() );
        //debug ( "MONA : newTransferRecord filenames = " + newTransferRecord.getFileNames() );
        //debug ( "MONA : newTransferRecord directory names = " + newTransferRecord.getDirectoryNames() );

        List <TransferRecord> transferRecords =
            profileManager.loadRecordByTaskId
            ( newTransferRecord.getTaskId() );
        //x.debug ( "MONA : transferRecords = " + transferRecords );
        //x.debug ( "MONA : transferRecords.size() = " + transferRecords.size() );

        if ( transferRecords.isEmpty() )
            return ( 0 );

        int saved = 0;
        TransferRecord oldTransferRecord = transferRecords.get ( 0 );
        //x.debug ( "MONA : oldTransferRecord = " + oldTransferRecord );
        //x.debug ( "MONA : oldTransferRecord id = " + oldTransferRecord.getId() );
        //x.debug ( "MONA : oldTransferRecord userid = " + oldTransferRecord.getUserId() );
        //x.debug ( "MONA : oldTransferRecord status = " + oldTransferRecord.getStatus() );
        //x.debug ( "MONA : oldTransferRecord folder id = " + oldTransferRecord.getEnclosingFolderId() );
        //debug ( "MONA : oldTransferRecord filenames = " + oldTransferRecord.getFileNames() );
        //debug ( "MONA : oldTransferRecord directory names = " + oldTransferRecord.getDirectoryNames() );

        // If status has not changed, don't continue
        if ( newTransferRecord.getStatus().equals
            ( oldTransferRecord.getStatus() ) )
            return ( saved );

        /*
        FolderController controller = getFolderController();
        x.debug ( "MONA : controller = " + controller );

        // Get the folder the user selected to store this transfer in
        Folder folder;
        folder = getCurrentFolder();
        x.debug ( "MONA : folder 1 = " + folder );
        x.debug ( "MONA : folder label = " + folder.getLabel() );
        x.debug ( "MONA : folder uuid = " + folder.getUUID() );
        */

        // Get the folder the user selected to store this transfer in
        Folder folder;
        try
        {
            folder = new Folder
                ( oldTransferRecord.getEnclosingFolderId().longValue() );
            //x.debug ( "MONA : folder 2 = " + folder );
            log.debug ( "MONA : folder label = " + folder.getLabel() );
            //setCurrentFolder ( folder );
            //x.debug ( "MONA : folder uuid = " + folder.getUUID() );

            /*
            List<UserDataItem> dataitems = folder.findDataItems();
            x.debug ( "MONA : folder dataitems = " + dataitems );
            x.debug ( "MONA : dataitems size = " + dataitems.size() );
            for ( UserDataItem dataitem : dataitems )
            {
                x.debug ( "MONA : dataitem = " + dataitem );
                x.debug ( "MONA : dataitem label = " + dataitem.getLabel() );
            }
            */
        }
        catch ( Exception e )
        {
            log.error ( "System Error: cannot get folder with ID = " +
                oldTransferRecord.getEnclosingFolderId().longValue() );
            return ( saved );
        }

        // Check and handle files...
        String file = oldTransferRecord.getFileNames();
        if ( file != null && ! file.trim().equals ( "" ) )
        {
            String files[] = file.split ( "\\|" );
            //x.debug ( "MONA : files length = " + files.length );
            String path_file = "";

            if ( files != null && files.length > 0 )
            {
                //x.debug ( "MONA : files 0 = " + files[0] );
                UserDataItem dataItem;
                try
                {
                    for ( String filename : files )
                    {
                        path_file = destination_path + filename;
                        log.debug ( "MONA : about to save file = " +
                            filename );
                        dataItem = new UserDataItem ( destination_path +
                            filename, folder );
                        //x.debug ( "MONA : dataItem 1 = " + dataItem );
                        dataItem.setLabel ( filename );
                        dataItem = wbs.saveUserDataItem ( dataItem, folder );
                        saved++;
                        //x.debug ( "MONA : dataItem = " + dataItem );
                    }
                    folder.save();
                }

                catch ( Exception e )
                {
                    log.error
                        ( "System Error : cannot save Globus transferred file " +
                          path_file + "(" + e + ")" );
                }
                //refreshFolderDataTabs();
                //refreshFolders();
            }
        }

        // Check and handle directories...
        String dirString = oldTransferRecord.getDirectoryNames();
        if ( dirString != null && ! dirString.trim().equals ( "" ) )
        {
            String dirs[] = dirString.split ( "\\|" );
            log.debug ( "MONA : dirs length = " + dirs.length );
            if ( dirs != null && dirs.length > 0 )
            {
                log.debug ( "MONA : dirs 0 = " + dirs[0] );
                GlobusFolder folderItem;

                try
                {
                    for ( String dir : dirs )
                    {
                        log.debug ( "MONA : dir = " + dir );
                        folderItem = new GlobusFolder ( folder );
                            //newTransferRecord.getId(),
                            //destination_path + dir );
                        log.debug ( "MONA : folderItem = " + folderItem );
                        folderItem.save();
                        saved++;
                    }
                }

                catch ( Exception e )
                {
                    log.error
                        ( "System Error : cannot create Globus transferred directory ("
                        + e + ")" );
                }
            }
        }

        return ( saved );
    }
}
