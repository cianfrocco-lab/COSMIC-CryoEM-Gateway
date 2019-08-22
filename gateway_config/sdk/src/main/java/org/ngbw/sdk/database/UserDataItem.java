/*
 * UserDataItem.java
 */
package org.ngbw.sdk.database;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FilenameUtils;

import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.common.util.IOUtils;
import org.ngbw.sdk.core.shared.SourceDocumentType;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;


/**
 *
 * @author Paul Hoover
 *
 */
public class UserDataItem extends FolderItem implements SourceDocument, Comparable<UserDataItem> {

	// nested classes


	/**
	 *
	 */
	private class MetaDataMap extends MonitoredMap<String, String> {

		// constructors


		protected MetaDataMap(Map<String, String> prefMap)
		{
			super(prefMap);
		}


		// protected methods


		@Override
		protected void addMapPutOp(String key, String value)
		{
			Column<String> fieldName = new StringColumn("FIELD", false, 100, key);
			Column<String> fieldValue = new StringColumn("VALUE", true, 255, value);
			List<Column<?>> cols = new ArrayList<Column<?>>();

			cols.add(m_key);
			cols.add(fieldName);
			cols.add(fieldValue);

			m_opQueue.add(new InsertOp("item_metadata", cols));
		}

		@Override
		protected void addMapSetOp(String key, String oldValue, String newValue)
		{
			Column<String> fieldName = new StringColumn("FIELD", false, 100, key);
			Column<String> fieldValue = new StringColumn("VALUE", true, 255, newValue);
			CompositeKey fieldKey = new CompositeKey(m_key, fieldName);

			m_opQueue.add(new UpdateOp("item_metadata", fieldKey, fieldValue));
		}

		@Override
		protected void addMapRemoveOp(String key)
		{
			Column<String> fieldName = new StringColumn("FIELD", false, 100, key);
			CompositeKey fieldKey = new CompositeKey(m_key, fieldName);

			m_opQueue.add(new DeleteOp("item_metadata", fieldKey));
		}

		@Override
		protected void addMapClearOp()
		{
			m_opQueue.add(new DeleteOp("item_metadata", getKey()));
		}
	}

	/**
	 *
	 */
	private class AddDataRecordOp implements RowOperation {

		// data fields


		private final UserItemDataRecord m_record;


		// constructors


		protected AddDataRecordOp(UserItemDataRecord record)
		{
			if (record.isNew())
				m_record = record;
			else
				m_record = new UserItemDataRecord(record);
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			m_record.setUserDataId(getUserDataId());

			m_record.save(dbConn);
		}
	}

	/**
	 *
	 */
	private class RemoveDataRecordOp implements RowOperation {

		// data fields


		private final UserItemDataRecord m_record;


		// constructors


		protected RemoveDataRecordOp(UserItemDataRecord record)
		{
			m_record = record;
		}


		// public methods


		@Override
		public void execute(Connection dbConn) throws IOException, SQLException
		{
			m_record.delete(dbConn);
		}
	}

	/**
	 *
	 */
	private class DataRecordList extends MonitoredList<UserItemDataRecord> {

		// constructors


		protected DataRecordList(List<UserItemDataRecord> records)
		{
			super(records);
		}


		// protected methods


		@Override
		protected void addListAddOp(UserItemDataRecord record)
		{
			m_opQueue.add(new AddDataRecordOp(record));
		}

		@Override
		protected void addListSetOp(UserItemDataRecord oldRecord, UserItemDataRecord newRecord)
		{
			m_opQueue.add(new RemoveDataRecordOp(oldRecord));
			m_opQueue.add(new AddDataRecordOp(newRecord));
		}

		@Override
		protected void addListRemoveOp(UserItemDataRecord record)
		{
			m_opQueue.add(new RemoveDataRecordOp(record));
		}

		@Override
		protected MonitoredList<UserItemDataRecord> newListInstance(List<UserItemDataRecord> list)
		{
			return new DataRecordList(list);
		}
	}


	// data fields


	private static final String TABLE_NAME = "userdata";
	private static final String KEY_NAME = "USERDATA_ID";
	private final Column<String> m_comment = new StringColumn("COMMENT", true, 255);
	private final Column<String> m_dataFormat = new StringColumn("DATAFORMAT", false, 255);
	private final Column<String> m_dataType = new StringColumn("DATATYPE", false, 255);
	private final Column<String> m_entityType = new StringColumn("ENTITYTYPE", false, 255);
	private final Column<Boolean> m_validated = new BooleanColumn("VALIDATED", false);
	private final Column<String> m_UUID = new StringColumn("UUID", false, 46);
	private final Column<Long> m_sourceDocumentId = new LongColumn("SOURCE_DOCUMENT_ID", false);
	private SourceDocumentRow m_sourceDocument;
	private MetaDataMap m_metaData;
	private DataRecordList m_dataRecords;


	// constructors


	public UserDataItem(Folder enclosingFolder)
	{
		this();

		if (enclosingFolder.isNew())
			throw new WorkbenchException("Can't create a data item in an unpersisted folder");

		setDataFormat(DataFormat.UNKNOWN);
		setDataType(DataType.UNKNOWN);
		setEntityType(EntityType.UNKNOWN);
		setValidated(false);
		setUserId(enclosingFolder.getUserId());
		setGroupId(enclosingFolder.getGroupId());
		setEnclosingFolderId(enclosingFolder.getFolderId());
		setUUID(generateUUID());
		setCreationDate(Calendar.getInstance().getTime());

		m_sourceDocument = new SourceDocumentRow();
	}

	public UserDataItem(SourceDocument document, Folder enclosingFolder) throws IOException, SQLException
	{
		this();

		if (enclosingFolder.isNew())
			throw new WorkbenchException("Can't create a data item in an unpersisted folder");

		setDataFormat(document.getDataFormat());
		setDataType(document.getDataType());
		setEntityType(document.getEntityType());
		setValidated(document.isValidated());
		setUserId(enclosingFolder.getUserId());
		setGroupId(enclosingFolder.getGroupId());
		setEnclosingFolderId(enclosingFolder.getFolderId());
		setUUID(generateUUID());
		setCreationDate(Calendar.getInstance().getTime());

		m_sourceDocument = new SourceDocumentRow(document);
	}

	public UserDataItem(UserDataItem otherItem) throws IOException, SQLException
	{
		this(otherItem, otherItem.getEnclosingFolder());
	}

	public UserDataItem(UserDataItem otherItem, Folder enclosingFolder) throws IOException, SQLException
	{
		this((SourceDocument) otherItem, enclosingFolder);

		setComment(otherItem.getComment());
		setLabel(otherItem.getLabel());
		setUUID(generateUUID());

		m_metaData = new MetaDataMap(new TreeMap<String, String>());

		m_metaData.putAll(otherItem.metaData());

		m_dataRecords = new DataRecordList(new ArrayList<UserItemDataRecord>());

		for (Iterator<UserItemDataRecord> records = otherItem.dataRecords().iterator() ; records.hasNext() ; )
			m_dataRecords.add(new UserItemDataRecord(records.next()));
	}

	public UserDataItem(long userDataId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(userDataId);

		load();
	}

	public UserDataItem(String fileName, Folder enclosingFolder) throws IOException
	{
		this(enclosingFolder);

		setUUID(generateUUID());

		m_sourceDocument.setDataFromFile(fileName);

        String ext = FilenameUtils.getExtension ( fileName );
        switch ( ext ) {
            case "mrc" :
                setDataFormat ( DataFormat.MRC );
                break;
        }
	}

	UserDataItem(Connection dbConn, long userDataId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(userDataId);

		load(dbConn);
	}

	private UserDataItem()
	{
		super(TABLE_NAME, KEY_NAME, 1023);

		construct(m_comment, m_dataFormat, m_dataType, m_entityType, m_validated, m_UUID, m_sourceDocumentId);
	}


	// public methods


	public long getUserDataId()
	{
		return m_key.getValue();
	}

	@Override
	public String getUUID()
	{
		return m_UUID.getValue();
	}

	@Override
	public void setUUID(String UUID)
	{
		m_UUID.setValue(UUID);
	}

	public String getComment()
	{
		return m_comment.getValue();
	}

	public void setComment(String comment)
	{
		m_comment.setValue(comment);
	}

	@Override
	public DataFormat getDataFormat()
	{
		return DataFormat.valueOf(m_dataFormat.getValue());
	}

	public void setDataFormat(DataFormat dataFormat)
	{
		m_dataFormat.setValue(dataFormat.toString());
	}

	@Override
	public DataType getDataType()
	{
		return DataType.valueOf(m_dataType.getValue());
	}

	public void setDataType(DataType dataType)
	{
		m_dataType.setValue(dataType.toString());
	}

	@Override
	public EntityType getEntityType()
	{
		return EntityType.valueOf(m_entityType.getValue());
	}

	public void setEntityType(EntityType entityType)
	{
		m_entityType.setValue(entityType.toString());
	}

	@Override
	public SourceDocumentType getType()
	{
		return new SourceDocumentType(getEntityType(), getDataType(), getDataFormat());
	}

	@Override
	public boolean isValidated()
	{
		return m_validated.getValue();
	}

	public void setValidated(Boolean validated)
	{
		m_validated.setValue(validated);
	}

	@Override
	public void setValidated()
	{
		setValidated(true);
	}

	@Override
	public String getName()
	{
		return getLabel();
	}

	@Override
	public byte[] getData() throws IOException, SQLException
	{
		return m_sourceDocument.getData();
	}

	@Override
	public InputStream getDataAsStream() throws IOException, SQLException
	{
		return m_sourceDocument.getDataAsStream();
	}

	@Override
	public long getDataLength() throws IOException, SQLException
	{
		return m_sourceDocument.getDataLength();
	}

	public void setData(byte[] data)
	{
		m_sourceDocument.setData(data);
	}

	public void setData(String data)
	{
		setData(data.getBytes());
	}

	public void setData(Serializable data) throws IOException
	{
		setData(IOUtils.toBytes(data));
	}

	public void setData(InputStream data) throws IOException
	{
		m_sourceDocument.setData(data);
	}

	public void setData(File file) throws IOException
	{
		m_sourceDocument.setDataFromFile(file.getAbsolutePath());
	}

	@Override
	public long getSourceDocumentId()
	{
		return m_sourceDocumentId.getValue();
	}

	public Map<String, String> metaData() throws IOException, SQLException
	{
		if (m_metaData == null) {
			Map<String, String> metaData = new TreeMap<String, String>();

			if (!isNew()) {
				Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
				PreparedStatement selectStmt = null;
				ResultSet dataRows = null;

				try {
					selectStmt = dbConn.prepareStatement("SELECT FIELD, VALUE FROM item_metadata WHERE USERDATA_ID = ?");

					m_key.setParameter(selectStmt, 1);

					dataRows = selectStmt.executeQuery();

					while (dataRows.next())
						metaData.put(dataRows.getString(1), dataRows.getString(2));
				}
				finally {
					if (dataRows != null)
						dataRows.close();

					if (selectStmt != null)
						selectStmt.close();

					dbConn.close();
				}
			}

			m_metaData = new MetaDataMap(metaData);
		}

		return m_metaData;
	}

	public List<UserItemDataRecord> dataRecords() throws IOException, SQLException
	{
		if (m_dataRecords == null) {
			List<UserItemDataRecord> newRecords;

			if (!isNew())
				newRecords = UserItemDataRecord.findDataRecords(getKey());
			else
				newRecords = new ArrayList<UserItemDataRecord>();

			m_dataRecords = new DataRecordList(newRecords);
		}

		return m_dataRecords;
	}

	public static UserDataItem findDataItem(String path) throws IOException, SQLException
	{
		if (!path.startsWith(SEPARATOR))
			throw new WorkbenchException("Path must be absolute");

		int offset = path.lastIndexOf(SEPARATOR);
		Long enclosingFolderId;

		if (offset > 1) {
			String folderName = path.substring(0, offset);
			Folder enclosingFolder = Folder.findFolder(folderName);

			if (enclosingFolder == null)
				return null;

			enclosingFolderId = enclosingFolder.getFolderId();
		}
		else
			enclosingFolderId = null;

		String fileName = path.substring(offset + 1);
		List<UserDataItem> items = findDataItems(new LongCriterion("ENCLOSING_FOLDER_ID", enclosingFolderId), new StringCriterion("LABEL", fileName));

		if (items.isEmpty())
				return null;

		return items.get(0);
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof UserDataItem == false)
			return false;

		UserDataItem otherItem = (UserDataItem) other;

		if (isNew() || otherItem.isNew())
			return false;

		return getUserDataId() == otherItem.getUserDataId();
	}

	@Override
	public int hashCode()
	{
		return (new Long(getUserDataId())).hashCode();
	}

	@Override
	public int compareTo(UserDataItem other)
	{
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getUserDataId() - other.getUserDataId());
	}


	// package methods


	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
		m_sourceDocument.save(dbConn);

		m_sourceDocumentId.setValue(m_sourceDocument.getSourceDocumentId());

		super.save(dbConn);
	}

	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
		super.load(dbConn);

		m_sourceDocument = new SourceDocumentRow(dbConn, m_sourceDocumentId.getValue());
		m_metaData = null;
		m_dataRecords = null;
	}

	@Override
	void delete(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

		delete(dbConn, getKey(), m_sourceDocumentId.getValue());

		m_key.reset();
	}

	/*
		Returns list of matching data items.
		Multiple criteria are AND'd together (all must be satisfied).

		Returns empty list if nothing matches.
	*/
	static List<UserDataItem> findDataItems(Criterion... keys) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder("SELECT " + KEY_NAME + " FROM " + TABLE_NAME + " WHERE ");

		stmtBuilder.append(keys[0].getPhrase());

		for (int i = 1 ; i < keys.length ; i += 1) {
			stmtBuilder.append(" AND ");
			stmtBuilder.append(keys[i].getPhrase());
		}

		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet itemRows = null;

		try {
			selectStmt = dbConn.prepareStatement(stmtBuilder.toString());

			int index = 1;

			for (int i = 0 ; i < keys.length ; i += 1)
				index = keys[i].setParameter(selectStmt, index);

			itemRows = selectStmt.executeQuery();

			List<UserDataItem> dataItems = new ArrayList<UserDataItem>();

			while (itemRows.next())
				dataItems.add(new UserDataItem(dbConn, itemRows.getLong(1)));

			return dataItems;
		}
		finally {
			if (itemRows != null)
				itemRows.close();

			if (selectStmt != null)
				selectStmt.close();

			dbConn.close();
		}
	}

	public static List<UserDataItem> findDataItemsWithComment(String comment) throws IOException, SQLException
	{
		return findDataItems(new StringCriterion("COMMENT", comment));
	}

	public static UserDataItem findDataItemByUUID(String uuid) throws IOException, SQLException
	{
		List<UserDataItem> items = findDataItems(new StringCriterion("UUID", uuid));
		if (items.size() > 0)
		{
			return items.get(0);
		}
		return null;
	}

	static public List<UserDataItem> findDataItemsByAge(int age) throws IOException, SQLException
	{
		Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
		PreparedStatement selectStmt = null;
		ResultSet itemRows = null;

		try
		{
			selectStmt = dbConn.prepareStatement("SELECT " + KEY_NAME + " FROM " + TABLE_NAME + " WHERE DATEDIFF(NOW(), CREATION_DATE) > ? ");
			selectStmt.setInt(1, age);
			itemRows = selectStmt.executeQuery();
			List<UserDataItem> dataItems = new ArrayList<UserDataItem>();
			while (itemRows.next())
			{
				dataItems.add(new UserDataItem(dbConn, itemRows.getLong(1)));
			}
			return dataItems;
		}
		finally
		{
			if (itemRows != null)
			{
				itemRows.close();
			}
			if (selectStmt != null)
			{
				selectStmt.close();
			}
			dbConn.close();
		}
	}

	static void delete(Connection dbConn, long userDataId) throws IOException, SQLException
	{
		Criterion key = new LongCriterion(KEY_NAME, userDataId);
		long sourceDocumentId = getSourceDocId(dbConn, key);

		delete(dbConn, key, sourceDocumentId);
	}


	// private methods


	private static void delete(Connection dbConn, Criterion userDataKey, long sourceDocumentId) throws IOException, SQLException
	{
		(new DeleteOp("item_metadata", userDataKey)).execute(dbConn);

		deleteDataRecords(dbConn, userDataKey);

		(new DeleteOp(TABLE_NAME, userDataKey)).execute(dbConn);

		SourceDocumentRow.delete(dbConn, sourceDocumentId);
	}

	private static long getSourceDocId(Connection dbConn, Criterion userDataKey) throws IOException, SQLException
	{
		Column<Long> sourceDocumentId = new LongColumn("SOURCE_DOCUMENT_ID", false);

		(new SelectOp(TABLE_NAME, userDataKey, sourceDocumentId)).execute(dbConn);

		return sourceDocumentId.getValue();
	}

	private static void deleteDataRecords(Connection dbConn, Criterion userDataKey) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder();

		stmtBuilder.append("SELECT RECORD_ID FROM data_records WHERE ");
		stmtBuilder.append(userDataKey.getPhrase());

		PreparedStatement selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
		ResultSet recordRows = null;

		try {
			userDataKey.setParameter(selectStmt, 1);

			recordRows = selectStmt.executeQuery();

			while (recordRows.next())
				UserItemDataRecord.delete(dbConn, recordRows.getLong(1));
		}
		finally {
			if (recordRows != null)
				recordRows.close();

			selectStmt.close();
		}
	}
}
