/*
 * TaskInputSourceDocument.java
 */
package org.ngbw.sdk.database;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
 * 12/5/17 - Modified by Mona Wong.
 *              The COSMIC2 gateway now allows user data to be directory
 *              rather than a file as previously.  This means it is possible
 *              now that m_sourceDocument and m_sourceDocumentId are null.
 */
public class TaskInputSourceDocument extends GeneratedKeyRow implements SourceDocument, Comparable<TaskInputSourceDocument> {

	// data fields


	private static final String TABLE_NAME = "task_input_source_documents";
	private static final String KEY_NAME = "INPUT_DOCUMENT_ID";
	private final Column<Long> m_inputId = new LongColumn("INPUT_ID", false);
	private final Column<String> m_dataFormat = new StringColumn("DATAFORMAT", false, 255);
	private final Column<String> m_dataType = new StringColumn("DATATYPE", false, 255);
	private final Column<String> m_entityType = new StringColumn("ENTITYTYPE", false, 255);
	private final Column<String> m_name = new StringColumn("NAME", true, 255);
	private final Column<Boolean> m_validated = new BooleanColumn("VALIDATED", false);
	//private final Column<Long> m_sourceDocumentId = new LongColumn("SOURCE_DOCUMENT_ID", false);
	private Column<Long> m_sourceDocumentId = null;
	private SourceDocumentRow m_sourceDocument = null;


	// constructors


	public TaskInputSourceDocument()
	{
		this(TABLE_NAME, KEY_NAME);

		setDataFormat(DataFormat.UNKNOWN);
		setDataType(DataType.UNKNOWN);
		setEntityType(EntityType.UNKNOWN);
		setValidated(false);

		//m_sourceDocument = new SourceDocumentRow();
	}

	public TaskInputSourceDocument(long inputDocumentId) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

		m_key.assignValue(inputDocumentId);

		load();
	}

	public TaskInputSourceDocument(SourceDocument document) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

		setDataFormat(document.getDataFormat());
		setDataType(document.getDataType());
		setEntityType(document.getEntityType());
		setName(document.getName());
		setValidated(document.isValidated());

		m_sourceDocument = new SourceDocumentRow(document);
	}

	public TaskInputSourceDocument(String name, byte[] data)
	{
		this(name, EntityType.UNKNOWN, DataType.UNKNOWN, DataFormat.UNKNOWN, data, false);
	}

	public TaskInputSourceDocument(String name, EntityType entity, DataType dataType, DataFormat format, byte[] data, boolean validated)
	{
		this(TABLE_NAME, KEY_NAME);

		setDataFormat(format);
		setDataType(dataType);
		setEntityType(entity);
		setName(name);
		setValidated(validated);

        if ( data != null )
		    m_sourceDocument = new SourceDocumentRow(data);
	}

	public TaskInputSourceDocument(String name, InputStream data) throws IOException
	{
		this(name, EntityType.UNKNOWN, DataType.UNKNOWN, DataFormat.UNKNOWN, data, false);
	}

	public TaskInputSourceDocument(String name, EntityType entity, DataType dataType, DataFormat format, InputStream data, boolean validated) throws IOException
	{
		this(TABLE_NAME, KEY_NAME);

		setDataFormat(format);
		setDataType(dataType);
		setEntityType(entity);
		setName(name);
		setValidated(validated);

        if ( data != null )
		    m_sourceDocument = new SourceDocumentRow(data);
	}

	TaskInputSourceDocument(Connection dbConn, long inputDocumentId) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

		m_key.assignValue(inputDocumentId);

		load(dbConn);
	}

	private TaskInputSourceDocument(String tableName, String keyName)
	{
		super(TABLE_NAME, KEY_NAME);

        if ( m_sourceDocumentId == null )
		    construct ( m_inputId, m_dataFormat, m_dataType, m_entityType,
                m_name, m_validated );
        else
		    construct ( m_inputId, m_dataFormat, m_dataType, m_entityType,
                m_name, m_validated, m_sourceDocumentId );
	}


	// public methods


	public long getInputDocumentId()
	{
		return m_key.getValue();
	}

	public long getInputId()
	{
		return m_inputId.getValue();
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
	public String getName()
	{
		return m_name.getValue();
	}

	public void setName(String name)
	{
		m_name.setValue(name);
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
	public byte[] getData()
	{
        if ( m_sourceDocument != null )
		    return m_sourceDocument.getData();
        else
            return ( null );
	}

	@Override
	public InputStream getDataAsStream() throws IOException, SQLException
	{
        if ( m_sourceDocument != null )
		    return m_sourceDocument.getDataAsStream();
        else
            return ( null );
	}

	@Override
	public long getDataLength()
	{
        if ( m_sourceDocument != null )
		    return m_sourceDocument.getDataLength();
        else
            return ( 0L );
	}

	public void setData(byte[] data)
	{
        if ( m_sourceDocument == null )
		    m_sourceDocument = new SourceDocumentRow();

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
        if ( m_sourceDocument == null )
		    m_sourceDocument = new SourceDocumentRow();

		m_sourceDocument.setData(data);
	}

	@Override
	public long getSourceDocumentId()
	{
        if ( m_sourceDocument != null )
		    return m_sourceDocument.getSourceDocumentId();
        else
            return ( 0L );
	}

	public Task getTask() throws IOException, SQLException
	{
		return new TaskInputParameter(m_inputId.getValue()).getTask();
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == null)
			return false;

		if (this == other)
			return true;

		if (other instanceof TaskInputSourceDocument == false)
			return false;

		TaskInputSourceDocument otherDoc = (TaskInputSourceDocument) other;

		if (isNew() || otherDoc.isNew())
			return false;

		return getInputDocumentId() == otherDoc.getInputDocumentId();
	}

	@Override
	public int hashCode()
	{
		return (new Long(getInputDocumentId())).hashCode();
	}

	@Override
	public int compareTo(TaskInputSourceDocument other)
	{
		if (other == null)
			throw new NullPointerException("other");

		if (this == other)
			return 0;

		if (isNew())
			return -1;

		if (other.isNew())
			return 1;

		return (int) (getInputDocumentId() - other.getInputDocumentId());
	}


	// package methods


	void setInputId(Long inputId)
	{
		m_inputId.setValue(inputId);
	}

	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
        if ( m_sourceDocument != null )
        {
		    m_sourceDocument.save(dbConn);

            if ( m_sourceDocumentId == null )
	            m_sourceDocumentId = new LongColumn ( "SOURCE_DOCUMENT_ID",
                    false);

		    m_sourceDocumentId.setValue
                ( m_sourceDocument.getSourceDocumentId() );
        }

		super.save(dbConn);
	}

	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
		super.load(dbConn);

        if ( m_sourceDocumentId != null )
		    m_sourceDocument = new SourceDocumentRow ( dbConn,
                m_sourceDocumentId.getValue() );
	}

	@Override
	void delete(Connection dbConn) throws IOException, SQLException
	{
		if (isNew())
			throw new WorkbenchException("Not persisted");

        if ( m_sourceDocumentId != null )
		    delete(dbConn, getKey(), m_sourceDocumentId.getValue());
        else
		    delete ( dbConn, getKey() );

		m_key.reset();
	}

	static List<TaskInputSourceDocument> findInputDocuments(Connection dbConn, long inputId) throws IOException, SQLException
	{
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT INPUT_DOCUMENT_ID FROM " + TABLE_NAME + " WHERE INPUT_ID = ?");
		ResultSet inputRows = null;

		try {
			selectStmt.setLong(1, inputId);

			inputRows = selectStmt.executeQuery();

			List<TaskInputSourceDocument> inputDocs = new ArrayList<TaskInputSourceDocument>();

			while (inputRows.next())
				inputDocs.add(new TaskInputSourceDocument(dbConn, inputRows.getLong(1)));

			return inputDocs;
		}
		finally {
			if (inputRows != null)
				inputRows.close();

			selectStmt.close();
		}
	}

	static void delete(Connection dbConn, long inputDocumentId) throws IOException, SQLException
	{
		Criterion key = new LongCriterion(KEY_NAME, inputDocumentId);
		long sourceDocumentId = getSourceDocId(dbConn, key);

		delete(dbConn, key, sourceDocumentId);
	}


	// private methods


	private static void delete(Connection dbConn, Criterion inputDocumentKey, long sourceDocumentId) throws IOException, SQLException
	{
		(new DeleteOp(TABLE_NAME, inputDocumentKey)).execute(dbConn);

		SourceDocumentRow.delete(dbConn, sourceDocumentId);
	}

    /*
     * New function (copied from above) without sourceDocumentId
     */
	private static void delete ( Connection dbConn, Criterion
        inputDocumentKey ) throws IOException, SQLException
	{
		(new DeleteOp(TABLE_NAME, inputDocumentKey)).execute(dbConn);

		//SourceDocumentRow.delete(dbConn, sourceDocumentId);
	}

	private static long getSourceDocId(Connection dbConn, Criterion inputDocumentKey) throws IOException, SQLException
	{
		Column<Long> sourceDocumentId = new LongColumn("SOURCE_DOCUMENT_ID", false);

		(new SelectOp(TABLE_NAME, inputDocumentKey, sourceDocumentId)).execute(dbConn);

		return sourceDocumentId.getValue();
	}
}
