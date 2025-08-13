package org.ngbw.web.actions;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.Workbench;

import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.jobs.UsageLimit;
//import org.ngbw.sdk.database.UserSuAllocationService;
//import org.ngbw.sdk.database.UserSuAllocationService.UserSuAllocation;
//import org.ngbw.sdk.database.UserSuAllocationService.UserSuBalance;
//import org.ngbw.sdk.tool.DateUtils;


/**
 * Struts action class to manage user tasks in the NGBW web application.
 *
 * @author mzhuang
 */
@SuppressWarnings("serial")
public class UserSuAllocationManager extends ManageTasks
{
    private static final Logger logger = Logger.getLogger(UserSuAllocationManager.class);
    
    public static final int MAX_TRANSACTIONS = 100;
    
    // session attribute key constants
    public static final String USER_SU_INFO = "userSuInfo";
    private static final String ONE = "1";
    private static final String TWO = "2";
    
    
    private long userId = 0;
    
    
    private Date dateRangeFrom = null;
    
    private Date dateRangeTo = null;
    
    
    private String userName = null;
    
    private String suAmount = null;   // from input form
    
    private String suExpDateStr = null; // from input form
    
    private String userSuSearchStr = ""; // from input form
    
    private String transTimeFrom = null;  // from input form
    
    private String transTimeTo = null; // from input form
    
    private String adjustExpDateAuto = null;
    
    private User targetUser = null;
    
    //private UserSuBalance suBalance = null;
    
    //private UserSuAllocation suAllocation = null;
    
    private List<Statistics.UserSuTransaction> suBalance = null;
    
    private boolean myselfSuInfo = false;
    private boolean userFound = false;
    private static ArrayList<String> months = new ArrayList<String>();
    private String selectedMonth = null;
    private long suHours = -1;
    private static SimpleDateFormat monthDate = new SimpleDateFormat("MMM, yyyy");
    
    static
    {
        //SimpleDateFormat monthDate = new SimpleDateFormat("MMM, yyyy");
        Calendar cal = Calendar.getInstance();
        for (int i = 1; i <= 12; i++) {
            String month_name1 = monthDate.format(cal.getTime());
            months.add(month_name1);
            cal.add(Calendar.MONTH, -1);
        }
    }

    public long 
    getUserId ()
    {
        return userId;
    }
    
    public long
    getTotalAward ()
    {
        return -1;
    }
    
    
    public long
    getTotalAdjustment ()
    {
        return -1;
    }
    
    
    public long 
    getTotalSubscription ()
    {
        return -1;
    }
    
    
    public long 
    getTotalUsage ()
    {
        return -1;
    }
    
    
    public Date 
    getDateRangeFrom ()
    {
        return dateRangeFrom;
    }
    
    
    public Date 
    getDateRangeTo ()
    {
        return dateRangeTo; 
    }
    
    
    public String 
    getUserName ()
    {
        return userName;
    }
    
    public void 
    setUserName (String userName)
    {
        this.userName = userName;
    }
    
    public String 
    getSuAmount ()
    {
        return suAmount;
    }
    
    
    public User
    getTargetUser ()
    {
        return targetUser;
    }
    
    public ArrayList<String>
    getMonths()
    {
        return months;
    }
    /* 
    public UserSuAllocation
    getSuAllocation ()
    {
        return suAllocation;
    }*/

    public String getSelectedMonth() {
        return selectedMonth;
    }

    public void setSelectedMonth(String selectedMonth) {
        this.selectedMonth = selectedMonth;
    } 
    
    public List<Statistics.UserSuTransaction>
    getSuBalance ()
    {
        return suBalance;
    }
    
    public String
    getAdjustExpDateAuto ()
    {
        return adjustExpDateAuto;
    }
//    /** @deprecated  */
//    public List<UserSuAllocation>
//    getSuAllocations () 
//    {
//        return suAllocations;
//    }
    
    
//    /** @deprecated  */
//    public List<UserSuAllocationTransaction>
//    getAllocationTransactions ()
//    {
//        return allocationTransactions;
//    }
    
    
    public void 
    setSuExpDateStr ( String str ) 
    {
        this.suExpDateStr = str;
    }
    
    
    public void 
    setUserSuSearchStr ( String str ) 
    {
        this.userSuSearchStr = str;
    }
    
    
    public void 
    setTransTimeFrom ( String str )
    {
        this.transTimeFrom = str;
    }
    
    
    public void 
    setTransTimeTo ( String str )
    {
        this.transTimeTo = str;
    }
    
    
    public void 
    setUserId ( long id ) 
    {
        this.userId = id;
    }
    
    
    public void 
    setSuAmount ( String amt )
    {
        this.suAmount = amt;
    }
    
    
    public boolean 
    hasSuAllocation ()
    {
        return userFound;
        //return (null != suAllocation);
    }
    
    public void
    setAdjustExpDateAuto (String adjustExpDateAuto)
    {
        this.adjustExpDateAuto = adjustExpDateAuto;
    }  
//    public boolean 
//    hasSuAllocations () 
//    {
//        return (null != suBalances && !suBalances.isEmpty()); 
//    }
    
    
//    public boolean 
//    hasActiveSuAllocation () throws IOException, SQLException
//    {
//        return (null != suAllocation);
//    }
    
    
//    /** @deprecated  */
//    public boolean 
//    hasAllocationTransactions ()
//    {
//        return (null != allocationTransactions && !allocationTransactions.isEmpty());
//    }
    
    public boolean getMyselfSuInfo()
    {
        return myselfSuInfo;
    }
    
    public void setMyselfSuInfo(boolean myselfSuInfo)
    {
        logger.debug("Setting MyselfSuInfo: " + myselfSuInfo);
        this.myselfSuInfo = myselfSuInfo;
    }
            
    @Override 
    public String 
    execute ()
    {
        return SUCCESS; 
    }
    
    
    private User 
    findUser ( String searchStr ) throws IOException, SQLException 
    {
        User user = null; 
        
        if (searchStr.contains("@")) {
            user = User.findUserByEmail(searchStr);
            if (null == user) {
                super.reportUserError(String.format(
                        "No user with email address '%s' can be found in the database.", searchStr));
            }
        }
        /*
        else if (StringUtils.hasPattern(searchStr, Pattern.compile("^[0-9]+$"))) {
            user = User.findUser(Long.parseLong(searchStr));
            if (null == user) {
                super.reportUserError(String.format(
                        "No user with ID '%s' can be found in the database.", searchStr));
            }
        }*/
        else {
            user = User.findUser(searchStr);
            if (null == user) {
                super.reportUserError(String.format(
                        "No user with username '%s' can be found in the database.", searchStr));
            }
        }
        
        return user; 
    }
    
    
    /**
     * Load the user info from the database and update local variables 
     * {@code userId}, {@code userName}, and {@code userSuSearchStr}.
     * 
     * @throws IOException
     * @throws SQLException 
     */
    private void 
    loadUserInfo () throws IOException, SQLException 
    {
        targetUser = new User(userId);
        if (targetUser != null) {
            this.userId = targetUser.getUserId();
            this.userName = targetUser.getUsername();
            this.userSuSearchStr = targetUser.getUsername();
        }
    }
    
    
    /**
     * Loads the user's last SU allocation (if any) from the database.
     * 
     * <p>
     * The allocation may already be expired.
     */
    private void 
    loadSuAllocation () 
    {
        /*
        try {
            // The SU Allocation may already be expired.
            suAllocation = UserSuAllocationService.getUserLastSuAllocation(userId, false);
            if (suAllocation != null) {
                logger.debug("UserSuAllocation loaded.");
            } else {
                logger.debug("UserSuAllocation is null.");
            }
        } 
        catch ( Throwable t ) {
            suAllocation = null;
            logger.error(t.getMessage(), t);
        }*/
    }
    
    
    private List<Statistics.UserSuTransaction> 
    loadSuBalance (final Timestamp from, final Timestamp to)
    {
        
        try {
                
            logger.debug("Loading UserSuBalance ...");
                
            suBalance = Statistics.transactionsPerUserThisPeriod(
                        userId, Statistics.ALL, null, from, to);
                
            logger.debug("UserSuBalance loaded.");
        } 
        catch ( Throwable t ) {
            suBalance = null;
            logger.error(t.getMessage(), t);
        }
        
        return suBalance;
    }
    
    
    /** 
     * @deprecated
     * Loads all user's SU allocations (if any) from the database.
     * 
     * <p>
     * Some or all of the allocations may already be expired.
     */
    private void 
    loadSuAllocations () 
    {
//        try {
//            // The SU Allocation may already be expired.
//            suAllocations = UserSuAllocationService.getUserSuAllocations(userId, false);
//            if (suAllocations != null) {
//                logger.debug(String.format("SU Allocations loaded; Count=[%d].", suAllocations.size()));
//            }
//        } 
//        catch ( Throwable t ) {
//            suAllocations = null;
//            logger.error(t.getMessage(), t);
//        }
    }
    
    
    /** 
     * @deprecated
     * Loads all user's SU transactions of the last allocation.
     */
    private void
    loadAllocationTransactions ()
    {
//        try {
//            final UserSuAllocation allocation = UserSuAllocationService.getUserLastSuAllocation(userId, false);
//            if (allocation != null) {
//                allocationTransactions = UserSuAllocationService.getUserSuTransactions(
//                        userId, null,
//                        allocation.getStartTime(),
//                        allocation.getSuExpireTime(),
//                        0, 0);
//            } else {
//                allocationTransactions = null;
//            }
//        }
//        catch ( Throwable t ) {
//            allocationTransactions = null;
//            logger.error(t.getMessage(), t);
//        }
    }
    
    
    /** 
     * @deprecated
     * Calculates various values related to SU allocations, transactions, and job runs.
     */
    private void 
    calculateSummaries ()
    {
//        if (suAllocations == null) {
//            loadSuAllocations(userId);
//        }
        
//        if (allocationTransactions == null) {
//            loadAllocationTransactions(userId);
//        }
        
//        if (allocationTransactions != null && !allocationTransactions.isEmpty()) {
//            for (UserSuAllocationTransaction transaction : allocationTransactions) {
//                if (transaction.getSuTransactionType().equals(SuTransactionType.ADJUSTMENT)) {
//                    this.totalAdjustment += transaction.getSuAmount();
//                } else if (transaction.getSuTransactionType().equals(SuTransactionType.AWARD)) {
//                    this.totalAward += transaction.getSuAmount();
//                } else if (transaction.getSuTransactionType().equals(SuTransactionType.PURCHASE)) {
//                    this.totalSubscription += transaction.getSuAmount();
//                }
//            }
//        }
        
//        try {
//            final UserSuAllocation allocation = UserSuAllocationService.getUserLastSuAllocation(userId, false);
//            if (allocation != null) {
//                this.dateRangeFrom = allocation.getStartTime();
//                this.dateRangeTo = allocation.getSuExpireTime();
//                this.totalUsage += Statistics.susPerUser(
//                        userId, allocation.getStartTime(), allocation.getSuExpireTime());
//            }
//        }
//        catch ( Throwable t ) {
//            logger.error(t.getMessage(), t);
//        }
    }
    
    
    private void 
    setTransactionTimeRanges ( final Date startTime ) 
    {
        logger.debug(String.format("'transTimeFrom' from input form = [%s]", transTimeFrom));

        /*
        if (!StringUtils.isNullOrEmpty(this.transTimeFrom, true)) {
            try {
                this.dateRangeFrom = DateUtils.getDate(this.transTimeFrom);
            } catch ( ParseException pe ) {
                this.dateRangeFrom = null;
                logger.error(pe.getMessage(), pe);
            }
        }

        logger.debug(String.format("'transTimeTo' from input form = [%s]", transTimeTo));

        if (!StringUtils.isNullOrEmpty(this.transTimeTo, true)) {
            try {
                this.dateRangeTo = DateUtils.getDate(this.transTimeTo);
            } catch ( ParseException pe ) {
                this.dateRangeTo = DateUtils.getDate();
                logger.error(pe.getMessage(), pe);
            }
        } else {
            this.dateRangeTo = DateUtils.getDate();
        }
        
        if (this.dateRangeFrom == null) {
            this.dateRangeFrom = StringUtils.max(
                DateUtils.addDays(this.dateRangeTo, -180), startTime);
        }*/
    }
    
    
    @SkipValidation
    public String 
    searchMyselfSuInfo () throws Throwable 
    {
        myselfSuInfo = true;
        logger.debug("BEGIN: searchMyselfSuInfo()::String");
        return searchUserSuInfoBase(true);
    }
    
    // Mapped to "userSuInfoSearch" action.
    @SkipValidation
    public String
    searchUserSuInfo () throws Throwable
    {
        myselfSuInfo = false;
        return searchUserSuInfoBase(false);
    }
   
   
    private String
    searchUserSuInfoBase (boolean myself) throws Throwable
    {

        logger.debug("BEGIN: searchUserSuInfo()::String");
        logger.info("searchUserSuInfoBase():  myself = " + myself);

        String resultName = SUCCESS;

        try {
            if (myself || !StringUtils.isNullOrEmpty(userSuSearchStr, true)) {
                logger.debug(String.format("Searching for user [%s] ...", userSuSearchStr));
                targetUser = null;
                if (myself)
                {
                    targetUser = super.getAuthenticatedUser();
                    logger.info("Show SU inof of myself: " + targetUser.getUsername());
                }
                else
                    targetUser = this.findUser(userSuSearchStr.trim());

                if (null != targetUser) {
                    this.userId = targetUser.getUserId();
                    this.userName = targetUser.getUsername();
                    this.userSuSearchStr = targetUser.getUsername();
                    
                    logger.debug(String.format("User found :: [%d - %s].", userId, userName));
                    this.userFound = true; 

                    this.loadSuAllocation();
                    this.setTransactionTimeRanges(null);
                    this.loadSuBalance(null, null);
                } 
                else {
                    logger.debug(String.format("Searching for user [%s] ... NOT found!", userSuSearchStr));
                }
            }
            else {
                resultName = INPUT;
                //super.reportUserError("Please enter a valid user's ID, UUID, username, or email address.");
            }
        }
        catch ( Throwable t ) {
            logger.error(t.getMessage(), t);
            resultName = ERROR;
            super.reportUserError("Unable to retrieve user's SU information.");
        }
        
        this.printLocalVarValues();
        
        logger.debug("END: searchUserSuInfo()::String");

        return resultName;
    }
    
    @SkipValidation
    public String
    submitUserSuLimit () throws Throwable
    {
        logger.debug(String.format("User found for submitUserSuLimit :: [%d - %s].", userId, userName));
        if (userName != null && userId > 0) 
        {
           if (targetUser == null)
               targetUser = new User(userId);
           this.userFound = true;
           this.loadSuAllocation();
           this.setTransactionTimeRanges(null);
           this.loadSuBalance(null, null);
        }
        return SUCCESS;
    }

    @SkipValidation
    public String
    monthSelectUserSuInfo () throws Throwable
    {
        myselfSuInfo = false;
                    //this.userId = targetUser.getUserId();
                    //this.userName = targetUser.getUsername();
                    //this.userSuSearchStr = targetUser.getUsername();

       logger.debug(String.format("User found :: [%d - %s].", userId, userName));
       if (userName != null && userId > 0) 
       {
           logger.debug(String.format("User found :: [%d - %s].", userId, userName));
           targetUser = new User(userId);
           this.userFound = true;
           Calendar cal = Calendar.getInstance();
           if (selectedMonth != null && !selectedMonth.isEmpty())
           {
                cal.setTime(monthDate.parse(selectedMonth));
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                this.loadSuAllocation();
                this.setTransactionTimeRanges(cal.getTime());
                Timestamp  from = new Timestamp(cal.getTimeInMillis());
                cal.add(Calendar.MONTH, 1);
                cal.add(Calendar.DAY_OF_YEAR, -1);
                Timestamp  to = new Timestamp(cal.getTimeInMillis());
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd mm:ss");
                String fromStr = formatter.format(from);
                String toStr = formatter.format(to);
                logger.info("loadBalance(from, to) from = " + fromStr + ",  to = " + toStr); 

                List<Statistics.UserSuTransaction> tranList = this.loadSuBalance(from, to);
                suHours = 0L;
                for (Statistics.UserSuTransaction t : tranList)
                {
                     logger.info("loadBalance(from, to) tranaction id = " + t.getId() + ",  transactionTime  = " + formatter.format(t.getTransactionTime()) + ", suAmount = " + t.getSuAmount()); 
                     suHours += t.getSuAmount();
                }
                suHours = Math.abs(suHours);
            }
       }
       return SUCCESS;
    }

    @SkipValidation
    public long 
    getSUHours () throws Throwable 
    {
        if (suHours < 0L)
        	return Statistics.susPerUserThisPeriod(userId); 
        else
            return suHours;
    }

    @SkipValidation
    public long 
    getUserXSEDELimit() throws Throwable
    {
        if (targetUser != null) 
            return UsageLimit.getInstance().getXSEDELimit(targetUser, null);
        return 0L;
    }

    @SkipValidation
    public void 
    setUserXSEDELimit(long limit) throws Throwable
    {
        logger.debug(String.format("User found for setUserXSEDELimit :: [%d - %s].", userId, userName));
        if (targetUser == null) 
            targetUser = new User(userId);
        UsageLimit.getInstance().setXSEDELimit(targetUser, null, limit);
    }

    @SkipValidation
    public String 
    getSuAllocationExpireTimeWithMonth() throws Throwable 
    {
       if (selectedMonth != null && !selectedMonth.isEmpty())
       {
           Calendar calendar = Calendar.getInstance();
           calendar.setTime(monthDate.parse(selectedMonth));
           int day = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
           calendar.set(Calendar.DAY_OF_MONTH, day);
           calendar.set(Calendar.MINUTE, 59);
           calendar.set(Calendar.HOUR_OF_DAY, 23);
           SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yy HH:mm");
           return dateFormat.format(calendar.getTime());
       }
       else
           return super.getSuAllocationExpireTime();
    }

    @SkipValidation
    public String 
    adjustUserSu () throws Throwable 
    {
        logger.debug("BEGIN: adjustUserSu()::String");

        String resultName = SUCCESS;

        logger.debug(String.format(
                "Attempting to adjust SU Allocation amount [%s] for user [%s] ...",
                suAmount, userId));
        /* 
        try {
            if (userId <= 0) {
                resultName = ERROR;
                super.reportUserError("Please enter a valid user's ID.");
            }
            else if (StringUtils.isNullOrEmpty(suAmount, true)) {
                resultName = ERROR;
                super.reportUserError("Please enter an integer for SU amount.");
            }
            else if (!StringUtils.hasPattern(suAmount.trim(), Pattern.compile("^(-)?[0-9]+$"))) {
                resultName = ERROR;
                super.reportUserError("Please enter an integer for SU amount.");
            }
            else {
                this.loadUserInfo();
                if (targetUser != null) {
                    final UserSuAllocation suAllocation = UserSuAllocationService
                            .getUserLastSuAllocation(targetUser.getUserId(), true);

                    if (null != suAllocation) {
                        UserSuAllocationService.adjustSu(
                                targetUser, Integer.parseInt(suAmount), super.getAuthenticatedUser());
                        
                        if (adjustExpDateAuto != null && adjustExpDateAuto.equals(ONE))
                        {
                            final Date currExpDate = suAllocation.getSuExpireTime();
                            Calendar currExp = Calendar.getInstance();
                            currExp.setTime(currExpDate);
                            Calendar today = Calendar.getInstance();
                            today.set(Calendar.HOUR_OF_DAY, currExp.get(Calendar.HOUR_OF_DAY));
                            today.set(Calendar.MINUTE, currExp.get(Calendar.MINUTE));
                            today.set(Calendar.SECOND, currExp.get(Calendar.SECOND));
                            Date expireTime = DateUtils.addDays(today.getTime(),  Workbench.getInstance().getAllocationsValidDays(365));
                            
                            UserSuAllocation suAllocation2 = UserSuAllocationService.getUserLastSuAllocation(targetUser.getUserId(), true);
                            suAllocation2.setSuExpireTime(expireTime);
                            UserSuAllocationService.updateUserSuAllocation(suAllocation2);
                        }
                        logger.debug(String.format(
                                "SU [%d] adjusted to user [%d - %s] overall SU allocations.",
                                Integer.parseInt(suAmount), userId, userName));
                        
                        super.reportUserMessage("User's SU has been adjusted.");
                        
                        suAmount = null;
                    }
                    else {
                        logger.debug(String.format(
                                "User [%d - %s] does not have active SU allocation/subscription record.",
                                userId, userName));
                        
                        super.reportUserError(String.format(
                                "User '%s' does not have active SU allocation/subscription record.",
                                targetUser.getUsername()));
                    }
                }
                else {
                    resultName = ERROR;
                    logger.error(String.format("User ID '%d' not found.", userId));
                    super.reportUserError(String.format("User ID '%d' not found.", userId));
                }
            }
        }
        catch ( Throwable t ) {
            logger.error(t.getMessage(), t);
            resultName = ERROR;
            super.reportUserError("Unable to adjust user's SU.");
        }
        finally {
            if (null != targetUser) {
                this.loadSuAllocation();
                this.setTransactionTimeRanges(null);
                this.loadSuBalance();
            }
        }
        
        this.printLocalVarValues();
        */ 
        logger.debug("END: adjustUserSu()::String");

        return resultName;
    }
    
    
    @SkipValidation
    public String 
    adjustUserSuExpDate () throws Throwable 
    {
        logger.debug("BEGIN: adjustUserSuExpDate()::String");

        String resultName = SUCCESS;

        logger.debug(String.format(
                "Attempting to adjust SU Subscription Exp Date to [%s] for user [%s]...",
                suExpDateStr, userId));
        /* 
        try {
            if (userId <= 0) {
                resultName = ERROR;
                super.reportUserError("Please enter a valid user's ID.");
            }
            else if (StringUtils.isNullOrEmpty(suExpDateStr, true)) {
                resultName = ERROR;
                super.reportUserError("Please enter a date in correct format.");
            }
            else {
                this.loadUserInfo();
                if (targetUser != null) {
                    String expDateStr = suExpDateStr.trim(); // remove leading and trailing spaces.
                    expDateStr = expDateStr.replaceAll("\\s+", " "); // remove all extra spaces.
                    expDateStr = expDateStr.replaceAll("[\\/\\.]", "-"); // replace '/' and '.' with '-'

                    // Parse the date string.
                    boolean dateOnly = true;
                    String dateFormat = null;

                    // i.e. '2021-10-15'
                    if (StringUtils.hasPattern(expDateStr, Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) {
                        dateOnly = true;
                        dateFormat = "yyyy-MM-dd";
                    } 

                    // i.e. '10-15-2021'
                    else if (StringUtils.hasPattern(expDateStr, Pattern.compile("^[0-9]{2}-[0-9]{2}-[0-9]{4}$"))) {
                        dateOnly = true;
                        dateFormat = "MM-dd-yyyy";
                    }

                    // i.e. '2021-10-15 13:20:45'
                    else if (StringUtils.hasPattern(
                            expDateStr, Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2}$"))) {
                        dateOnly = false;
                        dateFormat = "yyyy-MM-dd HH:mm:ss";
                    }

                    // i.e. '10-15-2021 13:20:45'
                    else if (StringUtils.hasPattern(
                            expDateStr, Pattern.compile("^[0-9]{2}-[0-9]{2}-[0-9]{4}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2}$"))) {
                        dateOnly = false;
                        dateFormat = "MM-dd-yyyy HH:mm:ss";
                    }

                    // i.e. '2021-10-15T13:20:45'
                    else if (StringUtils.hasPattern(
                            expDateStr, Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}$"))) {
                        dateOnly = false;
                        dateFormat = "yyyy-MM-dd'T'HH:mm:ss";
                    }

                    // i.e. '10-15-2021T13:20:45'
                    else if (StringUtils.hasPattern(
                            expDateStr, Pattern.compile("^[0-9]{2}-[0-9]{2}-[0-9]{4}T[0-9]{2}:[0-9]{2}:[0-9]{2}$"))) {
                        dateOnly = false;
                        dateFormat = "MM-dd-yyyy'T'HH:mm:ss";
                    }
                    
                    logger.debug(String.format(
                            "Subscription exp date str [%s] to be parsed with format [%s] for user [%d - %s].", 
                            expDateStr, dateFormat, targetUser.getUserId(), targetUser.getUsername()));

                    Date expDate = null;

                    if (!StringUtils.isNullOrEmpty(dateFormat, true)) {
                        expDate = DateUtils.getDate(dateFormat, expDateStr, Locale.US);
                    }

                    if (expDate == null) {
                        throw new RuntimeException("Unable to parse the date string: " + suExpDateStr);
                    }

                    logger.debug(String.format(
                            "Parsed subscription exp date for user [%d - %s] is [%s].", 
                            targetUser.getUserId(), targetUser.getUsername(),
                            DateUtils.formatDate("yyyy/MM/dd HH:mm:ss", expDate)));

                    UserSuAllocation allocation = UserSuAllocationService
                            .getUserLastSuAllocation(targetUser.getUserId(), false);

                    if (allocation == null) {
                        throw new RuntimeException(
                                "Unable to find SU allocation for user " + targetUser.getUsername());
                    } 
//                    else if (allocation.isExpired()) {
//                        throw new RuntimeException("SU allocation of the user " 
//                                + targetUser.getUsername() + " has already expired.");
//                    }

                    if (dateOnly) {
                        logger.debug("Subscription exp date does not have time portion.");
                        expDate = DateUtils.setTimeToEndOfDay(expDate); // Set the time to 23:59:59.
                    }

                    logger.debug(String.format(
                            "Final subscription exp date to be set for user [%d - %s] is [%s].", 
                            targetUser.getUserId(), targetUser.getUsername(),
                            DateUtils.formatDate("yyyy/MM/dd HH:mm:ss", expDate)));

                    final Date currExpDate = allocation.getSuExpireTime();

                    allocation.setSuExpireTime(expDate);
                    UserSuAllocationService.updateUserSuAllocation(allocation);
                    allocation = UserSuAllocationService.getUserLastSuAllocation(targetUser.getUserId(), false);

                    final String msg = String.format(
                            "Expiration date of SU subscription of the user '%s' has been updated to %s.",
                            targetUser.getUsername(),
                            DateUtils.formatDate("yyyy/MM/dd HH:mm:ss", allocation.getSuExpireTime()));

                    logger.info(msg);
                    super.reportUserMessage(msg);
                }
                else {
                    resultName = ERROR;
                    logger.error(String.format("User ID '%d' not found.", userId));
                    super.reportUserError(String.format("User ID '%d' not found.", userId));
                }
            }
        }
        catch ( Throwable t ) {
            logger.error(t.getMessage(), t);
            resultName = ERROR;
            super.reportUserError("Unable to adjust user's subscription expiration date.");
        }
        finally {
            if (null != targetUser) {
                this.loadSuAllocation();
                this.setTransactionTimeRanges(null);
                this.loadSuBalance();
            }
        }
        
        this.printLocalVarValues();
        */ 
        logger.debug("END: adjustUserSuExpDate()::String");

        return resultName;
    }
    
    
    @SkipValidation
    public String 
    filterUserSuTransactions () throws Throwable 
    {
        logger.debug("BEGIN: filterUserSuTransactions()::String");

        String resultName = SUCCESS;

        try {
            if (!myselfSuInfo && userId <= 0) {
                resultName = ERROR;
                super.reportUserError("Please enter a valid user's ID.");
            }
            else {
                if (myselfSuInfo)
                {
                    User targetUser = super.getAuthenticatedUser();
                    userId = targetUser.getUserId();
                    logger.info("Filter SU inof of myself: " + targetUser.getUsername());
                }
                this.loadUserInfo();
                this.loadSuAllocation();
                this.setTransactionTimeRanges(null);
                
                //logger.debug(String.format(
                //       "Retrieving Alloc Trans for user [%s] filter [%s] - [%s] ...",
                //        targetUser.getUsername(),
                //        DateUtils.formatDate(this.dateRangeFrom),
                //       DateUtils.formatDate(this.dateRangeTo)));

                this.loadSuBalance(null, null);
            }
        }
        catch ( Throwable t ) {
            logger.error(t.getMessage(), t);
            resultName = ERROR;
            super.reportUserError("Unable to retrieve user's SU information.");
        }

        logger.debug("END: filterUserSuTransactions()::String");

//        this.searchUserSuInfo();

        return resultName;
    }
    
    
    public String 
    filterUserSuTransactions_ () throws Throwable 
    {
        return SUCCESS;
    }
    
    
    private void 
    printLocalVarValues ()
    {
        /*
        logger.debug(String.format(">>>\n" +
                "Local vars -> userId[%d] userName[%s], userSuSearchStr[%s] suAmount[%s] \n" + 
                "suExpDateStr[%s] transTimeFrom[%s] transTimeTo[%s] \n" + 
                "dateRangeFrom[%s] dateRangeTo[%s].",
                userId, userName, userSuSearchStr, suAmount, 
                suExpDateStr, transTimeFrom, transTimeTo,
                (dateRangeFrom == null ? "" : DateUtils.formatDate("yyyy/MM/dd HH:mm:ss", dateRangeFrom)),
                (dateRangeTo == null ? "" : DateUtils.formatDate("yyyy/MM/dd HH:mm:ss", dateRangeTo)))); */
    }
    
    
    /*
    private void
    printUserSuBalanceInfo ( final UserSuBalance suBalance )
    {
        
        logger.debug(String.format(
            "UserSuBalance: >>>\n"
            + "GrandTotal[%d], Award[%d] Adjustment[%d] Subscription[%d] Usage[%d] Remaining[%d]",
            suBalance.getGrandTotal(), suBalance.getTotalAward(),
            suBalance.getTotalAdjustment(),
            suBalance.getTotalSubscription(), suBalance.getTotalUsage(),
            suBalance.getSuRemaining())); 
    }*/

}
