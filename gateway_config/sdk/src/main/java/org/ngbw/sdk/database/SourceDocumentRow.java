/*
 * SourceDocumentRow.java
 */
package org.ngbw.sdk.database;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.common.util.IOUtils;


/**
 *
 * @author Paul Hoover
 *
 */
class SourceDocumentRow extends GeneratedKeyRow implements Comparable<SourceDocumentRow> {

	// data fields


	private static final String TABLE_NAME = "source_documents";
	private static final String KEY_NAME = "SOURCE_DOCUMENT_ID";
	private static boolean m_useCompression;
	private final Column<Long> m_length = new LongColumn("LENGTH", false);
	private final Column<String> m_signature = new StringColumn("SIGNATURE", false, 255);
	private final FileColumn m_data = new FileColumn("FILENAME", false, m_useCompression, this);


	static {
		String useCompression = Workbench.getInstance().getProperties().getProperty("database.useFileCompression");

		m_useCompression = Boolean.parseBoolean(useCompression);
	}


	// constructors


	/**
	 *
	 */
	SourceDocumentRow()
	{
		super(TABLE_NAME, KEY_NAME);

		construct(m_length, m_signature, m_data);
	}

	/**
	 *
	 * @param sourceDocumentId
	 * @throws SQLException
	 * @throws IOException
	 */
	SourceDocumentRow(long sourceDocumentId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(sourceDocumentId);

		load();
	}

	/**
	 *
	 * @param dbConn
	 * @param sourceDocumentId
	 * @throws SQLException
	 * @throws IOException
	 */
	SourceDocumentRow(Connection dbConn, long sourceDocumentId) throws IOException, SQLException
	{
		this();

		m_key.assignValue(sourceDocumentId);

		load(dbConn);
	}

	/**
	 *
	 * @param data
	 */
	SourceDocumentRow(byte[] data)
	{
		this();

		setData(data);
	}

	/**
	 *
	 * @param data
	 * @throws IOException
	 */
	SourceDocumentRow(InputStream data) throws IOException
	{
		this();

		setData(data);
	}

	/**
	 *
	 * @param document
	 * @throws IOException
	 * @throws SQLException
	 */
	SourceDocumentRow(SourceDocument document) throws IOException, SQLException
	{
		this();

		if (document.getSourceDocumentId() != 0){
			m_key.assignValue(document.getSourceDocumentId());
		}
		InputStream data = document.getDataAsStream();

		setData(data);
	}


	// public methods


	/**
	 *
	 * @return long
	 */
	public long getSourceDocumentId()
	{
		return m_key.getValue();
	}

	/**
	 *
	 * @return
	 */
	public byte[] getData()
	{
		return m_data.getValue();
	}

	/**
	 *
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public InputStream getDataAsStream() throws IOException, SQLException
	{
		return m_data.getValueAsStream();
	}

	/**
	 *
	 * @return
	 */
	public long getDataLength()
	{
		return m_length.getValue();
	}

	/**
	 *
	 * @param data
	 */
	public void setData(byte[] data)
	{
		m_data.setValue(data);
		m_length.setValue(m_data.getLength());
		m_signature.setValue(m_data.getSignature());
	}

	/**
	 *
	 * @param data
	 */
	public void setData(String data)
	{
		setData(data.getBytes());
	}

	/**
	 *
	 * @param data
	 * @throws IOException
	 */
	public void setData(Serializable data) throws IOException
	{
		setData(IOUtils.toBytes(data));
	}

	/**
	 *
	 * @param data
	 * @throws IOException
	 */
	public void setData(InputStream data) throws IOException
	{
		m_data.setValue(data);
		m_length.setValue(m_data.getLength());
		m_signature.setValue(m_data.getSignature());
	}

	/**
	 *
	 * @param fileName
	 * @throws IOException
	 */
	public void setDataFromFile(String fileName) throws IOException
	{
		m_data.setValue(fileName);
		m_length.setValue(m_data.getLength());
		m_signature.setValue(m_data.getSignature());
	}

	/**
	 *
	 * @param other
	 * @return
	 */
	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof SourceDocumentRow == false)
			return false;

		SourceDocumentRow otherDoc = (SourceDocumentRow) other;

		if (isNew() || otherDoc.isNew())
			return false;

		return getSourceDocumentId() == otherDoc.getSourceDocumentId();
	}

	/**
	 *
	 * @return
	 */
	@Override
	public int hashCode()
	{
		return (new Long(getSourceDocumentId())).hashCode();
	}

	/**
	 *
	 * @param other
	 * @return
	 */
	@Override
	public int compareTo(SourceDocumentRow other)
	{
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getSourceDocumentId() - other.getSourceDocumentId());
	}


	// package methods


	/**
	 * Saves the current state of the object to the database. If the object has not yet been persisted,
	 * new records are inserted in the appropriate tables. If the object has been persisted, then any
	 * changes are written to the backing tables. Only those values that have changed are written, and
	 * if the state of the object is unchanged, the method does nothing.
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			insert(dbConn);
		else {
			if (!m_data.isModified())
				return;

			if (numReferences(dbConn, getKey()) > 1) {
				m_data.cancelCleanup();

				insert(dbConn);
			}
			else {
				pushUpdateOps();

				executeOps(dbConn);
			}
		}
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
		m_data.reset();

		super.load(dbConn);
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	void delete(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

		delete(dbConn, getKey());

		m_data.reset();
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @param sourceDocumentId
	 * @throws IOException
	 * @throws SQLException
	 */
	static void delete(Connection dbConn, long sourceDocumentId) throws IOException, SQLException
	{
		Criterion key = new LongCriterion(KEY_NAME, sourceDocumentId);

		delete(dbConn, key);
	}


	// private methods


	/**
	 *
	 * @param dbConn
	 * @throws IOException
	 * @throws SQLException
	 */
	private void insert(Connection dbConn) throws IOException, SQLException
	{
		long duplicateId = findDuplicate(dbConn);

		if (duplicateId > 0) {
			m_key.assignValue(duplicateId);

			load(dbConn);
		}
		else {
			pushInsertOps();

			try {
				executeOps(dbConn);
			}
			catch (SQLException sqlErr) {
				String state = sqlErr.getSQLState();

				if (state.equals("23000") || state.equals("23505")) {
					duplicateId = findDuplicate(dbConn);

					m_key.assignValue(duplicateId);

					load(dbConn);
				}
				else
					throw sqlErr;
			}
		}
	}

	/**
	 *
	 * @param dbConn
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	private long findDuplicate(Connection dbConn) throws IOException, SQLException
	{
		if (m_signature.isNull())
			return 0;

		SimpleKey signature = new SimpleKey(m_signature);
		Column<Long> duplicateId = new LongColumn(m_key.getName(), false);

		(new SelectOp(TABLE_NAME, signature, duplicateId, false)).execute(dbConn);

		return duplicateId.getValue();
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @param sourceDocumentKey
	 * @throws IOException
	 * @throws SQLException
	 */
	private static void delete(Connection dbConn, Criterion sourceDocumentKey) throws IOException, SQLException
	{
		if (numReferences(dbConn, sourceDocumentKey) > 0)
			return;

		deleteFile(dbConn, sourceDocumentKey);

		(new DeleteOp(TABLE_NAME, sourceDocumentKey)).execute(dbConn);
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @param sourceDocumentKey
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	private static int numReferences(Connection dbConn, Criterion sourceDocumentKey) throws IOException, SQLException
	{
		Column<Integer> result = new IntegerColumn("result", true);
		int count = 0;

		(new CountOp("userdata", sourceDocumentKey, result)).execute(dbConn);

		count += result.getValue();

		(new CountOp("task_input_source_documents", sourceDocumentKey, result)).execute(dbConn);

		count += result.getValue();

		(new CountOp("task_output_source_documents", sourceDocumentKey, result)).execute(dbConn);

		count += result.getValue();

		return count;
	}

	/**
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @param sourceDocumentKey
	 * @throws SQLException
	 */
	private static void deleteFile(Connection dbConn, Criterion sourceDocumentKey) throws IOException, SQLException
	{
		StringBuilder stmtBuilder = new StringBuilder();

		stmtBuilder.append("SELECT FILENAME FROM " + TABLE_NAME + " WHERE ");
		stmtBuilder.append(sourceDocumentKey.getPhrase());

		PreparedStatement selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
		ResultSet fileRow = null;

		try {
			sourceDocumentKey.setParameter(selectStmt, 1);

			fileRow = selectStmt.executeQuery();

			if (fileRow.next())
				FileColumn.delete(fileRow.getString(1));
		}
		finally {
			if (fileRow != null)
				fileRow.close();

			selectStmt.close();
		}
	}
}
