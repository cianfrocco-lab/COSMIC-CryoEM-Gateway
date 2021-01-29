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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
     * Setup data items for the new Globus transferred files and directories.
     * IMPORTANT: the transferred is in the same folder as the user's data
     * folder.
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

        // Get the transferred directories and files
        String dirs[] = getList ( transfer_record.getDirectoryNames() );
        //log.debug ( "MONA : dirs = " + Arrays.toString ( dirs ) );
        String files[] = getList ( transfer_record.getFileNames() );
        ArrayList<String> new_files = null;
        if ( files != null && files.length > 0 )
        {
            //log.debug ( "MONA : files = " + Arrays.toString ( files ) );
            new_files = new ArrayList<String> ( Arrays.asList ( files ) );

            new_files = removeChildFiles ( new_files, dirs );
            //log.debug ( "MONA : new_files = " + new_files );
        }

        num_files_saved +=
            saveTopDirStarFiles ( new_destination_path, dirs, folder, tr_id );
        //log.debug ( "MONA: saved_labels = " + saved_labels.toString() );

        ArrayList<String> new_dirs = cleanDirs2 ( dirs );
        //log.debug ( "MONA : new_dirs = " + new_dirs );

        // Save directories first so that any files in the directory will not
        // be double saved...
        if ( new_dirs != null && new_dirs.size() > 0 )
            new_files = saveDirectories ( transfer_record.getTrId(),
                new_destination_path, folder, new_dirs, new_files );

        if ( new_files != null && new_files.size() > 0 )
        {
            //log.debug ( "MONA : new_files.size() = " + new_files.size() );
		    if ( new_files.size() == 1 )
		    {
                String file = new_files.get ( 0 );
			    String path_file = new_destination_path + file;
			    //log.debug ( "MONA : path_file = " + path_file );

			    try
			    {
				    UserDataItem dataItem = new UserDataItem ( path_file,
                        folder );
                    //log.debug ( "MONA: dataItem = " + dataItem );
				    dataItem.setLabel ( file );
				    dataItem.setEnclosingFolder ( folder );
				    dataItem.save();
			        //log.debug ( "MONA : saved dataItem = " + dataItem );
				    num_files_saved++;
                    //log.debug ( "MONA: num_files_saved = " + num_files_saved );
			    }
			    catch ( Exception e )
			    {
				    log.error (
					    "System Error : cannot save Globus transferred file " +
					    path_file + "(" + e + ")" );
                    failed_files_messages.add ( file + ": unable to save" );
			    }
		    } // if ( new_files.size() == 1 )

		    // Else multiple files, has to contain at least 1 *.star file
		    else
		    {
                String filename = null;
                Iterator<String> itr = new_files.iterator();
        	    String parts[];
                List<String> star_files = new ArrayList<String>();

                while( itr.hasNext() )
			    {
                    filename = itr.next();
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
                    saveFiles ( tr_id, new_destination_path, folder,
                        star_files );
                else
                {
                    boolean first = true;
                    String msg = "Error transferring ";

                    itr = new_files.iterator();

                    while ( itr.hasNext() )
			        {
                        filename = itr.next();
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

        return;
    }


    //***************** PRIVATE ***********************//

    private static final Log log = LogFactory.getLog
        ( Transfer2DataManager.class );

    private ArrayList<String> failed_directories_messages =
        new ArrayList<String>();
    private ArrayList<String> failed_files_messages = new ArrayList<String>();
    private int num_directories_saved = 0;
    private int num_files_saved = 0;
    private ArrayList<String> saved_labels = new ArrayList<String>();
    private boolean system_error = false;
    private ArrayList<String> user_system_error_messages =
        new ArrayList<String>();

	/**
	 * This function will remove all directories > 2 levels deep and put their
	 * 2nd parent into the list; eg if the directory is a/b/c, then a/b will
	 * be returned.  All second level directories will be returns (eg a/b).
	 * For a first level directory, if there is a second level child, then
	 * first level directory will be removed; otherwise, it will be returned
	 * (eg if a & a/b, a will be removed and a/b returned).  HashSet is used
     * because it doesn't allow duplicates but the data returned will be
     * ArrayList for ease of use with already working code.
     *
     * Using HashSet because it does not allow duplicate
	 **/
	private ArrayList<String> cleanDirs ( String[] dirs_in )
	{
		//log.debug ( "MONA: entered cleanDirs()" );
		if ( dirs_in == null || dirs_in.length < 1 )
			return ( null );

		int count = 0;
		String dir = null;
        String top = null;
		String[] dir_parts = null;
		HashSet<String> dir_top = new HashSet ( dirs_in.length );
		HashSet<String> dir_etc = new HashSet ( dirs_in.length );
        Iterator<String> itr;

		// First, go through incoming directories
		for ( int i = 0; i < dirs_in.length; i++ )
		{
			//log.debug ( "MONA: dirs_in @ " + i + " = " + dirs_in[i] );
			count = StringUtils.countMatches ( dirs_in[i], "/" );
			//log.debug ( "MONA: count = " + count );
			if ( count == 0 )
				dir_top.add ( dirs_in[i] );
			else if ( count == 1 )
				dir_etc.add ( dirs_in[i] );
			else
			{
    			dir_parts = dirs_in[i].split ( "/" );
				dir = dir_parts[0] + "/" + dir_parts[1];
				//log.debug ( "MONA: dir = " + dir );
				dir_etc.add ( dir );
			}
		}

		// Next, remove dir_top if parent of a dir_etc entry
        itr = dir_top.iterator();
        while ( itr.hasNext() )
		{
            top = itr.next();
			//log.debug ( "MONA: top = " + top );
			for ( String child : dir_etc )
			{
			    //log.debug ( "MONA: child = " + child );
                if ( child.startsWith ( top ) )
                {
                    //log.debug ( "MONA: removing " + top );
                    itr.remove();
                    break;
                }
			}
		}

		//HashSet[] dirs_out = { dir_top, dir_etc };
        ArrayList reply = new ArrayList ( dir_top );
        reply.addAll ( dir_etc );
		//log.debug ( "MONA: reply = " + reply );

		return ( reply );
	}

	/**
	 * This function will keep only the bottom-most directory.  Example:
     * if dir_in = a|a/b, then return only a/b.  Using HashSet because it
     * does not allow duplicate
	 **/
	private ArrayList<String> cleanDirs2 ( String[] dirs_in )
	{
		//log.debug ( "MONA: entered cleanDirs2()" );
        //log.debug ( "MONA: dirs_in = " + Arrays.toString ( dirs_in ) );
		if ( dirs_in == null || dirs_in.length < 1 )
			return ( null );

        // If only 1 directory, nothing to clean
        else if ( dirs_in.length == 1 )
            return ( new ArrayList ( Arrays.asList ( dirs_in ) ) );

        // More than 1 directories...
        String compare1, compare2;
        ArrayList<String> dirs_out = new ArrayList<String>( dirs_in.length );
        boolean keep = true;

        // Couldn't find a more efficient looping mechanism that will allow
        // comparing 2 directories and removing either one so using a basic
        // looping mechanism here
        for ( int i = 0; i < dirs_in.length; i++ )
        {
            compare1 = dirs_in[i];
            keep = true;

            for ( int j = i+1; j < dirs_in.length; j++ )
            {
                compare2 = dirs_in[j];

                if ( compare2.startsWith ( compare1 ) )
                {
                    //log.debug ( "MONA: ignoring " + compare1 );
                    keep = false;
                    break;
                }
            }

            if ( keep )
                dirs_out.add ( compare1 );
        }
        //log.debug ( "MONA: dirs_out = " + dirs_out );

        return ( dirs_out );
	}

    private String[] getList ( String s )
    {
    	if ( s == null || s.trim().equals ( "" ) )
    		return null;
    	else
    		return ( s.split ( "\\|" ) );
    }

	/**
	 * This function will get *.star files from directories that have only 1
     * level deep.
	 **/
	private ArrayList<String> getTopDirStarFiles ( String parent, String[] dirs )
	{
		//log.debug ( "MONA: entered getTopDirStarFiles" );
		if ( parent.isEmpty() || dirs == null || dirs.length < 1 )
			return ( null );

        ArrayList<String> files = null;
		String[] parts = null;

		// First, go through incoming directories
		for ( int i = 0; i < dirs.length; i++ )
		{
    		parts = dirs[i].split ( "/", 2 );
			//log.debug ( "MONA: " + i + " parts = " + Arrays.toString ( parts ) );

            // If it is a top-level directory, then look for *.star file
            if ( parts.length == 1 )
            {
                getStarFiles2 ( parent + parts[0] );
            }
		}
		//log.debug ( "MONA: files = " + files );

        return ( files );
	}

    /**
     * Save the given given directories. If a *.star file in a directory is
     * saved and also in the files parameter, that *.star file will be
     * removed from files[] to avoid saving it twice.
     * @return - ArrayList of (possibly changed) files
     **/
    private ArrayList<String> saveDirectories
        ( long tr_id, String destination_path, Folder folder,
        ArrayList directories, ArrayList<String> files_in )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveDirectories()" );
        //log.debug ( "MONA : destination_path = " + destination_path );
        //log.debug ( "MONA : directories = " + directories );

        ArrayList<String> files_out = null;
        if ( files_in != null && files_in.size() > 0 )
            files_out = new ArrayList ( files_in );
        //log.debug ( "MONA : files_out = " + files_out );

        if ( tr_id < 1 || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null ||
            directories == null || directories.size() < 1 )
            return ( files_out );

        UserDataDirItem data_item = null;
        File dirF = null;
		String dirS = null;
        String full_path = null;
		Iterator<String> itr = directories.iterator();
        int label_start_index = destination_path.length();
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        List<File> paths = new ArrayList<File>();
        int saved = 0;
        long size = 0L;
        String[] starfile_ext = { "star" };
        List<String> star_files = new ArrayList<String>();

        // Loop through all the transferred directories
		while ( itr.hasNext() )
        {
			dirS = itr.next();
            //log.debug ( "MONA : dirS = " + dirS );
            full_path = destination_path + dirS + "/";
            //log.debug ( "MONA : full_path = " + full_path );

            saved += traverseDirectory ( destination_path + dirS + "/", folder,
                tr_id, label_start_index, files_out );
            //log.debug ( "MONA : saved = " + saved );
        } // while( itr.hasNext() )

        return ( files_out );
    }

    /**
     * @return a list of *.star files for the given full_path
     **/
    private ArrayList<File> getStarFiles2 ( String full_path )
    {
        //log.debug ( "MONA: entered getStarFiles2()" );
        //log.debug ( "MONA: full_path = " + full_path );
        if ( full_path.isEmpty() )
            return ( null );

        File path = new File ( full_path );
        String[] starfile_ext = { "star" };
        //log.debug ( "MONA : starfile_ext = " + Arrays.toString ( starfile_ext ) );
        Collection < File > files =
            FileUtils.listFiles ( path, starfile_ext, false );
        //log.debug ( "MONA : files = " + files );

        return ( new ArrayList ( files ) );
    }

    private String[] getSubDirs ( String path )
    {
        //log.debug ( "MONA: entered getSubDirs()" );
        //log.debug ( "MONA: path = " + path );

        if ( path.isEmpty() )
            return ( null );

        File dir = new File ( path );
        String[] subdirs = dir.list ( new FilenameFilter()
            {
                @Override
                public boolean accept ( File current, String name )
                {
                    //log.debug ( "MONA: current = " + current );
                    //log.debug ( "MONA: name = " + name );
                    return new File ( current, name ).isDirectory();
                }
            } );
        //log.debug ( "MONA : subdirs = " + subdirs );
        return ( subdirs );
    }

    /**
     * Remove entries in files with directory that are in dirs
     **/
    private ArrayList<String> removeChildFiles
        ( ArrayList<String> files, String[] dirs )
	{
        //log.debug ( "MONA : entered removeChildFiles" );
        //log.debug ( "MONA: files = " + files );
        //log.debug ( "MONA: dirs = " + dirs );

		if ( files == null || files.size() < 1 )
			return ( null );
		else if ( dirs == null || dirs.length < 1 )
            return ( files );

		String dir, file;
		ArrayList<String> new_files = new ArrayList<String> ( files );

		for ( int i = 0; i < dirs.length; i++ )
		{
            dir = dirs[i];
			//log.debug ( "MONA : dir = " + dir );

			for ( int j = 0; j < new_files.size(); )
			{
				//log.debug ( "MONA : new_files size 1 = " + new_files.size() );
				//log.debug ( "MONA : j = " + j );
				file = new_files.get ( j );
				//log.debug ( "MONA : file = " + file );
				if ( file.startsWith ( dir ) )
					new_files.remove ( j );
				else
					j++;
			}
		}

		if ( new_files.size() > 0 )
			return ( new_files );
		else
			return ( null );
	}

	/**
	 * Examples of how the directories may be ordered:
	 * CtfFind|CtfFind/job003|CtfFind/job065|CtfFind/job087|CtfFind/job003/micrographs|CtfFind/job065/movies2|CtfFind/job087/movies2
	 * tmp_filtered/micrographs|tmp_filtered
	 *
	 **/
	private ArrayList<String> removeSubdirectories ( String[] dirs )
	{
        //log.debug ( "MONA : entered removeSubdirectories" );

		if ( dirs == null )
			return null;
		else if ( dirs.length < 2 )
			return ( new ArrayList<String> ( Arrays.asList ( dirs ) ) );

		// Ok, we have at least 2 directories...

		String d1, d2;
		ArrayList<String> new_dirs =
			new ArrayList<String> ( Arrays.asList ( dirs ) );
		//log.debug ( "MONA : new_dirs = " + new_dirs );

        for ( int i2 = 1, i1 = i2 - 1; i1 < new_dirs.size(); )
		{
			//log.debug ( "MONA : size 1 = " + new_dirs.size() );
			//log.debug ( "MONA : i1 = " + i1 + " " + new_dirs.get ( i1 ) );
			//log.debug ( "MONA : i2 = " + i2 + " " + new_dirs.get ( i2 ) );

			d1 = new_dirs.get ( i1 );
			d2 = new_dirs.get ( i2 );

			if ( d2.startsWith ( d1 ) )
				new_dirs.remove ( i2 );
			else if ( d1.startsWith ( d2 ) )
				new_dirs.remove ( i1 );
			else
				i2++;

			if ( i2 >= new_dirs.size() )
			{
				//log.debug ( "MONA : incrementing i1 & i2" );
				i1++;
				i2 = i1 + 1;
			}
		}
		//log.debug ( "MONA : final new_dirs = " + new_dirs );

		return ( new_dirs );
	}

    /*
     * Save the given TransferRecord's file(s)
     * @param tr_id - TransferRecord ID
     * @param destination_path - the user's toplevel data directory
     * @param folder - folder to save data in
     * @param files - list of star files to save
     * @return - number of files saved (>= 0 )
     */
    private void saveFiles ( Long tr_id, String destination_path,
        Folder folder, List<String> files )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveFiles()" );
        //log.debug ( "MONA : destination_path = " + destination_path );
        //log.debug ( "MONA : files = " + files );

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
                //log.debug ( "MONA : size = " + size );
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

    /**
     * This function will save the given star_files as a UserDataDirItem
     **/
    private int saveDirStarFiles ( ArrayList<File> star_files, String path,
        Folder folder, long tr_id, int label_start_index,
        ArrayList<String> files )
    {
        //log.debug ( "MONA : entered Transfer2DataManager.saveDirStarFiles()" );
        //log.debug ( "MONA : star_files = " + star_files );
        //log.debug ( "MONA : path = " + path );
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        //log.debug ( "MONA : files = " + files );

        if ( star_files.isEmpty() || label_start_index < 1 || path == null ||
            path.isEmpty() )
            return ( 0 );

        UserDataDirItem data_item = null;
        Iterator<File> itr = star_files.iterator();
        int saved = 0;
        long size;
        File star_file = null;

        while ( itr.hasNext() )
		{
            //log.debug ( "MONA : i = " + i );
            star_file = itr.next();
            //log.debug ( "MONA : star_file = " + star_file );
            //log.debug ( "MONA : star_file name = " + star_file.getName() );
            String label =
                star_file.toString().substring ( label_start_index );
            //log.debug ( "MONA : label = " + label );

            if ( files != null && files.contains ( label ) )
                files.remove ( label );

            // If the directory was also a top-level data directory, its
            // *.star file would have been saved already by saveTopDirStarFiles
            // so we won't save it again by checking the saved_labels global
            // variable
            if ( saved_labels.contains ( label ) )
                continue;

            try
            {
                size = FileUtils.sizeOf ( star_file );
                //log.debug ( "MONA : size = " + size );
                data_item = new UserDataDirItem ( folder, tr_id, label, size );
                //log.debug ( "MONA : saved data_item = " + data_item );
                if ( data_item != null )
                {
                    data_item.save();
                    saved++;
                    saved_labels.add ( label );
                }
            }
            catch ( Exception e )
            {
                log.error
                    ( "System Error : cannot create data item for Globus data item "
                    + star_file.getName() + " (" + e + ")" );
                failed_directories_messages.add ( "Error saving " +
                    label );
            }
        }

        return ( saved );
    }

    /**
     * Save Find the top-level data directory and save all *.star files found
     * in it
     **/
    private int saveTopDirStarFiles
        ( String user_folder_path, String[] dirs, Folder folder, Long tr_id )
    {
        //log.debug ( "MONA : Transfer2DataManager.saveTopDirStarFiles()" );
        //log.debug ( "MONA : user_folder_path = " + user_folder_path );
        //log.debug ( "MONA : dirs = " + Arrays.toString ( dirs ) );
        //log.debug ( "MONA : dirs.length = " + dirs.length );

        int saved = 0;

        if ( dirs == null || dirs.length < 1 || folder == null ||
            tr_id == null )
            return ( saved );

        ArrayList<String> top_dirs = new ArrayList<String> ( dirs.length );

        if ( dirs.length == 1 )
            top_dirs.add ( dirs[0] );
        else
        {
            String dir_a = dirs[0];
            String dir_b = null;

            for ( int i = 1; i < dirs.length; i++ )
            {
                //log.debug ( "MONA: i = " + i );
                dir_b = dirs[i];
                //log.debug ( "MONA : dir_a = " + dir_a );
                //log.debug ( "MONA : dir_b = " + dir_b );

                if ( dir_a.trim().isEmpty() || dir_a.equals ( dir_b ) ||
                    dir_a.startsWith ( dir_b ) )
                {
                    if ( i + 1 < dirs.length )
                        dir_a = dir_b;
                    else
                        top_dirs.add ( dir_b );
                }
                else if ( ! dir_b.startsWith ( dir_a ) )
                {
                    top_dirs.add ( dir_a );
                    if ( i + 1 < dirs.length )
                        dir_a = dir_b;
                    else
                        top_dirs.add ( dir_b );
                }
                else if ( dir_b.startsWith ( dir_a ) && ( i + 1 >= dirs.length ) )
                    top_dirs.add ( dir_a );
            }

        } // end else
        //log.debug ( "MONA : top_dirs = " + top_dirs );

        // Now loop through top_dirs to look for *.star files, save them and
        // remove any duplicate in files
        UserDataDirItem data_item = null;
        String dir = null;
        String full_path = null;
        String label = null;
        long size = 0L;
        File star_file = null;
        ArrayList<File> star_files = null;
        Iterator<File> star_files_itr = null;
        Iterator<String> top_dirs_itr = top_dirs.iterator();

        while ( top_dirs_itr.hasNext() )
        {
            dir = top_dirs_itr.next();
            //log.debug ( "MONA: dir = " + dir );
            full_path = user_folder_path + dir;
            //log.debug ( "MONA: full_path = " + full_path );
            star_files = getStarFiles2 ( full_path );
            //log.debug ( "MONA : star_files = " + star_files );

            if ( star_files != null && star_files.size() > 0 )
            {
                star_files_itr = star_files.iterator();
                while ( star_files_itr.hasNext() )
                {
                    star_file = star_files_itr.next();
                    //log.debug ( "MONA : star_file = " + star_file );
                    size = FileUtils.sizeOf ( star_file );
                    //log.debug ( "MONA : size = " + size );
                    label = dir + "/" + star_file.getName();
                    //log.debug ( "MONA: label = " + label );

                    data_item = new UserDataDirItem ( folder, tr_id, label,
                        size );

                    if ( data_item != null )
                    {
                        try
                        {
                            data_item.save();
                            saved++;
                            saved_labels.add ( label );
                        }
                        catch ( Exception e )
                        {
                            failed_files_messages.add ( label +
                                ": unable to save" );
                        }
                    }
                }
            }
        }
        return ( saved );
    }

    /**
     * This is a recursive function that ends when either a *.star file
     * is found or no sub-directory found
     **/
    private int traverseDirectory ( String start_path, Folder folder,
        long tr_id, int label_start_index, ArrayList<String> files )
    {
        //log.debug ( "MONA : entered Transfer2DataManager.traverseDirectory()" );
        //log.debug ( "MONA : start_path = " + start_path );
        //log.debug ( "MONA : label_start_index = " + label_start_index );
        //log.debug ( "MONA : files = " + files );

        if ( start_path == null || start_path.isEmpty() )
            return ( 0 );

        int saved = 0;
        ArrayList<File> star_files = getStarFiles2 ( start_path );
        //log.debug ( "MONA : star_files = " + star_files );

        // If there is/are *.star file/s, save it/them
        if ( star_files != null && star_files.size() > 0 )
            num_files_saved += saveDirStarFiles ( star_files, start_path,
                folder, tr_id, label_start_index, files );

        // Else no *.star file, recurse into subdirectory...
        else
        {
            String[] sub_dirs = getSubDirs ( start_path );
            //log.debug ( "MONA: sub_dirs = " + sub_dirs );
            //log.debug ( "MONA: sub_dirs.length = " + sub_dirs.length );

            if ( sub_dirs == null || sub_dirs.length < 1 )
                return ( 0 );
            else
            {
                for ( int i = 0; i < sub_dirs.length; i++ )
                    saved += traverseDirectory ( start_path + sub_dirs[i] + "/",
                        folder, tr_id, label_start_index, files );
            }
        }

        return ( saved );
    }
}
