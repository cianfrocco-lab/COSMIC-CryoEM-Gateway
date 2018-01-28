package org.ngbw.sdk.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Choonhan Youn
 *
 */

public class TransferRecord extends GeneratedKeyRow implements Comparable<TransferRecord> {

    private static final Log log = LogFactory.getLog(TransferRecord.class.getName());
    private static String accountingPeriodStart = null;

    private static final String TABLE_NAME = "transfer_record";
    private static final String KEY_NAME = "TR_ID"; // TR_ID is the primary, auto inc key

    private final Column<Long> m_userId = new LongColumn("USER_ID", true);
    private final Column<String> m_taskid = new StringColumn("TASK_ID", true, 255);
    private final Column<String> m_srcendpoint = new StringColumn("SRC_ENDPOINTNAME", true, 255);
    private final Column<String> m_destendpoint = new StringColumn("DEST_ENDPOINTNAME", true, 255);
    private final Column<String> m_requesttime = new StringColumn("REQUEST_TIME", true, 255);
    private final Column<String> m_comptime = new StringColumn("COMPLETION_TIME", true, 255);
    private final Column<String> m_status = new StringColumn("STATUS", true, 255);
    private final Column<Integer> m_filestransferred = new IntegerColumn("FILES_TRANSFERRED", true);
    private final Column<Integer> m_synclevel = new IntegerColumn("SYNC_LEVEL", true);
    private final Column<Integer> m_faults = new IntegerColumn("FAULTS", true);
    private final Column<Integer> m_dirs = new IntegerColumn("DIRECTORIES", true);
    private final StreamColumn<String> m_filenames = new TextColumn("FILE_NAMES", true, this);
    private final StreamColumn<String> m_directoryames = new TextColumn("DIRECTORY_NAMES", true, this);
    private final Column<Integer> m_files = new IntegerColumn("FILES", true);
    private final Column<Integer> m_filesskipped = new IntegerColumn("FILES_SKIPPED", true);
    private final Column<Long> m_bytetransferred = new LongColumn("BYTE_TRANSFERRED", true);
    private final Column<Long> m_folderid = new LongColumn("ENCLOSING_FOLDER_ID", true);

    // constructors
    public TransferRecord()
    {
        super(TABLE_NAME, KEY_NAME);

        construct(m_userId, m_taskid, m_srcendpoint,
                m_destendpoint, m_requesttime, m_comptime,
                m_status, m_filestransferred, m_synclevel,
                m_faults, m_dirs, m_filenames, m_directoryames,
                m_files, m_filesskipped, m_bytetransferred, m_folderid);
    }

    public TransferRecord(long trId) throws IOException, SQLException
    {
        this();
        m_key.assignValue(trId);
        load();
    }

    public TransferRecord(Connection dbConn, long trId) throws IOException, SQLException
    {
        this();
        m_key.assignValue(trId);
        load(dbConn);
    }


    // public methods
    public long getTrId()
    {
        return m_key.getValue();
    }
    public long getUserId()
    {
        return m_userId.getValue();
    }
    public void setUserId(long userId)
    {
        m_userId.setValue(userId);
    }

    public String getTaskId() {
        return m_taskid.getValue();
    }
    public String getSrcEndpointname() {
        return m_srcendpoint.getValue();
    }
    public String getDestEndpointname() {
        return m_destendpoint.getValue();
    }
    public String getRequestTime() {return m_requesttime.getValue();}
    public String getCompletionTime() {return m_comptime.getValue();}
    public String getStatus() {
        return m_status.getValue();
    }
    public int getFilesTransferred() { return m_filestransferred.getValue();}
    public int getSyncLevel() { return m_synclevel.getValue();}
    public int getFaults() { return m_faults.getValue();}
    public int getDirectories() { return m_dirs.getValue();}
    public int getFiles() { return m_files.getValue();}
    public int getFilesSkipped() {return m_filesskipped.getValue();}
    public long getByteTransferred() { return m_bytetransferred.getValue();}
    public long getEnclosingFolderId() { return m_folderid.getValue();}
    public String getFileNames() { return m_filenames.getValue();}
    public String getDirectoryNames() { return m_directoryames.getValue();}

    public void setTaskId(String taskId) {
        m_taskid.setValue(taskId);
    }
    public void setSrcEndpointname(String srcEndpointname) {
        m_srcendpoint.setValue(srcEndpointname);
    }
    public void setDestEndpointname(String destEndpointname) {m_destendpoint.setValue(destEndpointname);}
    public void setRequestTime(String requestTime) {m_requesttime.setValue(requestTime);}
    public void setCompletionTime(String completionTime) {
        m_comptime.setValue(completionTime);
    }
    public void setStatus(String status) {
        m_status.setValue(status);
    }
    public void setFilesTransferred(int filesTransferred) { m_filestransferred.setValue(filesTransferred);}
    public void setSyncLevel(int syncLevel) { m_synclevel.setValue(syncLevel); }
    public void setFaults(int faults) { m_faults.setValue(faults); }
    public void setDirectories(int directories) { m_dirs.setValue(directories); }
    public void setFiles(int files) { m_files.setValue(files); }
    public void setFilesSkipped(int filesSkipped) { m_filesskipped.setValue(filesSkipped); }
    public void setByteTransferred(long byteTransferred) { m_bytetransferred.setValue(byteTransferred); }
    public void setEnclosingFolderId(long enclosingFolderId) { m_folderid.setValue(enclosingFolderId); }
    public void setFileNames(String fileNames) { m_filenames.setValue(fileNames); }
    public void setDirectoryNames(String directoryNames) { m_directoryames.setValue(directoryNames); }

    public static List<TransferRecord> findAllTransferRecord() throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
        PreparedStatement selectStmt = null;
        ResultSet userRows = null;

        try {
            selectStmt = dbConn.prepareStatement("SELECT " + KEY_NAME + " FROM " + TABLE_NAME);
            userRows = selectStmt.executeQuery();
            List<TransferRecord> records = new ArrayList<TransferRecord>();

            while (userRows.next())
                records.add(new TransferRecord(dbConn, userRows.getLong(1)));

            return records;
        }
        finally {
            if (userRows != null)
                userRows.close();

            if (selectStmt != null)
                selectStmt.close();

            dbConn.close();
        }
    }

    public static List<TransferRecord> findAllTaskIDByUserId(long userId) throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
        PreparedStatement selectStmt = null;
        ResultSet userRows = null;

        try {
            String query = "SELECT " + KEY_NAME + " FROM " + TABLE_NAME + " WHERE USER_ID = "+userId+
                    " AND (STATUS = 'ACTIVE' OR STATUS = 'INACTIVE')";
            selectStmt = dbConn.prepareStatement(query);
            userRows = selectStmt.executeQuery();
            List<TransferRecord> taskids = new ArrayList<TransferRecord>();

            while (userRows.next())
                taskids.add(new TransferRecord(dbConn, userRows.getLong(1)));

            return taskids;
        }
        finally {
            if (userRows != null)
                userRows.close();

            if (selectStmt != null)
                selectStmt.close();

            dbConn.close();
        }
    }

    public static TransferRecord findTransferRecordByTaskId(String taskId) throws IOException, SQLException
    {
        return findTransferRecord(new StringCriterion("TASK_ID", taskId));
    }


    @Override
    public boolean equals(Object other)
    {
        if (other == null)
            return false;

        if (this == other)
            return true;

        if (other instanceof TransferRecord == false)
            return false;

        TransferRecord otherUser = (TransferRecord) other;

        if (isNew() || otherUser.isNew())
            return false;

        return getTrId() == otherUser.getTrId();
    }

    @Override
    public int hashCode()
    {
        return (new Long(getTrId())).hashCode();
    }

    @Override
    public int compareTo(TransferRecord other)
    {
        if (other == null)
            throw new NullPointerException("other");

        if (this == other)
            return 0;

        if (isNew())
            return -1;

        if (other.isNew())
            return 1;

        return (int) (getTrId() - other.getTrId());
    }

    // private methods
    private static TransferRecord findTransferRecord(Criterion key) throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

        try {
            Column<Long> trId = new LongColumn(KEY_NAME, false);
            (new SelectOp(TABLE_NAME, key, trId, false)).execute(dbConn);

            if (trId.isNull())
                return null;

            return new TransferRecord(dbConn, trId.getValue());
        }
        finally {
            dbConn.close();
        }
    }

}
