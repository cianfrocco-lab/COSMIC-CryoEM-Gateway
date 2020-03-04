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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataDirItem;
import org.ngbw.sdk.database.UserDataItem;

import org.ngbw.sdk.database.TransferRecord;


/**
 * This class encapsulates the handling of a Globus transfer and contains
 * information to aid in the display of information back to the user in the
 * calling action class.
 * System errors are logged but will not be stored in this object.
 **/
public class Transfer2DataManager
{
    private static final Log log = LogFactory.getLog
        ( Transfer2DataManager.class );

    private ArrayList<String> failed_directories_messages =
        new ArrayList<String>();
    private ArrayList<String> failed_files_messages = new ArrayList<String>();
    private int num_directories_saved = 0;
    private int num_files_saved = 0;
    private boolean system_error = false;
    private ArrayList<String> user_system_error_messages =
        new ArrayList<String>();

    public void addUserSystemErrorMessage ( String msg )
    {
        if ( msg != null && ! (msg.trim()).isEmpty() )
            user_system_error_messages.add ( msg );
    }
    public ArrayList<String> getFailedDirectoriesMessages()
        { return ( failed_directories_messages ); }
    public ArrayList<String> getFailedFilesMessages()
        { return ( failed_files_messages ); }
    public int getNumFilesSaved() { return ( num_files_saved ); }
    public int getNumDirectoriesSaved() { return ( num_directories_saved ); }
    public ArrayList<String> getUserSystemErrorMessages()
        { return ( user_system_error_messages ); }
    public boolean hasSystemError() { return ( system_error ); }
    public void setUserSystemErrorMessages ( ArrayList val )
        { user_system_error_messages = val; }
    public void setSystemError ( boolean val ) { system_error = val; }


    /**
     * Setup CIPRES data items for the new Globus transferred files and
     * directories. IMPORTANT: the transferred is in the same folder as
     * the user's data folder.
     * @return - number of data items created; 0 if invalid incoming
     *      parameters or newTransferRecord.getStatus() != "SUCCEEDED";
     *      -1 if the transfer failed
     **/
    public void setupDataItems
        ( TransferRecord transfer_record, String destination_path )
    {
        //log.debug ( "MONA : Transfer2DataManager.setupDataItems()" );
        //log.debug ( "MONA : transfer_record = " + transfer_record );
        //log.debug ( "MONA : transfer_record status = " + transfer_record.getStatus() );
        //log.debug ( "MONA : destination_path = " + destination_path );

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) )
            return;

        //log.debug ( "MONA : transfer_record.getFileNames() = " + transfer_record.getFileNames() );
        // Get the folder the user selected to store the transfer to
        Folder folder;
        Long tr_id = transfer_record.getTrId();
        //log.debug ( "MONA : tr_id = " + tr_id );

        try
        {
            folder = new Folder ( transfer_record.getEnclosingFolderId() );
            //log.debug ( "MONA : folder label = " + folder.getLabel() );
            //log.debug ( "MONA : folder userid = " + folder.getUserId() );
            //log.debug ( "MONA : folder creation date = " + folder.getCreationDate() );
            User user = new User ( transfer_record.getUserId() );
            //log.debug ( "MONA : user = " + user );
            //log.debug ( "MONA : user.getDataSize() = " + user.getDataSize() );
            //log.debug ( "MONA : user.queryDataSize() = " + user.queryDataSize() );
        }
        catch ( Exception e )
        {
            log.error ( "System Error: cannot get transfer folder with ID = " +
                transfer_record.getEnclosingFolderId() );
            system_error = true;
            return;
        }

        // Append user folder label to destination_path
        String new_destination_path = destination_path + folder.getLabel() +
            "/";
        //log.debug ( "MONA : new destination_path = " + new_destination_path );
        
        String directories[] = getList ( transfer_record.getDirectoryNames() );
        //log.debug ( "MONA : directories = " + directories );
        String files[] = getList ( transfer_record.getFileNames() );
        //log.debug ( "MONA : files = " + files );
        
        if ( files != null )
        {
            //log.debug ( "MONA : files.length = " + files.length );
		    if ( files.length == 1 )
		    {
			    String path_file = new_destination_path + files[0];
			    //log.debug ( "MONA : path_file = " + path_file );

			    try
			    {
				    UserDataItem dataItem = new UserDataItem ( path_file,
                        folder );
				    dataItem.setLabel ( files[0] );
				    dataItem.setEnclosingFolder ( folder );
				    dataItem.save();
				    num_files_saved++;
			    }
			    catch ( Exception e )
			    {
				    log.error (
					    "System Error : cannot save Globus transferred file " +
					    path_file + "(" + e + ")" );
                    failed_files_messages.add
                        ( files[0] + ": unable to save" );
			    }
		    } // if ( files.length == 1 )

		    // Else multiple files, has to contain at least 1 *.star file
		    else
		    {
        	    String parts[];
                List<String> star_files = new ArrayList<String>();

        	    for ( String filename : files )
			    {
				    //log.debug ( "MONA: filename = " + filename );
    			    parts = filename.split ( "\\." );
				    //log.debug ( "MONA: parts = " + parts );
				    //log.debug ( "MONA: parts.length = " + parts.length );
			
				    if ( parts[parts.length-1].equals ( "star" ) )
                        star_files.add ( filename );
			    }
			    //log.debug ( "MONA: star_files = " + star_files );
			    //log.debug ( "MONA: star_files.size() = " + star_files.size() );

                if ( star_files.size() > 0 )
                    saveFiles2 ( tr_id, new_destination_path, folder,
                        star_files );
                else
                {
                    String msg = "Error transferring ";
                    boolean first = true;

        	        for ( String filename : files )
			        {
                        File file =
                            new File ( new_destination_path + filename );
		                file.delete();

                        if ( first )
                        {
                            msg += filename;
                            first = false;
                        }
                        else
                            msg += ", " + filename;
			        }
                    failed_files_messages.add
                        ( msg + ": missing required *.star file." );
                }
		    } // else
        } // if ( files != null )

        if ( directories != null && directories.length > 0 )
            saveDirectories2 ( transfer_record, new_destination_path, folder,
                directories );
		/*
        int saved = saveFiles ( transfer_record, new_destination_path,
            folder );
        saved += saveDirectories ( transfer_record, new_destination_path,
            folder );
        reportUserError ( saved + " files/directories were saved" );

		*/

        return;
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
        long size = 0L;

        if ( transfer_record == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null )
            return ( saved );

        Long tr_id = transfer_record.getTrId();
        /*
        String user_data_folder = folder.getLabel();
        //log.debug ( "MONA : user_data_folder = " + user_data_folder );
        destination_path += user_data_folder + "/";
        log.debug ( "MONA : new destination_path = " + destination_path );
        */

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
                //reportUserError ( "Error: cannot read " + full_path );
                //addActionError ( "Error: cannot read " + full_path );
                continue;
            }

            //log.debug ( "MONA : starfile_ext = " + starfile_ext );
            Collection < File > files = FileUtils.listFiles ( dir,
                starfile_ext, false );
            //log.debug ( "MONA : files = " + files );

            if ( files != null && ! files.isEmpty() )
            {
                size = files.size();
                //log.debug ( "MONA : size 1 = " + size );
                for ( File file : files )
                {
                    //log.debug ( "MONA : file = " + file );
                    //log.debug ( "MONA : file name = " + file.getName() );
                    String label = file.toString().substring
                        ( label_start_index );
                    //log.debug ( "MONA : label = " + label );
                    try
                    {
                        size = FileUtils.sizeOf ( dir );
                        //log.debug ( "MONA : size 2 = " + size );
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            size );
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
                        //reportUserError ( msg );
                        //addActionError ( msg ); 
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
                        size = FileUtils.sizeOf ( dir );
                        //log.debug ( "MONA : size 3 = " + size );
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            size );
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
                        //reportUserError ( msg );
                        //addActionError ( msg ); 
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


    /**
     * Save the given TransferRecord's directories
     * @return - number of files saved (>= 0 )
     **/
    private void saveDirectories2 ( TransferRecord tr, String destination_path,
        Folder folder, String[] directories )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveDirectories2()" );
        //log.debug ( "MONA : destination_path = " + destination_path );

        if ( tr == null || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null ||
            directories == null || directories.length <= 0 )
            return;

        UserDataDirItem data_item = null;
        File dir = null;
        String full_path = null;
        int label_start_index = destination_path.length();
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        List<File> paths = new ArrayList<File>();
        int saved = 0;
        long size = 0L;
        String[] starfile_ext = { "star" };
        long tr_id = tr.getTrId();

        // Loop through all the transferred directories
        for ( String directory : directories )
        {
            // First, look for *.star file in the top-level transferred
            // directories...
            //log.debug ( "MONA : directory = " + directory );
            full_path = destination_path + directory + "/";
            //log.debug ( "MONA : full_path = " + full_path );
            dir = new File ( full_path );
            //log.debug ( "MONA : dir = " + dir );

            Path tmp = dir.toPath();
            if ( Files.isReadable ( tmp ) )
                paths.add ( dir );
            else
            {
                failed_directories_messages.add ( "Error reading directory " +
                    directory );
                continue;
            }

            //log.debug ( "MONA : starfile_ext = " + starfile_ext );
            Collection < File > files = FileUtils.listFiles ( dir,
                starfile_ext, false );
            //log.debug ( "MONA : files = " + files );

            if ( files != null && ! files.isEmpty() )
            {
                for ( File file : files )
                {
                    //log.debug ( "MONA : file = " + file );
                    //log.debug ( "MONA : file name = " + file.getName() );
                    String label = file.toString().substring
                        ( label_start_index );
                    //log.debug ( "MONA : label = " + label );
                    try
                    {
                        size = FileUtils.sizeOf ( file );
                        //log.debug ( "MONA : size 2 = " + size );
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            size );
                        //log.debug ( "MONA : data_item = " + data_item );
                        if ( data_item != null )
                        {
                            data_item.save();
                            saved++;
                        }
                    }
                    catch ( Exception e )
                    {
                        log.error
                            ( "System Error : cannot create data item for Globus data item "
                              + file.getName() + " (" + e + ")" );
                        failed_directories_messages.add
                            ( "Error transferring " + directory + "/" +
                            file.getName() );
                    }
                } // for ( File file : files )
            } // if ( files != null && ! files.isEmpty() )

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
                    log.debug ( "particles.star exists!" );
                    dir = new File ( full_path + "/" + subdir );
                    //log.debug ( "MONA : dir = " + dir );

                    String label = file.toString().substring
                        ( label_start_index );
                    //log.debug ( "MONA : label = " + label );
                    try
                    {
                        size = FileUtils.sizeOf ( file );
                        //log.debug ( "MONA : size 3 = " + size );
                        data_item = new UserDataDirItem ( folder, tr_id, label,
                            size );
                        //log.debug ( "MONA : data_item = " + data_item );
                        if ( data_item != null )
                        {
                            data_item.save();
                            saved++;
                        }
                    }
                    catch ( Exception e )
                    {
                        log.error
                            ( "System Error : cannot create data item for Globus data item "
                              + file.getName() + " (" + e + ")" );
                        failed_directories_messages.add
                            ( "Error transferring " + subdir + "/" +
                            file.getName() );
                    }
                } // if ( file.exists() )
            } // for ( String subdir : subdirs )
        } // for ( String directory : directories )

        //log.debug ( "MONA : saved = " + saved );
        // If nothing saved, then delete the directories uploaded!
        if ( saved == 0 )
        {
            boolean first = true;
            String bad_paths = null;
            //log.debug ( "MONA : paths = " + paths );
            for ( File path : paths )
            {
                //log.debug ( "MONA: deleting path " + path );
                try
                {
                    FileUtils.deleteDirectory ( path );

                    if ( first )
                    {
                        bad_paths = path.getName() + "/";
                        first = false;
                    }
                    else
                        bad_paths += ", " + path + "/";
                }
                catch ( Exception e )
                {
                    log.error ( "System error: cannot delete directory " +
                        path.getName() + " (" + e + ")" );
                }
            }
            failed_directories_messages.add ( "Error transferring directory " +
                bad_paths + ": missing required *.star file." );
        }
        else if ( saved > 0 )
            num_directories_saved += saved;

        return;
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
    
    
    /*
     * Save the given TransferRecord's file(s)
     * @param tr_id - TransferRecord ID
     * @param destination_path - the user's toplevel data directory
     * @param folder - folder to save data in
     * @param files - list of star files to save
     * @return - number of files saved (>= 0 )
     */
    private void saveFiles2 ( Long tr_id, String destination_path,
        Folder folder, List<String> files )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveFiles2()" );
        //log.debug ( "MONA : destination_path = " + destination_path );

        if ( tr_id == null || tr_id.longValue() <= 0L ||
            destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null ||
            files == null || files.size() == 0 )
            return;

        UserDataDirItem data_item = null;
        File file = null;;
        boolean first = true;
        String error_msg = "Error transferring ";
        long size = 0L;

        for ( String filename : files )
        {
            file = new File ( destination_path + filename );
            //log.debug ( "MONA; file = " + file );
            try
            {
                size = FileUtils.sizeOf ( file );
                ////log.debug ( "MONA : size = " + size );
                data_item = new UserDataDirItem ( folder, tr_id, filename,
                    size );
                //log.debug ( "MONA : data_item = " + data_item );
                if ( data_item != null )
                {
                    data_item.save();
				    num_files_saved++;
                }
            }
            catch ( Exception e )
            {
                log.error
                    ( "System Error : cannot create data item for Globus data item "
                    + file.getName() + " (" + e + ")" );

                if ( first )
                    error_msg += filename;
                else
                    error_msg += ", " + filename;
            }
        } // for ( String filename : files )

        if ( ! first )
            failed_files_messages.add ( error_msg );

        return;
    }
    
    private String[] getList ( String s )
    {
    	if ( s != null && ! s.trim().equals ( "" ) )
    		return ( s.split ( "\\|" ) );
    	else
    		return null;
    }
    
}
