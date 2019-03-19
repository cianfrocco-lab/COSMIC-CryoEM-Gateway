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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
 *           The COSMIC2 gateway now allows user data to be directory
 *           rather than just a file previously.  This means it is possible
 *           now that m_sourceDocument and m_sourceDocumentId are null.
 *           However, the columns to be saved or loaded from the database
 *           are determined in the constructors via construct() but now we
 *           don't know if there is a source_document_id column until later
 *           so extra code is needed before save() and load() to handle 
 *           the possible "missing" source_document_id column.
 *
 */
public class TaskInputSourceDocument extends GeneratedKeyRow implements SourceDocument, Comparable<TaskInputSourceDocument> {

    private static final Log log = LogFactory.getLog ( TaskInputSourceDocument.class );

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

        //log.debug ( "MONA : entered TaskInputSourceDocument(1)" );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

		setDataFormat(DataFormat.UNKNOWN);
		setDataType(DataType.UNKNOWN);
		setEntityType(EntityType.UNKNOWN);
		setValidated(false);

		//m_sourceDocument = new SourceDocumentRow();
	}

	public TaskInputSourceDocument(long inputDocumentId) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

        //log.debug ( "MONA : entered TaskInputSourceDocument(2)" );
        //log.debug ( "MONA : inputDocumentId = " + inputDocumentId );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

		m_key.assignValue(inputDocumentId);

		load();
	}

	public TaskInputSourceDocument(SourceDocument document) throws IOException, SQLException
	{
		this(TABLE_NAME, KEY_NAME);

        //log.debug ( "MONA : entered TaskInputSourceDocument(3)" );
        //log.debug ( "MONA : document = " + document );
        //log.debug ( "MONA : document class name = " + document.getClass().getName() );
        //log.debug ( "MONA : document.getName() = " + document.getName() );
        long sourceDocumentId = document.getSourceDocumentId();
        //log.debug ( "MONA : sourceDocumentId = " + sourceDocumentId );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );
        //log.debug ( "MONA : m_columns.size 1 = " + m_columns.size() );

        //if ( document instanceof UserDataItem &&m_sourceDocumentId == null )
        if ( sourceDocumentId != 0 && document instanceof UserDataItem &&
            m_sourceDocumentId == null )
        {
            //log.debug ( "MONA: adding!" );
	        m_sourceDocumentId = new LongColumn ( "SOURCE_DOCUMENT_ID", false );
            //m_sourceDocumentId.setValue ( document.getSourceDocumentId() );
            m_sourceDocumentId.setValue ( sourceDocumentId );
            m_columns.add ( m_sourceDocumentId );
        }
        //log.debug ( "MONA : m_columns.size 2 = " + m_columns.size() );

		setDataFormat(document.getDataFormat());
		setDataType(document.getDataType());
		setEntityType(document.getEntityType());
		setName(document.getName());
		setValidated(document.isValidated());

        if ( sourceDocumentId != 0 )
		    m_sourceDocument = new SourceDocumentRow(document);
	}

	public TaskInputSourceDocument(String name, byte[] data)
	{
		this(name, EntityType.UNKNOWN, DataType.UNKNOWN, DataFormat.UNKNOWN, data, false);

        //log.debug ( "MONA : entered TaskInputSourceDocument(4)" );
        //log.debug ( "MONA : name = " + name );
        //log.debug ( "MONA : data = " + data );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );
	}

	public TaskInputSourceDocument(String name, EntityType entity, DataType dataType, DataFormat format, byte[] data, boolean validated)
	{
		this(TABLE_NAME, KEY_NAME);

        //log.debug ( "MONA : entered TaskInputSourceDocument(5)" );
        //log.debug ( "MONA : name = " + name );
        //log.debug ( "MONA : data = " + data );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

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

        //log.debug ( "MONA : entered TaskInputSourceDocument(6)" );
        //log.debug ( "MONA : name = " + name );
        //log.debug ( "MONA : data = " + data );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );
	}

	public TaskInputSourceDocument(String name, EntityType entity, DataType dataType, DataFormat format, InputStream data, boolean validated) throws IOException
	{
		this(TABLE_NAME, KEY_NAME);

        //log.debug ( "MONA : entered TaskInputSourceDocument(7)" );
        //log.debug ( "MONA : name = " + name );
        //log.debug ( "MONA : data = " + data );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

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

        //log.debug ( "MONA : entered TaskInputSourceDocument(8)" );
        //log.debug ( "MONA : inputDocumentId = " + inputDocumentId );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

		m_key.assignValue(inputDocumentId);

		load(dbConn);
	}

	private TaskInputSourceDocument(String tableName, String keyName)
	{
		super(TABLE_NAME, KEY_NAME);

        //log.debug ( "MONA : entered TaskInputSourceDocument(9)" );
        //log.debug ( "MONA : tableName = " + tableName );
        //log.debug ( "MONA : keyName = " + keyName );
        //log.debug ( "MONA : m_inputId.getValue() 1 = " + m_inputId.getValue() );
        //log.debug ( "MONA : m_name.getValue() 1 = " + m_name.getValue() );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );

        if ( m_sourceDocumentId == null )
		    construct ( m_inputId, m_dataFormat, m_dataType, m_entityType,
                m_name, m_validated );
        else
		    construct ( m_inputId, m_dataFormat, m_dataType, m_entityType,
                m_name, m_validated, m_sourceDocumentId );
        //log.debug ( "MONA : m_columns.size() = " + m_columns.size() );
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
        //log.debug ( "MONA : entered getSourceDocumentId()" );
        //log.debug ( "MONA : m_sourceDocumentId = " + m_sourceDocumentId );
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

    /*
     * We now need to check to see if we need to add the missing
     * source_document_id column to the row to be saved...
     */
	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered TaskInputSourceDocument.save()" );
        //log.debug ( "MONA : m_sourceDocument = " + m_sourceDocument );
        //log.debug ( "MONA : m_sourceDocumentId 1 = " + m_sourceDocumentId );
        //log.debug ( "MONA : m_columns.size 1 = " + m_columns.size() );

        /*
        if ( m_sourceDocumentId != null )
            log.debug ( "MONA : m_sourceDocumentId.getValue() 1 = " + m_sourceDocumentId.getValue() );
        */
        
        if ( m_sourceDocument != null )
        {
            //log.debug ( "MONA : m_sourceDocument.getSourceDocumentId() = " + m_sourceDocument.getSourceDocumentId() );
		    m_sourceDocument.save(dbConn);

            if ( m_sourceDocumentId == null )
            {
                //log.debug ( "MONA : creating new m_sourceDocumentId!" );
	            m_sourceDocumentId = new LongColumn ( "SOURCE_DOCUMENT_ID",
                    false );
                m_columns.add ( m_sourceDocumentId );
                //log.debug ( "MONA : m_columns.size 2 = " + m_columns.size() );
            }

		    m_sourceDocumentId.setValue
                ( m_sourceDocument.getSourceDocumentId() );
        }

        // save the task_input_source_documents table entry
		super.save(dbConn);
	}

    /*
     * Now need to add the missing source_document_id column and after
     * retrieval, if that column = 0, then don't load the source document
     * since there is none (this will be the case if the source document
     * is a UserDataDirItem
     */
	@Override
	void load(Connection dbConn) throws IOException, SQLException
	{
        //log.debug ( "MONA : entered TaskInputSourceDocument.load()" );
        //log.debug ( "MONA : m_sourceDocumentId 1 = " + m_sourceDocumentId );
        //log.debug ( "MONA : m_columns.size 1 = " + m_columns.size() );

	    m_sourceDocumentId = new LongColumn ( "SOURCE_DOCUMENT_ID", false );
        m_columns.add ( m_sourceDocumentId );
        //log.debug ( "MONA : m_columns.size 2 = " + m_columns.size() );
		super.load(dbConn);
        //log.debug ( "MONA : m_columns.size 3 = " + m_columns.size() );
        //log.debug ( "MONA : m_sourceDocumentId 2 = " + m_sourceDocumentId.getValue() );

        if ( m_sourceDocumentId.getValue() != 0 )
		    m_sourceDocument = new SourceDocumentRow ( dbConn,
                m_sourceDocumentId.getValue() );
        //log.debug ( "MONA : m_sourceDocument = " + m_sourceDocument );
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
        //log.debug ( "MONA : entered getSourceDocId()" );
        //log.debug ( "MONA : inputDocumentKey = " + inputDocumentKey );
        //log.debug ( "MONA : m_sourceDocumentId = " + this.m_sourceDocumentId );
		Column<Long> sourceDocumentId = new LongColumn("SOURCE_DOCUMENT_ID", false);

		(new SelectOp(TABLE_NAME, inputDocumentKey, sourceDocumentId)).execute(dbConn);

		return sourceDocumentId.getValue();
	}
}
