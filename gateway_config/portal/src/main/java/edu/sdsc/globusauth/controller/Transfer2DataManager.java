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
        log.debug ( "MONA : Transfer2DataManager.setupDataItems()" );
        //log.debug ( "MONA : transfer_record = " + transfer_record );
        log.debug ( "MONA : transfer_record status = " + transfer_record.getStatus() );
        log.debug ( "MONA : destination_path = " + destination_path );

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
            log.debug ( "MONA : folder label = " + folder.getLabel() );
            log.debug ( "MONA : folder userid = " + folder.getUserId() );
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
        log.debug ( "MONA : new destination_path = " + new_destination_path );
        
        String dirs[] = getList ( transfer_record.getDirectoryNames() );
        log.debug ( "MONA : dirs = " + Arrays.toString ( dirs ) );
        ArrayList new_dirs = cleanDirs ( dirs );
        log.debug ( "MONA : new_dirs = " + new_dirs );

        // Save directories first so that any files in the directory will not
        // be double saved...
        if ( new_dirs != null && new_dirs.size() > 0 )
            saveDirectories ( transfer_record.getTrId(), new_destination_path,
                folder, new_dirs );

        String files[] = getList ( transfer_record.getFileNames() );
        //log.debug ( "MONA : files = " + files );
        ArrayList<String> new_files = removeChildFiles ( files, new_dirs );
        log.debug ( "MONA : new_files = " + new_files );
                  
        if ( new_files != null && new_files.size() > 0 )
        {
            //log.debug ( "MONA : files.length = " + files.length );
		    if ( new_files.size() == 1 )
		    {
                String file = new_files.get ( 0 );
			    String path_file = new_destination_path + file;
			    log.debug ( "MONA : path_file = " + path_file );

			    try
			    {
				    UserDataItem dataItem = new UserDataItem ( path_file,
                        folder );
				    dataItem.setLabel ( file );
				    dataItem.setEnclosingFolder ( folder );
				    dataItem.save();
			        //log.debug ( "MONA : saved dataItem = " + dataItem );
				    num_files_saved++;
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
				    log.debug ( "MONA: filename = " + filename );
    			    parts = filename.split ( "\\." );
				    //log.debug ( "MONA: parts = " + parts );
				    //log.debug ( "MONA: parts.length = " + parts.length );
			
				    if ( parts[parts.length-1].equals ( "star" ) )
                        star_files.add ( filename );
			    }
			    log.debug ( "MONA: star_files = " + star_files );
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

        /*
        if ( directories != null && directories.length > 0 )
            saveDirectories2 ( transfer_record, new_destination_path, folder,
                directories );
        */

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
	private ArrayList cleanDirs ( String[] dirs_in )
	{
		log.debug ( "MONA: entered cleanDirs()" );
		if ( dirs_in == null || dirs_in.length < 1 )
			return ( null );

		int count = 0;
		String dir = null;
        String top = null;
		String[] dir_parts = null;
		HashSet<String> dir1 = new HashSet ( dirs_in.length );
		HashSet<String> dir2 = new HashSet ( dirs_in.length );
        Iterator<String> itr;

		// First, go through incoming directories
		for ( int i = 0; i < dirs_in.length; i++ )
		{
			//log.debug ( "MONA: dirs_in @ " + i + " = " + dirs_in[i] );
			count = StringUtils.countMatches ( dirs_in[i], "/" );
			//log.debug ( "MONA: count = " + count );
			if ( count == 0 )
				dir1.add ( dirs_in[i] );
			else if ( count == 1 )
				dir2.add ( dirs_in[i] );
			else
			{
    			dir_parts = dirs_in[i].split ( "/" );
				dir = dir_parts[0] + "/" + dir_parts[1];
				//log.debug ( "MONA: dir = " + dir );
				dir2.add ( dir );
			}
		}
		log.debug ( "MONA: dir1 1 = " + dir1 );
		log.debug ( "MONA: dir2 1 = " + dir2 );

		// Next, remove dir1 if parent of a dir2 entry
        itr = dir1.iterator();
		//for ( String top : dir1 )
        while ( itr.hasNext() )
		{
            top = itr.next();
			//log.debug ( "MONA: top = " + top );
			for ( String child : dir2 )
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
		log.debug ( "MONA: dir1 2 = " + dir1 );
		log.debug ( "MONA: dir2 2 = " + dir2 );

		//HashSet[] dirs_out = { dir1, dir2 };
        ArrayList reply = new ArrayList ( dir1 );
        reply.addAll ( dir2 );
		log.debug ( "MONA: reply = " + reply );

		return ( reply );
	}

    private String[] getList ( String s )
    {
    	if ( s == null || s.trim().equals ( "" ) )
    		return null;
    	else
    		return ( s.split ( "\\|" ) );
    }

    private String getTopDir ( String dir )
    {
        //log.debug ( "MONA : entered getTopDir" );
    	if ( dir == null || dir.trim().equals ( "" ) )
    		return null;
    	else
		{
			int index = dir.indexOf ( "/" );
        	//log.debug ( "MONA : index = " + index );
			if ( index == -1 )
				return ( null );
			else
			{
        		//log.debug ( "MONA : returning " + dir.substring ( 0, index ) );
				return ( dir.substring ( 0, index ) );
			}
		}
    }
    
	//private ArrayList<String> getUniqueTopDirectories ( String[] dirs )
	private HashSet getUniqueTopDirectories ( String[] dirs )
	{
        //log.debug ( "MONA : entered getUniqueTopDirectories" );

		if ( dirs == null )
			return null;
		else if ( dirs.length < 2 )
			return ( new HashSet ( Arrays.asList ( dirs ) ) );
			//return ( new ArrayList<String> ( Arrays.asList ( dirs ) ) );

		// Ok, we have at least 2 directories...

		HashSet new_dirs = new HashSet();
		String top = null;
		
		for ( String dir : dirs )
		{
			top = getTopDir ( dir );
			if ( top != null && top.length() > 0 )
				new_dirs.add ( top );
		}
		//log.debug ( "MONA : final new_dirs = " + new_dirs );

		return ( ( new_dirs.size() > 0 ) ? new_dirs : null );
	}

    /**
     * Save the given given directories. 
     * @return - number of files saved (>= 0 )
     **/
    private void saveDirectories ( long tr_id, String destination_path,
        Folder folder, ArrayList directories )
    {
        log.debug ( "MONA : Transfer2DataManager.saveDirectories()" );
        log.debug ( "MONA : destination_path = " + destination_path );
        log.debug ( "MONA : directories = " + directories );

        if ( tr_id < 1 || destination_path == null ||
            destination_path.trim().equals ( "" ) || folder == null ||
            directories == null || directories.size() < 1 )
            return;

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

        // Loop through all the transferred directories
		while( itr.hasNext() )
        {
            // First, look for *.star file in the top-level transferred
            // directories...
			dirS = itr.next();
            //log.debug ( "MONA : dirS = " + dirS );
            full_path = destination_path + dirS + "/";
            log.debug ( "MONA : full_path = " + full_path );
            dirF = new File ( full_path );
            //log.debug ( "MONA : dirF = " + dirF );

            Path tmp = dirF.toPath();
            if ( Files.isReadable ( tmp ) )
                paths.add ( dirF );
            else
            {
                failed_directories_messages.add ( "Error reading directory " +
                    dirS );
                continue;
            }

            //log.debug ( "MONA : starfile_ext = " + starfile_ext );
            Collection < File > files = FileUtils.listFiles ( dirF,
                starfile_ext, false );
            log.debug ( "MONA : files = " + files );

            if ( files != null && ! files.isEmpty() )
            {
                for ( File file : files )
                {
                    //log.debug ( "MONA : file = " + file );
                    log.debug ( "MONA : file name = " + file.getName() );
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
                            ( "Error transferring " + dirS + "/" +
                            file.getName() );
                    }
                } // for ( File file : files )
            } // if ( files != null && ! files.isEmpty() )

            // Now, look for particles.star file only one directory down...
            String[] subdirs = dirF.list ( new FilenameFilter()
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
                log.debug ( "MONA : subdir = " + subdir );
                File file = new File ( full_path + "/" + subdir +
                    "/particles.star" );
                log.debug ( "MONA : file = " + file );
                if ( file.exists() )
                {
                    log.debug ( "particles.star exists!" );
                    dirF = new File ( full_path + "/" + subdir );
                    //log.debug ( "MONA : dirF = " + dirF );

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

        log.debug ( "MONA : saved = " + saved );
        // If nothing saved, then delete the directories uploaded!
        if ( saved == 0 )
        {
            boolean first = true;
            String bad_paths = null;
            //log.debug ( "MONA : paths = " + paths );
            for ( File path : paths )
            {
                log.debug ( "MONA: deleting path " + path );
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
     * @param tr_id - TransferRecord ID
     * @param destination_path - the user's toplevel data directory
     * @param folder - folder to save data in
     * @param files - list of star files to save
     * @return - number of files saved (>= 0 )
     */
    private void saveFiles ( Long tr_id, String destination_path,
        Folder folder, List<String> files )
    {
        log.debug ( "MONA : Transfer2DataManager.saveFiles()" );
        log.debug ( "MONA : destination_path = " + destination_path );
        log.debug ( "MONA : files = " + files );

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
    
	private ArrayList<String> removeChildFiles
		( String[] files, ArrayList dirs )
	{
        log.debug ( "MONA : entered removeChildFiles" );
		if ( files == null )
			return ( null );
		else if ( dirs == null )
			return ( new ArrayList<String> ( Arrays.asList ( files ) ) );

		String dir, file;
		Iterator<String> dir_itr = dirs.iterator();
		ArrayList<String> new_files =
			new ArrayList<String> ( Arrays.asList ( files ) );

		while( dir_itr.hasNext() )
		{
			dir = dir_itr.next();
			//log.debug ( "MONA : dir = " + dir );
			for ( int i = 0; i < new_files.size(); )
			{
				//log.debug ( "MONA : new_files size 1 = " + new_files.size() );
				//log.debug ( "MONA : i = " + i );
				file = new_files.get ( i );
				//log.debug ( "MONA : file = " + file );
				if ( file.startsWith ( dir ) )
				{
					log.debug ( "MONA : removing " + file );
					new_files.remove ( i );
					//log.debug ( "MONA : new_files size 2 = " + new_files.size() );
				}
				else
					i++;
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
        log.debug ( "MONA : entered removeSubdirectories" );

		if ( dirs == null )
			return null;
		else if ( dirs.length < 2 )
			return ( new ArrayList<String> ( Arrays.asList ( dirs ) ) );

		// Ok, we have at least 2 directories...

		String d1, d2;
		ArrayList<String> new_dirs =
			new ArrayList<String> ( Arrays.asList ( dirs ) );
		log.debug ( "MONA : new_dirs = " + new_dirs );

        for ( int i2 = 1, i1 = i2 - 1; i1 < new_dirs.size(); )
		{
			//log.debug ( "MONA : size 1 = " + new_dirs.size() );
			//log.debug ( "MONA : i1 = " + i1 + " " + new_dirs.get ( i1 ) );
			//log.debug ( "MONA : i2 = " + i2 + " " + new_dirs.get ( i2 ) );

			d1 = new_dirs.get ( i1 );
			d2 = new_dirs.get ( i2 );

			if ( d2.startsWith ( d1 ) )
			{
				//log.debug ( "MONA : removing i2 : " + d2 );
				new_dirs.remove ( i2 );
			} 
			else if ( d1.startsWith ( d2 ) )
			{
				//log.debug ( "MONA : removing i1 = " + d1 );
				new_dirs.remove ( i1 );
			}
			else
				i2++;
			//log.debug ( "MONA : size 2 = " + new_dirs.size() );

			if ( i2 >= new_dirs.size() )
			{
				//log.debug ( "MONA : incrementing i1 & i2" );
				i1++;
				i2 = i1 + 1;
			}
		}
		log.debug ( "MONA : final new_dirs = " + new_dirs );

		return ( new_dirs );
	}
}
