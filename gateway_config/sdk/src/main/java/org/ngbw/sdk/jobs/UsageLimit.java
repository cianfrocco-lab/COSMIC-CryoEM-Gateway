package org.ngbw.sdk.jobs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.ngbw.sdk.tool.Tool;
import org.ngbw.sdk.UsageLimitException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.database.Application;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.database.User;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.common.util.StringUtils;


/** 
 * <pre>
 * Default limits are set in workbench.properties via cipres-*.properties values. Default limits can
 * be overridden with application preferences. Those can be overriden with any preferences set on
 * user. You only set props on app or on user to override a value. Set property value to null if you
 * want to remove that limit (i.e make it unlimited). Set NO_LIMITS=1 to remove all limits for a
 * user or app.
 *
 * Example 1: 
 * default:       xsede_su_limit = 10 
 * pycipres app:  xsede_su_limit = null 
 * pycipres user: no limits set 
 * EFFECTIVE:     xsede_su_limit is unlimited.
 *
 * Example 2: 
 * default:       xsede_su_limit = 10 
 * pycipres app:  xsede_su_limit = null 
 * pycipres user: xsede_su_limit = 5 
 * EFFECTIVE:     xsede_su_limit is 5.
 *
 * Example 3: 
 * default:      xsede_su_limit = 10 
 * pycipres app: no_limits = 1, xsede_su_limit=2 
 * EFFECTIVE:    Everything is unlimited (except concurrent requests)
 * 
 * Example 4: 
 * default:       xsede_su_limit = 10 
 * pycipres app:  no_limits = 1 
 * pycipres user: no_limits = 0
 * EFFECTIVE:     Default limits are applied 
 * 
 * CONCURRENT_LIMIT This is number of concurrent requests allowed per user. Unlike other limits it
 * can't be set on a per-user basis because I don't want the overhead of that more complicated (and
 * synchronized) lookup on each request. The other limits only have to do with job submissions, but
 * this applies to everything.
 * 
 * When you change limits for a user or application, in the database, you must restart tomcat for
 * them to take effect. The limits are cached in this class after being read once.
 * </pre>
 */
public enum UsageLimit
{
    // this is a singleton
    INSTANCE;

    // Note that log cannot be used in the ctor, it won't be initialized yet.
    private static final Log logger = LogFactory.getLog(UsageLimit.class.getName());
    boolean didDebug = false;
    
    public static final String XSEDE_US_SU_LIMIT = "xsede_us_su_limit";
    public static final String XSEDE_NONUS_SU_LIMIT = "xsede_nonus_su_limit";
    public static final String OTHER_US_SU_LIMIT = "other_us_su_limit";
    public static final String OTHER_NONUS_SU_LIMIT = "other_nonus_su_limit";
    
    public static final String NO_LIMITS = "no_limits";
    public static final String ACTIVE_LIMIT = "active_limit";
    public static final String CONCURRENT_LIMIT = "concurrent_limit";
    public static final String SUBMITTED_TODAY_LIMIT = "submitted_today_limit";
    
    private static final String[] names =
    {
        XSEDE_US_SU_LIMIT, XSEDE_NONUS_SU_LIMIT, 
        OTHER_US_SU_LIMIT, OTHER_NONUS_SU_LIMIT, 
        ACTIVE_LIMIT, CONCURRENT_LIMIT, SUBMITTED_TODAY_LIMIT, NO_LIMITS 
    };
    
    private static final List<String> namesList = Arrays.asList(names);


    public static List getLimitNames ()
    {
        return namesList;
    }


    public Map<String, String> getDefaultLimits ()
    {
        return new HashMap<String, String>(defaultProps);
    }


    /*
     * Used to return info, eg in UsageLimitException, about the specific limit that was hit. Reset
     * is time in minutes until the limit will be reset. null value means not applicable.
     */
    public static class LimitStatus
    {
        public String type;
        public Long ceiling;
        public Long remaining;
        public Long resetMinutes;
        public Long resetDate;


        public LimitStatus ()
        {}


        public LimitStatus ( String type, long ceiling )
        {
            this.type = type;
            this.ceiling = ceiling;
        }

    }

    private static class Limits
    { 
        // Look these up for a user once and cache them;  Means app has to be restarted to pick up changes.
        public boolean unlimited;
        public Long xsede_us_su_limit;
        public Long xsede_nonus_su_limit; 
        public Long other_us_su_limit;
        public Long other_nonus_su_limit;
        public Long active_limit;
        public Long submitted_today_limit;
        
        public Limits ()
        {
            unlimited = false;
            xsede_us_su_limit = null;
            xsede_nonus_su_limit = null;
            other_us_su_limit = null;
            other_nonus_su_limit = null; 
            active_limit = null;
            submitted_today_limit = null;
        }
        
        public boolean isUnlimited () 
        {   return unlimited;       }
        
        public Long getXsedeUsSuLimit () 
        {   return xsede_us_su_limit;      }
        
        public Long getXseduNonUsSuLimit () 
        {   return xsede_nonus_su_limit;   }
        
        public Long getOtherUsSuLimit ()
        {   return other_us_su_limit;      }
        
        public Long getOtherNonUsSuLimit ()
        {   return other_nonus_su_limit;      }
        
        public Long getActiveLimit () 
        {   return active_limit;    }
        
        public Long getSubmittedTodayLimit () 
        {   return submitted_today_limit;   }
        
        String asString ()
        {
            return 
                String.format("unlimited=[%s], "
                              + "xsede_us_su_limit=[%d], "
                              + "xsede_nonus_su_limit=[%d], "
                              + "other_us_su_limit=[%d], "
                              + "other_nonus_su_limit=[%d], "
                              + "active_limit=[%d], "
                              + "submitted_today_limit=[%d]", 
                              Boolean.toString(unlimited), 
                              xsede_us_su_limit, xsede_nonus_su_limit, 
                              other_us_su_limit, other_nonus_su_limit, 
                              active_limit, submitted_today_limit); 
        }
    }

    
    // A list of User and UserPreferences, format: Map<UserID, Map<preference, value>>
    // See also User.preferences(). 
    private Map<Long, Map<String, String>> usersAndPreferences;
    
    // A list of Application and ApplicationPreferences, format: Map<AppName, Map<preference, value>>
    // See also Application.preferences(). 
    private Map<String, Map<String, String>> applicationsAndPreferences;
    
    private Map<String, String> defaultProps;
    
    private HashMap<Long, Long> userRequestCount = new HashMap<Long, Long>();
    
    private Long concurrentLimit = null;


    private UsageLimit ()
    {
        initialize();
    }


    /**
     * TODO: reinitialize when requested without having to restart app. With tomcat, look at JMX.
     * This would need to be synchronized if called outside of ctor. Setting concurrentLimit would
     * need to be synchronized on same object as testAndIncrement and decrement methods.
     *
     * DON'T USE LOGGER IN HERE
     */
    private void initialize ()
    {
        Properties wbp = Workbench.getInstance().getProperties();

        defaultProps = new HashMap<String, String>();
        defaultProps.put(XSEDE_US_SU_LIMIT, wbp.getProperty(XSEDE_US_SU_LIMIT));
        defaultProps.put(XSEDE_NONUS_SU_LIMIT, wbp.getProperty(XSEDE_NONUS_SU_LIMIT));
        defaultProps.put(OTHER_US_SU_LIMIT, wbp.getProperty(OTHER_US_SU_LIMIT));
        defaultProps.put(OTHER_NONUS_SU_LIMIT, wbp.getProperty(OTHER_NONUS_SU_LIMIT));
        
        defaultProps.put(ACTIVE_LIMIT, wbp.getProperty(ACTIVE_LIMIT));
        defaultProps.put(CONCURRENT_LIMIT, wbp.getProperty(CONCURRENT_LIMIT));
        defaultProps.put(SUBMITTED_TODAY_LIMIT, wbp.getProperty(SUBMITTED_TODAY_LIMIT));
        
        usersAndPreferences = new HashMap<Long, Map<String, String>>();
        applicationsAndPreferences = new HashMap<String, Map<String, String>>();

        Long l;
        
        try
        {
            if ((l = StringUtils.string2Long(wbp.getProperty(CONCURRENT_LIMIT))) != null)
            {
                concurrentLimit = l;
            }
        }
        catch ( Exception e )
        {
            logger.error(e.getMessage(), e);
        }
    }


    public static UsageLimit getInstance ()
    {
        return INSTANCE;
    }


    public synchronized void logDefaults ()
    {
        logger.info("BEGIN: logDefaults()::void");
        
        // Dump default properties here since we can't do it in the ctor.
        if (didDebug == false)
        {
            didDebug = true;
        
            logger.info(String.format("Default UsageLimits are: [%s]", StringUtils.map2String(defaultProps)));
            
            if (concurrentLimit != null)
            {
                logger.info(String.format("Number of concurrent requests allowed per user is [%d]", concurrentLimit));
            }
            else
            {
                logger.warn(String.format("Number of concurrent requests allowed per user is NOT SET. " 
                                            + "See the property [%s] in workbench.properties file.", CONCURRENT_LIMIT));
            }
        }
        
        logger.info("END: logDefaults()::void");
    }


    // ***** here is good place to check US/NonUS user. 
    private synchronized Map<String, String> getProperties ( User user, Application app ) throws Exception
    {
        logger.info("BEGIN: getProperties(User, Application)::Map<String, String>");
        
        logDefaults();
        
        // Load user's preferences. 
//        if (usersAndPreferences.get(user.getUserId()) == null)
        {
            usersAndPreferences.put(user.getUserId(), user.preferences());
            logger.debug("User's preferences loaded.");
        }
        
        Map<String, String> ourProps = new HashMap(defaultProps);
        
        if (app != null)
        {
            logger.debug(String.format("Application name: ", app.getName()));
            
//            if (applicationsAndPreferences.get(app.getName()) == null)
            {
                applicationsAndPreferences.put(app.getName(), app.preferences());
                logger.debug("Application's preferences loaded.");
            }
//            else
//            {
//                //log.debug("cached app preferences: " + StringUtils.map2String(app.preferences()));
//            }
            
            ourProps.putAll(applicationsAndPreferences.get(app.getName()));
        }

//        Map<String, String> ourProps = new HashMap(defaultProps);
//        ourProps.putAll(appMap.get(app.getName()));
       
        
        //log.debug("Merged in app preferences: " + appMap.get(app.getName()));
        //log.debug("Merged Usage Limits are now " + StringUtils.map2String(ourProps));

        ourProps.putAll(usersAndPreferences.get(user.getUserId()));
        
        logger.debug(String.format("Merged UsageLimits for User=[%s] are now [%s]", user.getUsername(), StringUtils.map2String(ourProps)));
        
        logger.info("END: getProperties(User, Application)::Map<String, String>");

        return ourProps;
    }


    private Limits propertiesToLimits ( Map<String, String> p ) throws Exception
    {
        Limits limits = new Limits();
        
        Long l;
        
        Boolean b = StringUtils.string2Boolean(p.get(NO_LIMITS));
        if (b != null)
        {
            limits.unlimited = b;
            //return limits;
        }
        
        if ((l = StringUtils.string2Long(p.get(XSEDE_US_SU_LIMIT))) != null)
        {
            limits.xsede_us_su_limit = l;
        }
        
        if ((l = StringUtils.string2Long(p.get(XSEDE_NONUS_SU_LIMIT))) != null)
        {
            limits.xsede_nonus_su_limit = l;
        }
        
        if ((l = StringUtils.string2Long(p.get(OTHER_US_SU_LIMIT))) != null)
        {
            limits.other_us_su_limit = l;
        }
        
        if ((l = StringUtils.string2Long(p.get(OTHER_NONUS_SU_LIMIT))) != null)
        {
            limits.other_nonus_su_limit = l;
        }
        
        if ((l = StringUtils.string2Long(p.get(ACTIVE_LIMIT))) != null)
        {
            limits.active_limit = l;
        }
        
        if ((l = StringUtils.string2Long(p.get(SUBMITTED_TODAY_LIMIT))) != null)
        {
            limits.submitted_today_limit = l;
        }
        
        logger.debug("Effective limits are: " + limits.asString());
        
        return limits;
    }


    public Limits getLimits ( User user, Application app ) throws Exception
    {
        return propertiesToLimits(getProperties(user, app));
    }


    /**
     * Todo: If user cancels a job, task is removed immediately, but runningTask record doesn't go
     * away until loadResults or recoverResults processes it. Especially if we don't run
     * recoverResults frequently there may be quite a delay. Should verifyActiveJobCount only count
     * a job if it has both a running task record and a task record?
     * @param user
     * @param app
     * @throws java.lang.Exception
     */
    public void verifyActiveJobCount ( User user, Application app ) throws Exception 
    {
        logger.info("BEGIN: verifyActiveJobCount(User, Application)::void");
        
        Limits limits = getLimits(user, app);
        
        long currRunningTasksCount = RunningTask.getRunningTaskCount(user.getUserId());
        
        logger.debug(String.format("active_limit = [%s], User [%s] has active jobs [%d].", 
                                   ((limits.active_limit == null)? "null" : limits.active_limit),
                                   user.getUsername(), currRunningTasksCount));
        
        if (limits.active_limit != null)
        {
            if (currRunningTasksCount >= limits.active_limit)
            {
                logger.warn("User " + user.getUsername() + " has reached active jobs limit of " + limits.active_limit);
                LimitStatus status = new LimitStatus(ACTIVE_LIMIT, limits.active_limit);
//                throw new UsageLimitException("Too many active jobs.  Limit is " + limits.active_limit, status);
                throw UsageLimitException.buildOverActiveJobCountException(limits.active_limit);
            }
        }
        else {
            logger.debug("Limits.active_limit is NULL, unable to verify active job count!");
        }
        
        logger.info("END: verifyActiveJobCount(User, Application)::void");
    }


    /** 
     * Verifies if the user has reached XSEDE SU usage limit. 
     * 
     * @param user
     * @param app
     * @throws Exception 
     */
    public void verifyXSEDEUsage ( User user, Application app ) throws Exception
    {
        logger.info("BEGIN: verifyXSEDEUsage(User, Application)::void");
        
        logger.debug(String.format("User's country = [%s]", user.getCountry()));
        
        final String ERR_MSG = "User [%s] has reached the [%s] user's XSEDE SU limit of [%d]!"; 
        
        Limits limits = getLimits(user, app);
        
        long currentSU = Statistics.susPerUserThisPeriod(user.getUserId());
        
        // If user's country is not specified, treat the user as non-US user. 
        if (user.isUsUser())
        {
            logger.debug("US user.");
            if (limits.xsede_us_su_limit != null) {
                if (currentSU >= limits.xsede_us_su_limit) {
                    logger.warn(String.format(ERR_MSG, user.getUsername(), "US", limits.xsede_us_su_limit));
                    throw UsageLimitException.buildOverXsedeSuQuotaException("US", limits.xsede_us_su_limit); 
                }
				else{
					logger.debug("currentSU :(" + currentSU + ") limits.xsede_su_limit: (" + limits.xsede_us_su_limit + ")");
				}
            }
            else {
                logger.error("UsageLimit.Limits.xsede_us_su_limit is NULL, unable to verify the SU usage!");
            }
        }
        else 
        {
            logger.debug("Non-US user.");
            if (limits.xsede_nonus_su_limit != null) {
                if (currentSU >= limits.xsede_nonus_su_limit) {
                    logger.warn(String.format(ERR_MSG, user.getUsername(), "Non-US", limits.xsede_nonus_su_limit));
                    throw UsageLimitException.buildOverXsedeSuQuotaException("Non-US", limits.xsede_nonus_su_limit); 
                }
            }
            else {
                logger.error("UsageLimit.Limits.xsede_nonus_su_limit is NULL, unable to verify the SU usage!");
            }
        }
        
        logger.info("END: verifyXSEDEUsage(User, Application)::void");
    }


    /** 
     * Verify if the user has reached Non-XSEDE SU usage limit. 
     * 
     * @param user
     * @param app
     * @throws Exception 
     */
    public void verifyOtherUsage ( User user, Application app ) throws Exception
    {
        logger.info("BEGIN: verifyOtherUsage(User, Application)::void");
        
        logger.debug(String.format("User's country = [%s]", user.getCountry()));
        
        final String ERR_MSG = "User [%s] has reached the [%s] user's Non-XSEDE SU limit of [%d]!"; 
        
        // Ask Mark whether to enforce US/Non-US users.
        // if so, we need to add two more new properties.
        
        Limits limits = getLimits(user, app);
        
        long currentSU = Statistics.susPerUserThisPeriodNonXsede(user.getUserId());

        // If user's country is not specified, treat the user as non-US user. 
        if (user.isUsUser())
        {
            logger.debug("US user.");
            if (limits.other_us_su_limit != null) {
                if (currentSU >= limits.other_us_su_limit) {
                    logger.warn(String.format(ERR_MSG, user.getUsername(), "US", limits.other_us_su_limit));
                    throw UsageLimitException.buildOverNonXsedeSuQuotaException("US", limits.other_us_su_limit); 
                }
            }
            else {
                logger.error("UsageLimit.Limits.other_us_su_limit is NULL, unable to verify the SU usage!");
            }
        }
        else 
        {
            logger.debug("Non-US user.");
            if (limits.other_nonus_su_limit != null) {
                if (currentSU >= limits.other_nonus_su_limit) {
                    logger.warn(String.format(ERR_MSG, user.getUsername(), "Non-US", limits.other_nonus_su_limit));
                    throw UsageLimitException.buildOverNonXsedeSuQuotaException("Non-US", limits.other_nonus_su_limit); 
                }
            }
            else {
                logger.error("UsageLimit.Limits.other_nonus_su_limit is NULL, unable to verify the SU usage!");
            }
        }
        
        logger.info("END: verifyOtherUsage(User, Application)::void");
    }


    /** 
     * Verifies if the user has reached the SU limits based on various factors. 
     * 
     * @param user
     * @param app
     * @param toolId
     * @param loggedInViaIplant
     * @throws Exception 
     */
    public void verifySULimit ( User user, Application app, String toolId, boolean loggedInViaIplant ) throws Exception, UsageLimitException
    {
        logger.debug("verifySULimit for user " + user.getUsername() + " toolId: " + toolId + " loggedInViaIplant: " + loggedInViaIplant);
        Tool tool = null;
        
        try
        {
            tool = new Tool(toolId, Workbench.getInstance().getServiceFactory().getToolRegistry());
        }
        catch ( Exception e )
        {
            logger.debug("verifying due to invalid tool id:  " + toolId);
            return; // we may have been passed an invalid tool id.  ignore that here.
        }
        
        if (!tool.runsOnXSEDE())
        {
            logger.debug("not tool.runsOnXSEDE()  " + user.getUsername());
            verifyOtherUsage(user, app);
            return;
        }
        
        if (tool.usesCipresCommunityAccount(loggedInViaIplant, user))
        {
            logger.debug("tool.runsOnXSEDE()  " + user.getUsername());
            verifyXSEDEUsage(user, app);
            return;
        }
        
        // If using their own xsede account, there's nothing to verify
        logger.debug("User " + user.getUsername() + " has his own allocation");
    }


    public void testAndIncrementUserRequestCount ( User user )
    {
        if (concurrentLimit == null)
        {
            return;
        }
        synchronized (userRequestCount)
        {
            Long count = userRequestCount.get(user.getUserId());
            if (count == null)
            {
                count = 0L;
            }
            if (count < concurrentLimit)
            {
                userRequestCount.put(user.getUserId(), count + 1);
                return;
            }
            logger.debug("INC Throwing usage limit exception for ." + user.getUsername());
            LimitStatus status = new LimitStatus(CONCURRENT_LIMIT, concurrentLimit);
            throw new UsageLimitException("Too many concurrent requests.  Per user maximum is " + concurrentLimit);
        }
    }


    public void decrementUserRequestCount ( User user )
    {
        if (concurrentLimit == null)
        {
            return;
        }
        
        synchronized (userRequestCount)
        {
            Long count = userRequestCount.get(user.getUserId());
            
            if (count == null)
            {
                logger.warn("DEC User count not found for " + user.getUsername());
            }
            else if (count == 0)
            {
                //log.warn("DEC User count is zero for " + user.getUsername());
            }
            else
            {
                userRequestCount.put(user.getUserId(), count - 1);
            }
        }
    }


    public Map<String, String> limits ( User u ) throws Exception
    {
        return limits(u.preferences());
    }


    public Map<String, String> limits ( Map<String, String> p )
    {
        HashMap<String, String> m = new HashMap<String, String>();
        
        if (p.get(NO_LIMITS) != null)
        {
            m.put(NO_LIMITS, p.get(NO_LIMITS));
        }
        
        if (p.get(XSEDE_US_SU_LIMIT) != null)
        {
            m.put(XSEDE_US_SU_LIMIT, p.get(XSEDE_US_SU_LIMIT));
        }
        
        if (p.get(XSEDE_NONUS_SU_LIMIT) != null)
        {
            m.put(XSEDE_NONUS_SU_LIMIT, p.get(XSEDE_NONUS_SU_LIMIT));
        }
        
        if (p.get(OTHER_US_SU_LIMIT) != null)
        {
            m.put(OTHER_US_SU_LIMIT, p.get(OTHER_US_SU_LIMIT));
        }
        
        if (p.get(OTHER_NONUS_SU_LIMIT) != null)
        {
            m.put(OTHER_NONUS_SU_LIMIT, p.get(OTHER_NONUS_SU_LIMIT));
        }
        
        if (p.get(ACTIVE_LIMIT) != null)
        {
            m.put(NO_LIMITS, p.get(ACTIVE_LIMIT));
        }
        
        if (p.get(SUBMITTED_TODAY_LIMIT) != null)
        {
            m.put(NO_LIMITS, p.get(SUBMITTED_TODAY_LIMIT));
        }
        
        return m;
    }

}
