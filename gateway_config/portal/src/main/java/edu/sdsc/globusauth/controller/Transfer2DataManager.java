/**
 * This class is intended to act as controller/manager bridging the CIPRES
 * framework and Choonhan Youn's Globus package 
 *
 * @author Mona Wong
 */

package edu.sdsc.globusauth.controller;

import java.io.File;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
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
        log.debug ( "MONA : Transfer2DataManager.saveDirectories()" );
        log.debug ( "MONA : transfer_record = " + transfer_record );
        log.debug ( "MONA : transfer_record ID = " + transfer_record.getId() );
        log.debug ( "MONA : destination_path = " + destination_path );

        int saved = 0;

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null )
            return ( saved );

        Long tr_id = transfer_record.getId();

        // Check and handle directories...
        String dirString = transfer_record.getDirectoryNames();
        log.debug ( "MONA : dirString = " + dirString );
        if ( dirString == null || dirString.trim().equals ( "" ) )
            return ( saved );

        String transferred_dirs[] = dirString.split ( "\\|" );
        log.debug ( "MONA : transferred_dirs = " + transferred_dirs );
        log.debug ( "MONA : transferred_dirs length = " + transferred_dirs.length );

        if ( transferred_dirs == null || transferred_dirs.length <= 0 )
            return ( saved );

        int label_start_index = destination_path.length();
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        String[] starfile_ext = { "star" };
        UserDataDirItem data_item = null;

        // Loop through all the transferred directories
        for ( String transferred_dir : transferred_dirs )
        {
            // First, look for star file in the top-level transferred
            // directories...
            log.debug ( "MONA : transferred_dir = " + transferred_dir );
            String full_path = destination_path + transferred_dir + "/";
            log.debug ( "MONA : full_path = " + full_path );
            File dir = new File ( full_path );
            Collection < File > files = FileUtils.listFiles ( dir,
                starfile_ext, false );
            log.debug ( "MONA : files = " + files );

            // If found, just use the first one...
            if ( files != null && ! files.isEmpty() )
            {
                log.debug ( "MONA : files.size() = " + files.size() );
                //File file = ( File ) files.iterator().next();
                for ( File file : files )
                {
                    log.debug ( "MONA : file = " + file );
                    log.debug ( "MONA : file name = " + file.getName() );
                    log.debug ( "MONA : file.length() = " + file.length() );
                    log.debug ( "MONA : FileUtils.sizeOf = " + FileUtils.sizeOf ( file ) );
                    log.debug ( "MONA : FileUtils.sizeOfDirectory = " + FileUtils.sizeOfDirectory ( file ) );
                    String label = file.toString().substring
                        ( label_start_index );
                    log.debug ( "MONA : label = " + label );
                    try
                    {
                        /*
                        data_item = new UserDataDirItem ( folder, tr_id,
                            transferred_dir + "/" + file.getName(),
                            file.length() );
                        */
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            file.length() );
                        //log.debug ( "MONA : data_item = " + data_item );
                        if ( data_item != null )
                        {
                            data_item.save();
                            saved++;
                        }
                    }
                    catch ( Exception e )
                    {
                        String msg = 
                            "Unable to setup Globus transferred data item for file "
                            + file.getName() + " (" + e + ")";
                        reportUserError ( msg );
                        addActionError ( msg ); 
                        //reportError(error, "Error creating new TaskInputSourceDocument");
                        log.error
                            ( "System Error : cannot create data item for Globus data item "
                              + file.getName() + " (" + e + ")" );
                    }
                }
            }

            /*
            // Else look for star file in the subdirectories
            else
            {
                files = FileUtils.listFiles ( dir, starfile_ext, true );
                log.debug ( "MONA : files = " + files );

                // If no * /*.star found, then go back to next transferred
                // directory...
                if ( files == null || files.isEmpty() )
                    continue;
                log.debug ( "MONA : files.size() = " + files.size() );

                for ( File file : files )
                {
                    log.debug ( "MONA : file = " + file );
                    log.debug ( "MONA : file 1 = " + file.toString() );
                    /* debug
                    log.debug ( "MONA : file 2 = " + file.getPath() );
                    log.debug ( "MONA : file 3 = " + file.getParent() );
                    log.debug ( "MONA : file 4 = " + file.getName() );
                    try
                    {
                        log.debug ( "MONA : file 5 = " + file.getCanonicalPath() );
                    }
                    catch ( Exception e )
                    {
                    }
                    log.debug ( "MONA : file 6 = " + file.getAbsolutePath() );
                    * /
                    String label = file.toString().substring
                        ( label_start_index );
                    log.debug ( "MONA : label = " + label );
                    try
                    {
                        data_item = new UserDataDirItem ( folder, tr_id,
                            label, file.length() );
                        log.debug ( "MONA : data_item = " + data_item );
                        if ( data_item != null )
                        {
                            data_item.save();
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
            */
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
