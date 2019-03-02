/**
 * This class is intended to act as controller/manager bridging the CIPRES
 * framework and Choonhan Youn's Globus package 
 *
 * @author Mona Wong
 */

package edu.sdsc.globusauth.controller;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.actions.NgbwSupport;

import org.ngbw.sdk.database.TransferRecord;


public class Transfer2DataManager extends NgbwSupport
{
    private static final Log log = LogFactory.getLog
        ( Transfer2DataManager.class );

    /**
     * Setup CIPRES data items for the new Globus transferred files and
     * directories. IMPORTANT: the transferred is in the same folder as
     * the user's data folder.
     * @return - number of data items created; null if invalid incoming
     *      parameters or newTransferRecord.getStatus() != "SUCCEEDED"
     **/
    public int setupDataItems ( TransferRecord transfer_record,
        String destination_path )
    {
        //log.debug ( "MONA : Transfer2DataManager.setupDataItems()" );
        //log.debug ( "MONA : transfer_record = " + transfer_record );
        //log.debug ( "MONA : transfer_record id = " + transfer_record.getTrId() );
        //log.debug ( "MONA : destination_path = " + destination_path );

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) )
            return ( 0 );

        //log.debug ( "MONA : transfer_record.getFileNames() = " + transfer_record.getFileNames() );
        // Get the folder the user selected to store the transfer to
        Folder folder;
        try
        {
            folder = new Folder ( transfer_record.getEnclosingFolderId() );
            //log.debug ( "MONA : folder label = " + folder.getLabel() );
            //log.debug ( "MONA : folder userid = " + folder.getUserId() );
            //log.debug ( "MONA : folder creation date = " + folder.getCreationDate() );
        }
        catch ( Exception e )
        {
            log.error ( "System Error: cannot get folder with ID = " +
                transfer_record.getEnclosingFolderId() );
            return ( 0 );
        }

        int saved = saveFiles ( transfer_record, destination_path, folder );
        saved += saveDirectories ( transfer_record, destination_path, folder );
        reportUserError ( saved + " files/directories were saved" );

        return ( saved );
    }


    /**
     * Save the given TransferRecord's directories
     * @return - number of files saved (>= 0 )
     **/
    private int saveDirectories ( TransferRecord transfer_record,
        String destination_path, Folder folder )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveDirectories()" );
        //log.debug ( "MONA : transfer_record = " + transfer_record );
        //log.debug ( "MONA : transfer_record ID = " + transfer_record.getTrId() );
        //log.debug ( "MONA : transfer_record status = " + transfer_record.getStatus() );
        //log.debug ( "MONA : destination_path = " + destination_path );

        int saved = 0;

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null )
            return ( saved );

        Long tr_id = transfer_record.getTrId();
        String user_data_folder = folder.getLabel();
        //log.debug ( "MONA : user_data_folder = " + user_data_folder );
        destination_path += user_data_folder + "/";
        //log.debug ( "MONA : new destination_path = " + destination_path );

        // Check and handle directories...
        String dirString = transfer_record.getDirectoryNames();
        //log.debug ( "MONA : dirString = " + dirString );
        if ( dirString == null || dirString.trim().equals ( "" ) )
            return ( saved );

        String transferred_dirs[] = dirString.split ( "\\|" );
        //log.debug ( "MONA : transferred_dirs = " + transferred_dirs );
        //log.debug ( "MONA : transferred_dirs length = " + transferred_dirs.length );

        if ( transferred_dirs == null || transferred_dirs.length <= 0 )
            return ( saved );

        int label_start_index = destination_path.length();
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        String[] starfile_ext = { "star" };
        UserDataDirItem data_item = null;

        // Loop through all the transferred directories
        for ( String transferred_dir : transferred_dirs )
        {
            // First, look for *.star file in the top-level transferred
            // directories...
            //log.debug ( "MONA : transferred_dir = " + transferred_dir );
            String full_path = destination_path + transferred_dir + "/";
            //log.debug ( "MONA : full_path = " + full_path );
            File dir = new File ( full_path );
            //log.debug ( "MONA : dir = " + dir );

            // If the directory is not readable, skip it...
            Path tmp = dir.toPath();
            if ( ! Files.isReadable ( tmp ) )
            {
                reportUserError ( "Error: cannot read " + full_path );
                addActionError ( "Error: cannot read " + full_path );
                continue;
            }

            //log.debug ( "MONA : starfile_ext = " + starfile_ext );
            Collection < File > files = FileUtils.listFiles ( dir,
                starfile_ext, false );
            //log.debug ( "MONA : files = " + files );

            if ( files != null && ! files.isEmpty() )
            {
                //log.debug ( "MONA : files.size() = " + files.size() );
                for ( File file : files )
                {
                    //log.debug ( "MONA : file = " + file );
                    //log.debug ( "MONA : file name = " + file.getName() );
                    String label = file.toString().substring
                        ( label_start_index );
                    //log.debug ( "MONA : label = " + label );
                    try
                    {
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            FileUtils.sizeOf ( dir ) );
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

            // Now, look for particles.star file only one directory down...
            //files = FileUtils.listFiles ( dir, starfile_ext, true );
            String[] subdirs = dir.list ( new FilenameFilter()
                {
                    @Override
                    public boolean accept ( File current, String name )
                    {
                        return new File ( current, name ).isDirectory();
                    }
                });
            //log.debug ( "MONA : subdirs = " + subdirs );

            for ( String subdir : subdirs )
            {
                //log.debug ( "MONA : subdir = " + subdir );
                File file = new File ( full_path + "/" + subdir +
                    "/particles.star" );
                //log.debug ( "MONA : file = " + file );
                if ( file.exists() )
                {
                    log.debug ( "file exists!" );
                    dir = new File ( full_path + "/" + subdir );
                    //log.debug ( "MONA : dir = " + dir );

                    String label = file.toString().substring
                        ( label_start_index );
                    //log.debug ( "MONA : label = " + label );
                    try
                    {
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            FileUtils.sizeOf ( dir ) );
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
        }

        //log.debug ( "MONA : saved = " + saved );
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
