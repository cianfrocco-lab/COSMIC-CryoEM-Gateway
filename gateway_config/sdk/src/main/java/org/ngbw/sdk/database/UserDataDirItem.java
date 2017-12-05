/*
 * This class is meant to encapsulate the userdata_dir database table
 */
package org.ngbw.sdk.database;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.core.shared.SourceDocumentType;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.WorkbenchException;


/**
 * This class encapsulates information for the Globus transferred directories.
 * Code was copied and modified from Folder.java.  Class is functional but
 * does contain additional member functions that are not use and have been
 * commented out. 
 *
 * @author Mona Wong
 *
 */
public class UserDataDirItem extends FolderItem
    implements SourceDocument, Comparable<UserDataDirItem>
{
    private static final Log log = LogFactory.getLog ( UserDataDirItem.class );

	// nested classes

	private class PreferenceMap extends MonitoredMap<String, String>
    {
		// constructors


		protected PreferenceMap(Map<String, String> prefMap)
		{
			super(prefMap);
            //log.debug ( "MONA : entered PreferenceMap()" );
            //log.debug ( "MONA : prefMap = " + prefMap );
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, String value)
		{
            //log.debug ( "MONA : entered addMapPutOp()" );
            //log.debug ( "MONA : key = " + key );
            //log.debug ( "MONA : value = " + value );
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			Column<String> prefValue = new StringColumn("VALUE", true, 100, value);
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(prefName);
			cols.add(prefValue);

			m_opQueue.add(new InsertOp("folder_preferences", cols));
		}

		@Override
		protected void addMapSetOp(String key, String oldValue, String newValue)
		{
            //log.debug ( "MONA : entered addMapSetOp()" );
            //log.debug ( "MONA : key = " + key );
            //log.debug ( "MONA : oldValue = " + oldValue );
            //log.debug ( "MONA : newValue = " + newValue );
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			Column<String> prefValue = new StringColumn("VALUE", true, 100, newValue);
			CompositeKey prefKey = new CompositeKey(m_key, prefName);

			m_opQueue.add(new UpdateOp("folder_preferences", prefKey, prefValue));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
            //log.debug ( "MONA : entered addMapRemoveOp()" );
            //log.debug ( "MONA : key = " + key );
			Column<String> prefName = new StringColumn("PREFERENCE", false, 100, key);
			CompositeKey prefKey = new CompositeKey(m_key, prefName);

			m_opQueue.add(new DeleteOp("folder_preferences", prefKey));
		}

		@Override
		protected void addMapClearOp()
		{
            //log.debug ( "MONA : entered addMapClearOp()" );
			m_opQueue.add(new DeleteOp("folder_preferences", getKey()));
		}
	}


	// data fields


    private static final String FILE_ROOT_PROPERTY =
        ConnectionSource.DATABASE_PROP_PREFIX + "globusRoot";
	private static final String KEY_NAME = "USERDATA_ID";
	private static final String TABLE_NAME = "userdata_dir";
    /*
	private final Column<String> m_comment = new StringColumn("COMMENT", true, 255);
	private final Column<Boolean> m_groupReadable = new BooleanColumn("GROUP_READABLE", false);
	private final Column<Boolean> m_worldReadable = new BooleanColumn("WORLD_READABLE", false);
    */
    private final Column<String> m_dataFormat = new StringColumn
        ( "DATAFORMAT", false, 255 );
    private final Column<String> m_dataType = new StringColumn ( "DATATYPE",
        false, 255 );
    private final Column<Long> m_size = new LongColumn ( "SIZE", false );
    /*
    private final Column<Long> m_sourceDocumentId = new LongColumn
        ( "SOURCE_DOCUMENT_ID", false );
    */
    private final Column<Long> m_transferRecordId = new LongColumn
        ( "TR_ID", false );
	private final Column<String> m_uuid = new StringColumn("UUID", false, 46);
    private Boolean m_validated = true;
	private PreferenceMap m_preferences;
    //private SourceDocumentRow m_sourceDocument = null;


	// constructors


	public UserDataDirItem(User owner)
	{
		this();
        //log.debug ( "MONA : entered UserDataDirItem() 1" );
        //log.debug ( "MONA : owner = " + owner );

		if (owner.isNew())
			throw new WorkbenchException("Can't create a folder for an unpersisted user");

		//setGroupReadable(false);
		//setWorldReadable(false);
		setUserId(owner.getUserId());
		//setGroupId(owner.getDefaultGroupId());
        setDataFormat ( DataFormat.STAR );
        setDataType ( DataType.DIRECTORY );
		setUUID(generateUUID());
		setCreationDate(Calendar.getInstance().getTime());

        //m_sourceDocument = new SourceDocumentRow();
	}

	public UserDataDirItem ( Folder enclosingFolder )
	{
		this();
        //log.debug ( "MONA : entered UserDataDirItem() 2" );
        //log.debug ( "MONA : enclosingFolder = " + enclosingFolder );

		if ( enclosingFolder.isNew() )
			throw new WorkbenchException (
                "Can't create a folder in an unpersisted folder");

		//setGroupReadable(false);
		//setWorldReadable(false);
		setUserId ( enclosingFolder.getUserId() );
		//setGroupId ( enclosingFolder.getGroupId() );
		setEnclosingFolderId ( enclosingFolder.getFolderId() );
        setDataFormat ( DataFormat.STAR );
        setDataType ( DataType.DIRECTORY );
		setUUID ( generateUUID() );
		setCreationDate ( Calendar.getInstance().getTime() );

        //m_sourceDocument = new SourceDocumentRow();
	}

	public UserDataDirItem ( long folderId ) throws IOException, SQLException
	{
		this();
        //log.debug ( "MONA : entered UserDataDirItem() 3" );
        //log.debug ( "MONA : folderId = " + folderId );

		m_key.assignValue ( folderId );

		load();
	}


    /*
     * @return null if any incoming argument is null
     */
	public UserDataDirItem ( Folder enclosingFolder, Long transferRecordId,
        String dir ) throws IOException
	{
		this();
        log.debug ( "MONA: entered UserDataDirItem() 4" );
        //log.debug ( "MONA : enclosingFolder = " + enclosingFolder );
        //log.debug ( "MONA : transferRecordId = " + transferRecordId );
        //log.debug ( "MONA : dir = " + dir );

        // Check incoming arguments
        if ( enclosingFolder == null || enclosingFolder.isNew() ||
            transferRecordId == null || dir == null ||
            dir.trim().equals ( "" ) )
			throw new WorkbenchException
                ( "Error creating Globus folder items!" );

        Hashtable starInfo = getStarFileInfo ( dir );
        //log.debug ( "MONA : starInfo = " + starInfo );

        if ( starInfo == null )
            throw new WorkbenchException ( "No .star file found!" );

		setUserId ( enclosingFolder.getUserId() );
		setGroupId ( enclosingFolder.getGroupId() );
		setEnclosingFolderId ( enclosingFolder.getFolderId() );
        setDataFormat ( DataFormat.STAR );
        setDataType ( DataType.DIRECTORY );
        //log.debug ( "MONA : m_dataType = " + m_dataType );
		setUUID ( generateUUID() );
		setCreationDate ( Calendar.getInstance().getTime() );
        //setLabel ( star_name );
        setLabel ( ( String ) starInfo.get ( "label" ) );
        setTransferRecordId ( transferRecordId ); 
        setSize ( ( Long ) starInfo.get ( "size" ) ); 

        /*
        Properties configProps = ConnectionSource.getDatabaseConfiguration();
        log.debug ( "MONA : configProps = " + configProps );
        String fileRoot = configProps.getProperty ( FILE_ROOT_PROPERTY );
        log.debug ( "MONA : fileRoot = " + fileRoot );
        m_sourceDocument = new SourceDocumentRow();
        log.debug ( "MONA : m_sourceDocument = " + m_sourceDocument );
        log.debug ( "MONA : file = " + dir + "/" + ( String ) starInfo.get ( "filename" ) );
        m_sourceDocument.setDataFromFile ( dir + "/" +
            ( String ) starInfo.get ( "filename" ) );
        */
	}


	UserDataDirItem(Connection dbConn, long folderId) throws IOException, SQLException
	{
		this();
        //log.debug ( "MONA : entered UserDataDirItem() 5" );
        //log.debug ( "MONA : folderId = " + folderId );

		m_key.assignValue(folderId);

		load(dbConn);
	}

    public UserDataDirItem ( UserDataDirItem otherItem,
        Folder enclosingFolder ) throws  IOException, SQLException
    {
        this ( enclosingFolder );
        //this((SourceDocument) otherItem, enclosingFolder);

        setLabel ( otherItem.getLabel() );
        setUUID ( generateUUID() );
                                                      
        /*
        m_metaData = new MetaDataMap ( new TreeMap<String, String>() );
        m_metaData.putAll ( otherItem.metaData() );
                                                                        
        m_dataRecords = new DataRecordList
            ( new ArrayList<UserItemDataRecord>() );

        for ( Iterator<UserItemDirDataRecord> records =
            otherItem.dataRecords().iterator() ; records.hasNext() ; )
            m_dataRecords.add ( new UserItemDataDirRecord ( records.next() ) );
        */
    }

	private UserDataDirItem()
	{
		super ( TABLE_NAME, KEY_NAME, 255 );
        //log.debug ( "MONA : entered UserDataDirItem() 6" );
        //log.debug ( "MONA : TABLE_NAME = " + TABLE_NAME );
        //log.debug ( "MONA : KEY_NAME = " + KEY_NAME );
        //log.debug ( "MONA : m_uuid = " + getUUID() );
        //log.debug ( "MONA : m_transferRecordId = " + getTransferRecordId() );

		construct ( m_dataType, m_dataFormat, m_uuid, m_transferRecordId,
            m_size );
            //m_size, m_sourceDocumentId );
	}


	// public methods


	public long getFolderId()
	{
        //log.debug ( "MONA : entered getFolderId()" );
		return m_key.getValue();
	}

    public long getUserDataId()
    {
        return m_key.getValue();
    }


    /*
	public String getComment()
	{
		return m_comment.getValue();
	}

	public void setComment(String comment)
	{
		m_comment.setValue(comment);
	}

	public boolean isGroupReadable()
	{
		return m_groupReadable.getValue();
	}

	public void setGroupReadable(Boolean groupReadable)
	{
		m_groupReadable.setValue(groupReadable);
	}

	public boolean isWorldReadable()
	{
		return m_worldReadable.getValue();
	}

	public void setWorldReadable(Boolean worldReadable)
	{
		m_worldReadable.setValue(worldReadable);
	}
    */

	@Override
	public String getUUID()
	{
		return m_uuid.getValue();
	}

	@Override
	public void setUUID(String uuid)
	{
		m_uuid.setValue(uuid);
	}


    public long getSize()
    {
        return ( m_size.getValue() );
    }


    public void setSize ( Long bytes )
    {
        m_size.setValue ( bytes );
    }


    public long getTransferRecordId()
    {
        return ( m_transferRecordId.getValue() );
    }


    public void setTransferRecordId ( Long tr_id )
    {
        m_transferRecordId.setValue ( tr_id );
    }


	public Map<String, String> preferences() throws IOException, SQLException
	{
        //log.debug ( "MONA : entered preferences()" );
		if (m_preferences == null) {
			Map<String, String> newPreferences = new TreeMap<String, String>();

			if (!isNew()) {
				Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
				PreparedStatement selectStmt = null;
				ResultSet prefRows = null;

				try {
					selectStmt = dbConn.prepareStatement("SELECT PREFERENCE, VALUE FROM folder_preferences WHERE USERDATA_ID = ?");

					m_key.setParameter(selectStmt, 1);

					prefRows = selectStmt.executeQuery();

					while (prefRows.next())
						newPreferences.put(prefRows.getString(1), prefRows.getString(2));
				}
				finally {
					if (prefRows != null)
						prefRows.close();

					if (selectStmt != null)
						selectStmt.close();

					dbConn.close();
				}
			}

			m_preferences = new PreferenceMap(newPreferences);
		}

		return m_preferences;
	}

    /*
	public List<Task> findTasks() throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findTasks()" );
		if (isNew())
			return null;

		return Task.findTasks(new LongCriterion("ENCLOSING_FOLDER_ID", m_key.getValue()));
	}

	public Task findTask(String label) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findTask()" );
        //log.debug ( "MONA : label = " + label );
		if (isNew())
			return null;

		List<Task> result = Task.findTasks(new LongCriterion("ENCLOSING_FOLDER_ID", m_key.getValue()), new StringCriterion("LABEL", label));

		if (result.isEmpty())
			return null;

		return result.get(0);
	}
    */

	public UserDataDirItem findDataDirItem(String label) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findDataDirItem()" );
        //log.debug ( "MONA : label = " + label );
		if (isNew())
			return null;

		List<UserDataDirItem> result = UserDataDirItem.findDataDirItems(new LongCriterion("ENCLOSING_FOLDER_ID", m_key.getValue()));

		if (result.isEmpty())
			return null;

		return result.get(0);
	}

	public List<UserDataDirItem> findDataDirItems() throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findDataDirItems()" );
		if (isNew())
			return null;

		return UserDataDirItem.findDataDirItems(new LongCriterion("ENCLOSING_FOLDER_ID", m_key.getValue()));
	}


    /*
     *  Returns list of matching directory items.
     *  Multiple criteria are AND'd together (all must be satisfied).
     *  Returns empty list if nothing matches.
     */
    static List<UserDataDirItem> findDataDirItems ( Criterion... keys )
        throws IOException, SQLException
    {
        //log.debug ( "MONA : entered findDataDirItems() 2" );
        //log.debug ( "MONA : keys = " + keys );
        StringBuilder stmtBuilder = new StringBuilder ( "SELECT " + KEY_NAME +
            "  FROM " + TABLE_NAME + " WHERE " );
        //log.debug ( "MONA : stmtBuilder 1 = " + stmtBuilder.toString() );
                                        
        stmtBuilder.append ( keys[0].getPhrase() );
        //log.debug ( "MONA : stmtBuilder 1 = " + stmtBuilder.toString() );
                                                     
        for ( int i = 1 ; i < keys.length ; i += 1 )
        {
            stmtBuilder.append ( " AND " );
            stmtBuilder.append ( keys[i].getPhrase() );
        }
                                                          
        Connection dbConn =
            ConnectionManager.getConnectionSource().getConnection();
        PreparedStatement selectStmt = null;
        ResultSet itemRows = null;

        try {
            selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
            int index = 1;
                                                                                           for (int i = 0 ; i < keys.length ; i += 1)
                index = keys[i].setParameter(selectStmt, index);
                                                                                           itemRows = selectStmt.executeQuery();
            List<UserDataDirItem> dataItems = new ArrayList<UserDataDirItem>();

            while (itemRows.next())
            {
                //log.debug ( "MONA : itemRows = " + itemRows );
                dataItems.add ( new UserDataDirItem ( dbConn,
                    itemRows.getLong ( 1 ) ) );
            }
                                                                                           return dataItems;
        }   
                                                                                       finally
        {
            if (itemRows != null)
                itemRows.close();

            if (selectStmt != null)
                selectStmt.close();

            dbConn.close();
        }
    }       


    /*
	public List<UserDataDirItem> findSubFolders() throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findSubFolders()" );
		if (isNew())
			return null;

		return findFolders(new LongCriterion("ENCLOSING_FOLDER_ID", m_key.getValue()));
	}

	public UserDataDirItem findSubFolder(String path) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findSubFolder()" );
        //log.debug ( "MONA : path = " + path );
		if (path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be relative");

		return findFolder(m_key.getValue(), path, null);
	}

	public UserDataDirItem findOrCreateSubFolder(String path) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findOrCreateSubFolder()" );
        //log.debug ( "MONA : path = " + path );
		if (path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be relative");

		return findFolder(m_key.getValue(), path, new User(getUserId()));
	}

	public static UserDataDirItem findFolder(String path) throws IOException, SQLException
	{
		if (!path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be absolute");

		return findFolder(null, path.substring(1), null);
	}

	public static UserDataDirItem findOrCreateFolder(User owner, String path) throws IOException, SQLException
	{
		if (!path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be absolute");

		return findFolder(null, path.substring(1), owner);
	}

	public static UserDataDirItem findFolderByUUID(String uuid) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findFolderByUUID()" );
        //log.debug ( "MONA : uuid = " + uuid );
		List<UserDataDirItem> folders = findFolders(new StringCriterion("UUID", uuid));

		if (folders.isEmpty())
			return null;

		return folders.get(0);
	}
    */

	@Override
	public boolean equals(Object other)
	{
        //log.debug ( "MONA : entered equals()" );
        //log.debug ( "MONA : other = " + other );
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof UserDataDirItem == false)
			return false;

		UserDataDirItem otherFolder = (UserDataDirItem) other;

		if (isNew() || otherFolder.isNew())
			return false;

		return getFolderId() == otherFolder.getFolderId();
	}

	@Override
	public int hashCode()
	{
        //log.debug ( "MONA : entered hashCode()" );
		return (new Long(getFolderId())).hashCode();
	}

	@Override
	public int compareTo(UserDataDirItem other)
	{
        //log.debug ( "MONA : entered compareTo()" );
        //log.debug ( "MONA : other = " + other );
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getFolderId() - other.getFolderId());
	}


	// package methods


    @Override
    void save(Connection dbConn) throws IOException, SQLException
    {
        //log.debug ( "MONA : entered UserDataDirItem.save()" );
        //m_sourceDocument.save(dbConn);
                            
        //m_sourceDocumentId.setValue(m_sourceDocument.getSourceDocumentId());
                                     
        super.save(dbConn);
    }


	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered load()" );
		super.load(dbConn);

        /*
        m_sourceDocument = new SourceDocumentRow ( dbConn,
            m_sourceDocumentId.getValue() );
        */

		m_preferences = null;
	}

	@Override
	void delete ( Connection dbConn ) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered delete()" );
		if ( isNew() )
			throw new WorkbenchException ( "Not persisted" );

		delete(dbConn, m_key.getValue());
        //delete ( dbConn, getKey(), m_sourceDocumentId.getValue() );

		m_key.reset();
	}

    /*
	static List<UserDataDirItem> findFolders(Criterion... keys) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findFolders()" );
        //log.debug ( "MONA : keys = " + keys );
		StringBuilder stmtBuilder = new StringBuilder("SELECT USERDATA_ID FROM " + TABLE_NAME + " WHERE ");

		stmtBuilder.append(keys[0].getPhrase());

		for (int i = 1 ; i < keys.length ; i += 1) {
			stmtBuilder.append(" AND ");
			stmtBuilder.append(keys[i].getPhrase());
		}

		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet folderRows = null;

		try {
			selectStmt = dbConn.prepareStatement(stmtBuilder.toString());

			int index = 1;

			for (int i = 0 ; i < keys.length ; i += 1)
				index = keys[i].setParameter(selectStmt, index);

			folderRows = selectStmt.executeQuery();

			List<UserDataDirItem> folders = new ArrayList<UserDataDirItem>();

			while (folderRows.next())
				folders.add(new UserDataDirItem(dbConn, folderRows.getLong(1)));

			return folders;
		}
		finally {
			if (folderRows != null)
				folderRows.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}
	}
    */

	static void delete(Connection dbConn, long folderId) throws
        IOException, SQLException
	{
        //log.debug ( "MONA : entered delete()" );
        //log.debug ( "MONA : folderId = " + folderId );
		Criterion folderKey = new LongCriterion(KEY_NAME, folderId);

		(new DeleteOp("folder_preferences", folderKey)).execute(dbConn);

        /*
		deleteData(dbConn, folderId);
		deleteTasks(dbConn, folderId);
		deleteSubFolders(dbConn, folderId);
        */

		(new DeleteOp(TABLE_NAME, folderKey)).execute(dbConn);
	}


	// private methods

    private static void delete ( Connection dbConn, Criterion userDataKey,
        long sourceDocumentId ) throws IOException, SQLException
    {
        ( new DeleteOp ( "item_metadata", userDataKey ) ).execute ( dbConn );
                           
        //deleteDataRecords ( dbConn, userDataKey );
                                    
        ( new DeleteOp ( TABLE_NAME, userDataKey ) ).execute ( dbConn );
                                             
        SourceDocumentRow.delete ( dbConn, sourceDocumentId );
    }


    /*
	private static UserDataDirItem findFolder ( Long enclosingFolderId,
        String path, User owner ) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered findFolder()" );
        //log.debug ( "MONA : enclosingFolderId = " + enclosingFolderId );
        //log.debug ( "MONA : path = " + path );
        //log.debug ( "MONA : owner = " + owner );
		int index;
		String[] folderNames = path.split("(?<!\\\\)" + SEPARATOR);

		for (index = 0 ; index < folderNames.length ; index += 1)
			folderNames[index] = folderNames[index].replaceAll("\\\\(.)", "$1");

		index = 0;

		UserDataDirItem result;

		while (true) {
			List<UserDataDirItem> folders = findFolders(new LongCriterion("ENCLOSING_FOLDER_ID", enclosingFolderId), new StringCriterion("LABEL", folderNames[index]));

			if (folders.isEmpty()) {
				if (owner != null) {
					while (true) {
						result = new UserDataDirItem(owner);

						result.setEnclosingFolderId(enclosingFolderId);
						result.setLabel(folderNames[index]);
						result.save();

						index += 1;

						if (index == folderNames.length)
							break;

						enclosingFolderId = result.getFolderId();
					}
				}
				else
					result = null;

				break;
			}

			result = folders.get(0);
			index += 1;

			if (index == folderNames.length)
				break;

			enclosingFolderId = result.getFolderId();
		}

		return result;
	}

	private static void deleteData(Connection dbConn, long folderId)
        throws IOException, SQLException
	{
        //log.debug ( "MONA : entered deleteData()" );
        //log.debug ( "MONA : folderId = " + folderId );
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT USERDATA_ID FROM userdata WHERE ENCLOSING_FOLDER_ID = ?");
		ResultSet dataRows = null;

		try {
			selectStmt.setLong(1, folderId);

			dataRows = selectStmt.executeQuery();

			while (dataRows.next())
				UserDataDirItem.delete(dbConn, dataRows.getLong(1));
		}
		finally {
			if (dataRows != null)
				dataRows.close();

			selectStmt.close();
		}
	}

	private static void deleteTasks(Connection dbConn, long folderId)
        throws IOException, SQLException
	{
        //log.debug ( "MONA : entered deleteTasks()" );
        //log.debug ( "MONA : folderId = " + folderId );
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT TASK_ID FROM tasks WHERE ENCLOSING_FOLDER_ID = ?");
		ResultSet taskRows = null;

		try {
			selectStmt.setLong(1, folderId);

			taskRows = selectStmt.executeQuery();

			while (taskRows.next())
				Task.delete(dbConn, taskRows.getLong(1));
		}
		finally {
			if (taskRows != null)
				taskRows.close();

			selectStmt.close();
		}
	}

	private static void deleteSubFolders(Connection dbConn, long folderId) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered deleteSubFolders()" );
        //log.debug ( "MONA : folderId = " + folderId );
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT USERDATA_ID FROM folders WHERE ENCLOSING_FOLDER_ID = ?");
		ResultSet folderRows = null;

		try {
			selectStmt.setLong(1, folderId);

			folderRows = selectStmt.executeQuery();

			while (folderRows.next())
				Folder.delete(dbConn, folderRows.getLong(1));
		}
		finally {
			if (folderRows != null)
				folderRows.close();

			selectStmt.close();
		}
	}
    */


    /**
     * Get the size and name (parent directory + filename) of the first
     * *.star file found in the given directory
     **/
    private Hashtable getStarFileInfo ( String dir )
    {
        //log.debug ( "MONA : entered getStarFileInfo" );
        //log.debug ( "MONA : dir = " + dir );
        if ( dir == null || dir.trim().equals ( "" ) )
            return ( null );

        String dirname = FilenameUtils.getName ( dir );
        //log.debug ( "MONA : dirname = " + dirname );
        File dir_handle = new File ( dir );
        File[] files = dir_handle.listFiles();
        Hashtable reply = new Hashtable ( 2 );

        //log.debug ( "MONA : files length = " + files.length );

        for ( File file : files )
        {    
            if ( FilenameUtils.getExtension ( file.getName() ).equals
                ( "star" ) )
            {
                //log.debug ( "MONA : found!" );
                //return ( dirname + "/" + file.getName() );
                reply.put ( "filename", file.getName() );
                reply.put ( "label", dirname + "/" + file.getName() );
                reply.put ( "size", file.length() );
                return ( reply );
            }
        }
        return ( null );
    }

    /**** Implementation for SourceDocument ****/
    /* This class doesn't actually use a SourceDocument but it was necessary
     * to implement SourceDocument because it is needed for job submission
     * related classes and functions
     */

    @Override
    public byte[] getData() throws IOException, SQLException
    {
        log.debug ( "MONA : entered UserDataDirItem.getData()" );
        //return m_sourceDocument.getData();
        return null;
    }

    @Override
    public InputStream getDataAsStream() throws IOException, SQLException
    {
        log.debug ( "MONA : entered UserDataDirItem.getDataAsStream()" );
        //return m_sourceDocument.getDataAsStream();
        return null;
    }
     
    @Override
    public long getDataLength() throws IOException, SQLException
    {
        //return m_sourceDocument.getDataLength();
        return ( m_size.getValue() );
    }

    //@Override
    public DataFormat getDataFormat()
    {
        //log.debug ( "MONA : entered UserDataDirItem.getDataFormat()" );
        return DataFormat.valueOf ( m_dataFormat.getValue() );
    }
     
    public void setDataFormat ( DataFormat dataFormat )
    {
        m_dataFormat.setValue ( dataFormat.toString() );
    }
          
    //@Override
    public DataType getDataType()
    {
        //log.debug ( "MONA : entered UserDataDirItem.getDataType()" );
        //log.debug ( "MONA : m_dataType = " + m_dataType.getValue() );
        return DataType.valueOf ( m_dataType.getValue() );
    }

    public void setDataType ( DataType dataType )
    {
        m_dataType.setValue ( dataType.toString() );
    }

    //@Override
    public EntityType getEntityType()
    {
        //return EntityType.valueOf(m_entityType.getValue());
        return EntityType.UNKNOWN;
    }

    //@Override
    public String getName()
    {
        return getLabel();
    }

    @Override
    public long getSourceDocumentId()
    {
        //return m_sourceDocumentId.getValue();
        return m_key.getValue();
    }

    @Override
    public SourceDocumentType getType()
    {
        return new SourceDocumentType ( getEntityType(), getDataType(),
            getDataFormat());
    }

    //@Override
    public boolean isValidated()
    {
        return m_validated.booleanValue();
    }
          
    /*
    public void setValidated(Boolean validated)
    {
        m_validated.setValue(validated);
    }
    */
               
    //@Override
    public void setValidated()
    {
        //setValidated(true);
        m_validated = true;
    }
                    

}