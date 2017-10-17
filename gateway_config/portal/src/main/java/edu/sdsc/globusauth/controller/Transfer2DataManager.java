/**
 * This class is intended to act as controller/manager bridging the CIPRES
 * framework and Choonhan Youn's Globus package 
 *
 * @author Mona Wong
 */

package edu.sdsc.globusauth.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.actions.NgbwSupport;

import edu.sdsc.globusauth.model.TransferRecord;


public class Transfer2DataManager extends NgbwSupport
{
    private static final Log log = LogFactory.getLog
        ( Transfer2DataManager.class );

    /**
     * Setup CIPRES data items for the new Globus transferred files and
     * directories.
     * @return - number of data items created; null if invalid incoming
     *      parameters or newTransferRecord.getStatus() != "SUCCEEDED"
     **/
    public int setupDataItems ( TransferRecord transfer_record,
        String destination_path )
    {
        //log.debug ( "MONA : Transfer2DataManager.setupDataItems()" );
        //log.debug ( "MONA : transfer_record = " + transfer_record );
        //log.debug ( "MONA : destination_path = " + destination_path );

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) )
            return ( 0 );

        //log.debug ( "MONA : transfer_record.getFileNames() = " + transfer_record.getFileNames() );
        // Get the folder the user selected to store the transfer to
        Folder folder;
        try
        {
            folder = new Folder
                ( transfer_record.getEnclosingFolderId().longValue() );
            //log.debug ( "MONA : folder label = " + folder.getLabel() );
        }
        catch ( Exception e )
        {
            log.error ( "System Error: cannot get folder with ID = " +
                transfer_record.getEnclosingFolderId().longValue() );
            return ( 0 );
        }

        int saved = saveFiles ( transfer_record, destination_path, folder );
        saved += saveDirectories ( transfer_record, destination_path, folder );

        return ( saved );
    }


    /**
     * Save the given TransferRecord's directories
     * @return - number of files saved (>= 0 )
     **/
    private int saveDirectories ( TransferRecord transfer_record,
        String destination_path, Folder folder )
    {
        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null )
            return ( 0 );

        int saved = 0;

        // Check and handle directories...
        String dirString = transfer_record.getDirectoryNames();
        //log.debug ( "MONA : dirString = " + dirString );
        if ( dirString != null && ! dirString.trim().equals ( "" ) )
        {
            String dirs[] = dirString.split ( "\\|" );
            //log.debug ( "MONA : dirs length = " + dirs.length );
            if ( dirs != null && dirs.length > 0 )
            {
                //log.debug ( "MONA : dirs 0 = " + dirs[0] );
                UserDataDirItem folderItem = null;

                try
                {
                    for ( String dir : dirs )
                    {
                        folderItem = new UserDataDirItem ( folder,
                            transfer_record.getId(), destination_path + dir );
                        //log.debug ( "MONA : folderItem = " + folderItem );
                        folderItem.save();
                        saved++;
                    }
                }

                catch ( Exception e )
                {
                    reportUserError (
                        "Unable to setup Globus transferred data item (" +
                        e + ")" );
                    addActionError ( 
                        "Unable to setup Globus transferred data item (" +
                        e + ")" );
                    //reportError(error, "Error creating new TaskInputSourceDocument");
                    log.error
                        ( "System Error : cannot create data item for Globus transferred directory ("
                        + e + ")" );
                }
            }
        }

        return ( saved );
    }


    /*
     * Save the given TransferRecord's file(s)
     * @return - number of files saved (>= 0 )
     */
    private int saveFiles ( TransferRecord tr, String destination_path,
        Folder folder )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveFiles()" );
        if ( tr == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null )
            return ( 0 );

        int saved = 0;
        String file = tr.getFileNames();
        //log.debug ( "MONA : file = " + file );

        if ( file != null && ! file.trim().equals ( "" ) )
        {
            String files[] = file.split ( "\\|" );
            //log.debug ( "MONA : files = " + files );
            String path_file = "";

            if ( files != null && files.length > 0 )
            {
                UserDataItem dataItem = null;

                try
                {
                    for ( String filename : files )
                    {
                        path_file = destination_path + filename;
                        //log.debug ( "MONA : path_file = " + path_file );
                        dataItem = new UserDataItem ( destination_path +
                            filename, folder );
                        dataItem.setLabel ( filename );
                        dataItem.setEnclosingFolder ( folder );
                        dataItem.save();
                        saved++;
                    }
                }

                catch ( Exception e )
                {
                    log.error
                        ( "System Error : cannot save Globus transferred file " +
                          path_file + "(" + e + ")" );
                }
            }
        }

        return ( saved );
    }
}
