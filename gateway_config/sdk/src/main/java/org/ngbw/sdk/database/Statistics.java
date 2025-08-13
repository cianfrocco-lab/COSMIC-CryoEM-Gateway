/*
 * Statistics.java
 */
package org.ngbw.sdk.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.time.LocalDate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.Workbench;


public class Statistics extends Row implements Comparable<Statistics>
{
    private static final Log logger = LogFactory.getLog(Statistics.class.getName());
    private static Timestamp accountingPeriodStart = null;
    private static String cipresChargeNumber = Workbench.getInstance().getProperties().getProperty("cipres.charge.number");
    private static String MONTHLY = "monthly";

    static
    {
        // TODO: test where this shows up
//        String propStringVal = Workbench.getInstance().getProperties().getProperty("accounting.period.start");
        
//        if (propStringVal == null)
//        {
//            throw new RuntimeException("Unable to retrieve accounting.period.start from workbench.properties");
//        }
        
//        try
//        {
////            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
////            Date propDateVal = formatter.parse(propStringVal);
////            accountingPeriodStart = new Timestamp(propDateVal.getTime());
//            
//            accountingPeriodStart = getAccountingPeriodStart(true);
//            logger.debug("'accounting.period.start' for CPU Hrs is '" + ((new SimpleDateFormat("yyyy/MM/dd")).format(accountingPeriodStart)) + "'");
//        }
//        catch ( Exception e )
//        {
//            throw new RuntimeException("Unable to parse accounting.period.start from workbench.properties");
//        }

        if (cipresChargeNumber == null)
        {
            throw new RuntimeException("Unable to retrieve cipres.charge.number from workbench.properties");
        }
    }

    public static final int PREDICTED   = 0x1; 
    public static final int COMPUTED    = 0x2;
    public static final int CHARGED     = 0x4;
    public static final int OVERRIDE    = 0x8;
    public static final int ALL         = PREDICTED | COMPUTED | CHARGED | OVERRIDE;

    private boolean m_isNew = true;

    private static final String TABLE_NAME = "job_stats";

    // false = NOT NULL
    private final Column<String> m_jobhandle = new StringColumn("JOBHANDLE", false, 255);
    private final Column<String> m_resource = new StringColumn("RESOURCE", false, 100);
    private final Column<String> m_toolId = new StringColumn("TOOL_ID", false, 100);
    private final Column<Long> m_taskId = new LongColumn("TASK_ID", false);
    private final Column<Long> m_userId = new LongColumn("USER_ID", false);
    private final Column<String> m_email = new StringColumn("EMAIL", false, 100);
    private final Column<String> m_submitter = new StringColumn("SUBMITTER", false, 1023);
    private final Column<String> m_tgChargeNumber = new StringColumn("TG_CHARGE_NUMBER", true, 100);
    private final Column<Date> m_dateEntered = new DateColumn("DATE_ENTERED", false);
    private final Column<String> m_remoteJobId = new StringColumn("REMOTE_JOB_ID", true, 1023);
    private final Column<Date> m_dateSubmitted = new DateColumn("DATE_SUBMITTED", true);
    private final Column<Date> m_dateTerminated = new DateColumn("DATE_TERMINATED", true);
    private final Column<Long> m_suPredicted = new LongColumn("SU_PREDICTED", true);
    private final Column<Long> m_suComputed = new LongColumn("SU_COMPUTED", true);
    private final Column<Long> m_suCharged = new LongColumn("SU_CHARGED", true);
    private final Column<Long> m_suOverride = new LongColumn("SU_OVERRIDE", true);
    private final Column<String> m_appname = new StringColumn("APPNAME", false, 30);
    
    private final Column<String> m_sprops = new StringColumn("SPROPS", true, 8000);
//    private final Column<String> m_resourceModifier = new StringColumn("RESRC_MODIFIER", false, 255);
    private final Column<Long> m_resourceConversionId = new LongColumn("RESRC_CONVRTN_ID", false);
    

    public static Statistics find ( String jobhandle ) throws IOException, SQLException
    {
        Statistics s = new Statistics(); 
        s.m_jobhandle.setValue(jobhandle);
        s.m_isNew = false;
        try
        {
            s.load();
            return s;
        }
        catch ( NotExistException nee )
        {
            return null;
        }
    }

    // constructors

    private Statistics ()
    {
        super(TABLE_NAME);

        construct(m_resource, m_toolId, m_taskId, m_userId, m_email,
                  m_submitter, m_tgChargeNumber, m_dateEntered, m_remoteJobId,
                  m_dateSubmitted, m_dateTerminated, m_suPredicted, m_suComputed,
                  m_suCharged, m_suOverride, m_appname, m_sprops, 
                  m_resourceConversionId);
    }

    // Creates a new object (jobhandle must not be in db yet or save() will fail).
    // TODO: this is really hokey but I'm just passing in the Task as a way to
    // distinguish this ctor from the one above.

    public Statistics ( String jobhandle, Task task ) throws SQLException, IOException
    {
        this();
        m_jobhandle.assignValue(jobhandle);
        m_dateEntered.assignValue(new Date());
        m_taskId.assignValue(task.getTaskId());
        m_toolId.assignValue(task.getToolId());
        m_userId.assignValue(task.getUserId());
        m_email.assignValue((new User(task.getUserId())).getEmail());
        m_appname.assignValue(task.getAppname());
//        m_resourceModifier.assignValue(task.getResourceModifier());
        m_resourceConversionId.assignValue(task.getResourceConversionId());
    }


    Statistics ( Connection dbConn, String jobhandle ) throws IOException, SQLException
    {
        this();

        m_jobhandle.assignValue(jobhandle);

        m_isNew = false;

        load(dbConn);
    }


    public String getJobhandle ()
    {
        return m_jobhandle.getValue();
    }


    public String getResource ()
    {
        return m_resource.getValue();
    }


    public void setResource ( String i )
    {
        m_resource.setValue(i);
    }


    public String getToolId ()
    {
        return m_toolId.getValue();
    }


    public void setToolId ( String i )
    {
        m_toolId.setValue(i);
    }


    public long getTaskId ()
    {
        return m_taskId.getValue();
    }


    public void setTaskId ( long i )
    {
        m_taskId.setValue(i);
    }


    public long getUserId ()
    {
        return m_userId.getValue();
    }


    public void setUserId ( long i )
    {
        m_userId.setValue(i);
    }


    public long getSuPredicted ()
    {
        return m_suPredicted.getValue();
    }


    public void setSuPredicted ( long i )
    {
        m_suPredicted.setValue(i);
    }


    public long getSuComputed ()
    {
        return m_suComputed.getValue();
    }


    public void setSuComputed ( long i )
    {
        m_suComputed.setValue(i);
    }


    public boolean isSuComputedNull ()
    {
        return m_suComputed.isNull();
    }


    public long getSuCharged ()
    {
        return m_suCharged.getValue();
    }


    public void setSuCharged ( long i )
    {
        m_suCharged.setValue(i);
    }


    public long getSuOverride ()
    {
        return m_suOverride.getValue();
    }


    public void setSuOverride ( long i )
    {
        m_suOverride.setValue(i);
    }


    public String getEmail ()
    {
        return m_email.getValue();
    }


    public void setEmail ( String i )
    {
        m_email.setValue(i);
    }


    public String getAppname ()
    {
        return m_appname.getValue();
    }


    public void setAppname ( String i )
    {
        m_appname.setValue(i);
    }
    
    
    public Long getResourceConversionId ()
    {
        return m_resourceConversionId.getValue();
    }
    
    
    public void setResourceConversionId ( Long i )
    {
        m_resourceConversionId.setValue(i);
    }
    
    
//    public String getResourceModifier ()
//    {
//        return m_resourceModifier.getValue();
//    }
    
    
//    public void setResourceModifier ( String s )
//    {
//        m_resourceModifier.setValue(s);
//    }
    
    
    public String getSprops () 
    {
        return m_sprops.getValue();
    }
    
    
    public void setSprops ( String s ) 
    {
        m_sprops.setValue(s);
    }


    public String getSubmitter ()
    {
        return m_submitter.getValue();
    }


    public void setSubmitter ( String i )
    {
        m_submitter.setValue(i);
    }


    public String getTgChargeNumber ()
    {
        return m_tgChargeNumber.getValue();
    }


    public void setTgChargeNumber ( String i )
    {
        m_tgChargeNumber.setValue(i);
    }


    public Date getDateEntered ()
    {
        return m_dateEntered.getValue();
    }


    public void setDateEntered ( Date i )
    {
        m_dateEntered.setValue(i);
    }


    public String getRemoteJobId ()
    {
        return m_remoteJobId.getValue();
    }


    public void setRemoteJobId ( String i )
    {
        m_remoteJobId.setValue(i);
    }


    public Date getDateSubmitted ()
    {
        return m_dateSubmitted.getValue();
    }


    public void setDateSubmitted ( Date i )
    {
        m_dateSubmitted.setValue(i);
    }


    public Date getDateTerminated ()
    {
        return m_dateTerminated.getValue();
    }


    public void setDateTerminated ( Date i )
    {
        m_dateTerminated.setValue(i);
    }


    public Task getTask () throws IOException, SQLException
    {
        if (m_taskId.isNull())
        {
            return null;
        }
        return new Task(m_taskId.getValue());
    }


    /**
     * Indicates whether or not the object has been persisted.
     *
     * @return <code>true</code> if the object has not been persisted
     */
    @Override
    public boolean isNew ()
    {
        return m_isNew;
    }


    /**
     * @param other
     *
     * @return
     */
    @Override
    public boolean equals ( Object other )
    {
        if (other == null)
        {
            return false;
        }

        if (this == other)
        {
            return true;
        }

        if (other instanceof Statistics == false)
        {
            return false;
        }

        Statistics otherRow = (Statistics) other;

        // an object that hasn't been persisted can only be equal to itself
        if (isNew() || otherRow.isNew())
        {
            return false;
        }

        return getJobhandle().equals(otherRow.getJobhandle());
    }


    /**
     * @return
     */
    @Override
    public int hashCode ()
    {
        return (new String(getJobhandle())).hashCode();
    }


    /**
     *
     * @param other
     *
     * @return
     */
    @Override
    public int compareTo ( Statistics other )
    {
        if (other == null)
        {
            throw new NullPointerException("other");
        }

        if (this == other)
        {
            return 0;
        }

        if (isNew())
        {
            return -1;
        }

        if (other.isNew())
        {
            return 1;
        }

        return (int) (getJobhandle().compareTo(other.getJobhandle()));
    }


    // package methods
    /**
     * Returns a <code>Criterion</code> object that describes the primary key of the record that the
     * object represents.
     *
     * @return a <code>Criterion</code> object that describes the primary key
     */
    @Override
    Criterion getKey ()
    {
        return new SimpleKey(m_jobhandle);
    }


    /**
     * Saves the current state of the object to the database. If the object has not yet been
     * persisted, new records are inserted in the appropriate tables. If the object has been
     * persisted, then any changes are written to the backing tables. Only those values that have
     * changed are written, and if the state of the object is unchanged, the method does nothing.
     *
     * @param dbConn a <code>Connection</code> object that will be used to communicate with the
     *               database
     *
     * @throws IOException
     * @throws SQLException
     */
    @Override
    void save ( Connection dbConn ) throws IOException, SQLException
    {
        super.save(dbConn);

        // TODO: really this should be done after transaction is saved, but I think
        // we're OK since we don't keep using the object after and db exception is thrown.
        m_isNew = false;
    }


    /**
     * @param dbConn a <code>Connection</code> object that will be used to communicate with the
     *               database
     *
     * @throws IOException
     * @throws SQLException
     */
    @Override
    void delete ( Connection dbConn ) throws IOException, SQLException
    {
        super.delete(dbConn);

        m_jobhandle.reset();
        m_isNew = true;
    }


    // protected methods
    
    @Override
    protected void pushInsertOps ()
    {
        List<Column<?>> allColumns = new ArrayList<Column<?>>();

        allColumns.add(m_jobhandle);
        allColumns.addAll(m_columns);

        m_opQueue.push(new InsertOp(m_tableName, allColumns));
    }


    public static List<Statistics> findAllStatistics () throws IOException, SQLException
    {
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();
        PreparedStatement selectStmt = null;
        ResultSet statisticsRows = null;

        try
        {
            selectStmt = dbConn.prepareStatement("SELECT JOBHANDLE FROM " + TABLE_NAME);
            statisticsRows = selectStmt.executeQuery();

            List<Statistics> list = new ArrayList<Statistics>();

            while (statisticsRows.next())
            {
                list.add(new Statistics(dbConn, statisticsRows.getString(1)));
            }

            return list;
        }
        finally
        {
            if (statisticsRows != null)
            {
                statisticsRows.close();
            }

            if (selectStmt != null)
            {
                selectStmt.close();
            }

            dbConn.close();
        }
    }
    
    
    public static synchronized Timestamp 
    getAccountingPeriodStart ( boolean usePropFileValueFirst ) 
    {
        Timestamp acctPeriod = null; 
        
        // First, retrieve the value from the property file. 
        String propStringVal = Workbench.getInstance().getProperty("accounting.period.start", null);
        
        if (propStringVal == null) 
        {
            logger.error("Unable to retrieve accounting.period.start from workbench.properties.");
            logger.debug("Calculating AccountingPeriodStart from built-in algorithm ...");
        }
        else // If found, attemp to parse it.
        {
            try
            {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date date = sdf.parse(propStringVal);
                acctPeriod = new Timestamp(date.getTime());
            }
            catch ( ParseException pe )
            {
                acctPeriod = null; 
                logger.error("Unable to parse 'accounting.period.start' from workbench.properties.");
                logger.debug("Calculating AccountingPeriodStart from built-in algorithm ...");
            }
        }
        String propFrequencyStringVal = Workbench.getInstance().getProperty("accounting.period.frequency", null);
      
        if (propFrequencyStringVal != null && propFrequencyStringVal.equalsIgnoreCase(MONTHLY)) 
        {
           Calendar cal = Calendar.getInstance();
           cal.setTime(new Date());
           cal.set(Calendar.DAY_OF_MONTH, 1);
           cal.set(Calendar.HOUR_OF_DAY, 0);
           cal.set(Calendar.MINUTE, 0);
           cal.set(Calendar.SECOND, 0);
           acctPeriod = new Timestamp(cal.getTimeInMillis());
        }
        // If the property is not defined or parsing failed, 
        // calculate one automatically based on today's date. 
        if (acctPeriod == null)
        {
            Calendar current = Calendar.getInstance(); // get the current date;
            
            // If the current month is before June (inclusive), the accounting period started July 1st of last year. 
            // If the current month is after June (exclusive), the accounting period started July 1st of this year. 
            // i.e. 
            //      a) current date = 2018/04/22 ==> accounting period starts = 2017/07/01. 
            //      b) current date = 2018/09/15 ==> accounting period starts = 2018/07/01. 
            int year = (current.get(Calendar.MONTH) <= Calendar.JUNE)? 
                       (current.get(Calendar.YEAR) - 1) : current.get(Calendar.YEAR);
            
            // The month is always July. 
            Calendar periodStart = (new Calendar.Builder()).setDate(year, Calendar.JULY, 1).build();

            logger.debug("Auto calculated 'accounting.period.start': " 
                         + (new SimpleDateFormat("yyyy/MM/dd")).format(periodStart.getTime()));
            
            acctPeriod = new Timestamp(periodStart.getTimeInMillis());
        }
        
        logger.info("'accounting.period.start': " 
                         + (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")).format(acctPeriod));
        return acctPeriod; 
    }


    /**
     * <pre>
     * Get total SUs used by this user, since start of this accounting period, for XSEDE jobs.
     * For each job this user has submitted we use SU_OVERRIDE if not null. Otherwise we use SU_CHARGED if not null
     * (this value comes from tgusage db, but there's a delay in getting it) Otherwise we use SU_COMPUTED (which we calculate
     * from start.txt, done.txt, etc after job finishes).  Or if that isn't available, eg if the job is still running, we
     * use SU_PREDICTED.  If all three columns are null, eg, if we aren't able to predict sus of a running job, 0 is used as
     * the su's for that job.
     *
     * This only counts jobs that are charged to the cipres community account (cipresChargeNumber parameter).
     * All xsede jobs have a non null TG_CHARGE_NUMBER.  If you instead wanted to get sus for only non xsede jobs,
     * you could select the jobs where tg_charge_number is null or select for a specific resource.
     * </pre>
     *
     * @param userId
     *
     * @return
     *
     * @throws SQLException
     */
    public static long susPerUserThisPeriod ( long userId ) throws SQLException
    {
        long l = susPerUserThisPeriod(userId, ALL, cipresChargeNumber);
        return l;
    }


    public static long susPerUserThisPeriodNonXsede ( long userId ) throws SQLException
    {
        long l = susPerUserThisPeriod(userId, ALL, null);
        return l;
    }


    /**
     * <pre>
     * Get total SUs used by this user, since start of this accounting period, for XSEDE jobs.
     * Second argument is bit flag to specify which of PREDICTED, COMPUTED and CHARGED values
     * to use.  Combos can be added as needed, but currently valid combos are:
     *      PREDICTED | COMPUTED | CHARGED | OVERRIDE (ALL)
     *          charges for queued, running and completed jobs, allowing for override values.
     *
     *      COMPUTED | CHARGED
     *          charges for all completed jobs, excludes jobs that haven't finished yet.
     *
     *      CHARGED
     *          only actual charges that have shown up in tgdb for completed jobs
     *
     *      PREDICTED
     *          predicted SUs for all jobs that are queued or running, but not yet completed.
     *
     * TODO: Include SU_OVERRIDE
     * </pre>
     *
     * @param userId       the user id
     * @param include      the type of charge/charges to be included
     * @param chargeNumber the charge number
     *
     * @return
     *
     * @throws SQLException
     */
    public static long susPerUserThisPeriod ( long userId, int include, String chargeNumber ) throws SQLException
    {
        logger.info("BEGIN: susPerUserThisPeriod(long, int, String)::long");
        
        accountingPeriodStart = getAccountingPeriodStart(true); 
        
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

        PreparedStatement selectStmt = null;
        ResultSet statisticsRows = null;
        
        StringBuilder stmtBuilder = new StringBuilder();
        
        // Open of the SELECT statement. 
        stmtBuilder.append("SELECT SUM(");
        
        if ((include & ALL) == ALL) // predicted, computed and charged
        {
            stmtBuilder.append("COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0))))");
        }
        else if ((include & (COMPUTED | CHARGED)) == (COMPUTED | CHARGED))
        {
            stmtBuilder.append("COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, 0))");
        }
        else if ((include & CHARGED) == CHARGED)
        {
            stmtBuilder.append("COALESCE(SU_CHARGED, 0)");
        }
        else if ((include & PREDICTED) == PREDICTED)
        {
            stmtBuilder.append("COALESCE(SU_PREDICTED, 0)");
        }
        else
        {
            logger.error("Invalid 'include' argument");
            return 0L;
        }
        
        // Times the SU with Conversion Rate (non-null value only) 
        stmtBuilder.append(" * COALESCE(resource_conversion.CONVERSION, 1)");
        
        // Close of the SELECT statement. 
        stmtBuilder.append(")");
        
        // FROM statement 
        stmtBuilder.append(" FROM " + TABLE_NAME);
        
        // Left Join with the resource_conversion table. 
        stmtBuilder.append(" LEFT JOIN resource_conversion ON " + TABLE_NAME + ".RESRC_CONVRTN_ID = resource_conversion.ID ");
        
        stmtBuilder.append(" WHERE " + TABLE_NAME + ".DATE_ENTERED >= ? AND " + TABLE_NAME + ".USER_ID = ? ");
        
        if (chargeNumber == null)
        {
            stmtBuilder.append(" AND " + TABLE_NAME + ".TG_CHARGE_NUMBER IS NULL ");
        }
        else
        {
            stmtBuilder.append(" AND " + TABLE_NAME + ".TG_CHARGE_NUMBER = ?");
        }
        
        stmtBuilder.append(" GROUP BY " + TABLE_NAME + ".USER_ID ");

        try
        {
            logger.debug("SQL Stmt:\n" + stmtBuilder.toString());
            logger.debug(String.format("SQL Params: DateEntered=[%s] UserID=[%d] ChargeNum=[%s]", 
                                       ((new SimpleDateFormat("yyyy/MM/dd")).format(accountingPeriodStart)), 
                                       userId, chargeNumber));
            
            selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
            selectStmt.setTimestamp(1, accountingPeriodStart);
            selectStmt.setLong(2, userId);
            
            if (chargeNumber != null)
            {
                selectStmt.setString(3, chargeNumber);
            }
            
            long retval = 0;
            statisticsRows = selectStmt.executeQuery();
            
            if (statisticsRows != null && statisticsRows.next())
            {
                // column indices start with 1!
                retval = statisticsRows.getLong(1);
            }
            
            logger.debug(String.format("SQL Query Result=[%d]", retval));
            
            logger.info("END: susPerUserThisPeriod(long, int, String)::long");
            
            return retval;
        }
        finally
        {
            if (statisticsRows != null)
            {
                statisticsRows.close();
            }

            if (selectStmt != null)
            {
                selectStmt.close();
            }
            
            dbConn.close();
        }
    }

    public static List<Statistics.UserSuTransaction> transactionsPerUserThisPeriod ( long userId, int include, String chargeNumber,  Timestamp onOrAfter, Timestamp onOrBefore ) throws SQLException
    {
        logger.info("BEGIN: transactionsPerUserThisPeriod(long, int, String, Timestamp, Timestamp)::List<Statistics.UserSuTransaction>");
        
        accountingPeriodStart = getAccountingPeriodStart(true); 
        
        Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

        PreparedStatement selectStmt = null;
        ResultSet statisticsRows = null;
        
        StringBuilder stmtBuilder = new StringBuilder();
        
        // Open of the SELECT statement. 
        stmtBuilder.append("SELECT JOBHANDLE, DATE_ENTERED, ");
        
        if ((include & ALL) == ALL) // predicted, computed and charged
        {
            stmtBuilder.append("COALESCE(SU_OVERRIDE, COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, COALESCE(SU_PREDICTED, 0))))");
        }
        else if ((include & (COMPUTED | CHARGED)) == (COMPUTED | CHARGED))
        {
            stmtBuilder.append("COALESCE(SU_CHARGED, COALESCE(SU_COMPUTED, 0))");
        }
        else if ((include & CHARGED) == CHARGED)
        {
            stmtBuilder.append("COALESCE(SU_CHARGED, 0)");
        }
        else if ((include & PREDICTED) == PREDICTED)
        {
            stmtBuilder.append("COALESCE(SU_PREDICTED, 0)");
        }
        else
        {
            logger.error("Invalid 'include' argument");
            return null;
        }
        
        // Times the SU with Conversion Rate (non-null value only) 
        stmtBuilder.append(" * COALESCE(resource_conversion.CONVERSION, 1)");
        
        // Close of the SELECT statement. 
        //stmtBuilder.append(")");
        
        // FROM statement 
        stmtBuilder.append(" FROM " + TABLE_NAME);
        
        // Left Join with the resource_conversion table. 
        stmtBuilder.append(" LEFT JOIN resource_conversion ON " + TABLE_NAME + ".RESRC_CONVRTN_ID = resource_conversion.ID ");
        
        stmtBuilder.append(" WHERE " + TABLE_NAME + ".DATE_ENTERED >= ? AND " + TABLE_NAME + ".USER_ID = ? ");
        
        if (null != onOrBefore) {
            stmtBuilder.append(" AND DATE_ENTERED <= ?");
        }

        if (chargeNumber == null && cipresChargeNumber == null)
        {
            stmtBuilder.append(" AND " + TABLE_NAME + ".TG_CHARGE_NUMBER IS NULL ");
        }
        else
        {
            stmtBuilder.append(" AND " + TABLE_NAME + ".TG_CHARGE_NUMBER = ?");
        }

        stmtBuilder.append(" ORDER BY DATE_ENTERED DESC ");
 

        try
        {
            logger.debug("SQL Stmt:\n" + stmtBuilder.toString());
            logger.debug(String.format("SQL Params: DateEntered=[%s] UserID=[%d] ChargeNum=[%s]", 
                                       (null == onOrAfter)? ((new SimpleDateFormat("yyyy/MM/dd")).format(accountingPeriodStart)):((new SimpleDateFormat("yyyy/MM/dd")).format(onOrAfter)), 
                                       userId, chargeNumber));
            int index = 1; 
            selectStmt = dbConn.prepareStatement(stmtBuilder.toString());
            if (null != onOrAfter)
                selectStmt.setTimestamp(index++, onOrAfter);
            else
                selectStmt.setTimestamp(index++, accountingPeriodStart);
            selectStmt.setLong(index++, userId);
            if (null != onOrBefore)
                selectStmt.setTimestamp(index++, onOrBefore);
            
            if (!(chargeNumber == null && cipresChargeNumber == null))
            {
                if (chargeNumber != null)
                    selectStmt.setString(index++, chargeNumber);
                else
                    selectStmt.setString(index++, cipresChargeNumber);
            }
            
            List<Statistics.UserSuTransaction> userSuTransactions = new ArrayList<Statistics.UserSuTransaction>();
            String id;
            Timestamp transactionTime;
            long suAmount = 0;
            statisticsRows = selectStmt.executeQuery();
            
            if (statisticsRows != null) 
                while ( statisticsRows.next())
                {
                    // column indices start with 1!
                    id = statisticsRows.getString(1);
                    transactionTime = statisticsRows.getTimestamp(2);
                    suAmount = statisticsRows.getLong(3);
                    userSuTransactions.add(new Statistics.UserSuTransaction(id, null, transactionTime, (suAmount>0? -suAmount:suAmount), null)); 
                }
            
            
            logger.info("END: susPerUserThisPeriod(long, int, String)::long");
            
            return userSuTransactions;
        }
        finally
        {
            if (statisticsRows != null)
            {
                statisticsRows.close();
            }

            if (selectStmt != null)
            {
                selectStmt.close();
            }
            
            dbConn.close();
        }
    }

    static public class  UserSuTransaction 
    {
        private String id;
        private String suTransactionType = "Usage";
        private Timestamp transactionTime;
        private long suAmount = 0L;
        private String note = ""; 

        public UserSuTransaction(String jobHandle, String type, Timestamp dateEntered, long su, String note)
        {
            this.id = jobHandle;
            if (type != null)
                this.suTransactionType = type;
            this.transactionTime = dateEntered;
            //if (su > 0L)
            this.suAmount = su;
            if (note != null)
                this.note = note;
        }
        public String getId()
        {
            return id;
        }
        public String getSuTransactionType()
        {
            return suTransactionType;
        }
        public Timestamp getTransactionTime()
        {
            return transactionTime;
        }
        public long getSuAmount()
        {
            return suAmount;
        }
        public String getNote()
        {
            return note;
        }
    }
}
