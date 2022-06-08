/*
 * This has a main program (at bottom) that lets user
 * view and modify user and app usage limits.
 */
package org.ngbw.sdk.jobs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.common.util.StringUtils;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.Application;
//sdk/src/main/java/org/ngbw/sdk/jobs/UsageLimit.java
//import org.ngbw.sdk/jobs/UsageLimit;
import org.ngbw.sdk.UsageLimitException;


public class UsageLimitShower
{
    Application app;
    User user;
    
    static final List<String> limitNames = UsageLimit.getLimitNames();
    
    static final Map<String, String> defaultLimits = UsageLimit.getInstance().getDefaultLimits();
    
    static final String defaultLimitsAsString = StringUtils.map2String(defaultLimits);


    public UsageLimitShower ( Application app )
    {
        this.app = app;
    }


    public UsageLimitShower ( User user )
    {
        this.user = user;
    }


    public String getName ()
    {
        if (this.user != null)
        {
            return user.getUsername();
        }
        
        return app.getName();
    }


    public Map<String, String> getLimits () throws Exception
    {
        Map<String, String> properties;
        
        if (user != null)
        {
            properties = user.preferences();
        }
        else
        {
            properties = app.preferences();
        }
        
        return justLimits(properties);
    }


    public void clearLimits () throws Exception
    {
        if (user != null)
        {
            removeLimits(user.preferences());
            user.save();
        }
        else
        {
            removeLimits(app.preferences());
            app.save();
        }
    }


    public void setXsedeSU ( Long limit ) throws Exception
    {
        if (user != null)
        {
            user.preferences().put(UsageLimit.XSEDE_US_SU_LIMIT, limit.toString());
            user.save();
        }
        else
        {
            app.preferences().put(UsageLimit.XSEDE_US_SU_LIMIT, limit.toString());
            app.save();
        }
    }


    public void setOtherSU ( Long limit ) throws Exception
    {
        if (user != null)
        {
            user.preferences().put(UsageLimit.OTHER_US_SU_LIMIT, limit.toString());
            user.save();
        }
        else
        {
            app.preferences().put(UsageLimit.OTHER_US_SU_LIMIT, limit.toString());
            app.save();
        }
    }


    public void setSubmittedToday ( Long limit ) throws Exception
    {
        if (user != null)
        {
            user.preferences().put(UsageLimit.SUBMITTED_TODAY_LIMIT, limit.toString());
            user.save();
        }
        else
        {
            app.preferences().put(UsageLimit.SUBMITTED_TODAY_LIMIT, limit.toString());
            app.save();
        }
    }


    public void setActive ( Long limit ) throws Exception
    {
        if (user != null)
        {
            user.preferences().put(UsageLimit.ACTIVE_LIMIT, limit.toString());
            user.save();
        }
        else
        {
            app.preferences().put(UsageLimit.ACTIVE_LIMIT, limit.toString());
            app.save();
        }
    }


    public void setNoLimits ( boolean b ) throws Exception
    {
        if (user != null)
        {
            user.preferences().put(UsageLimit.NO_LIMITS, (b ? "1" : "0"));
            user.save();
        }
        else
        {
            app.preferences().put(UsageLimit.NO_LIMITS, (b ? "1" : "0"));
            app.save();
        }
    }


    /*
     * return new map with just the usage limit properties (other properties are omitted)
     */
    private Map<String, String> justLimits ( Map<String, String> properties )
    {
//        Map<String, String> limits = new HashMap<String, String>(properties);
//        
//        for (String key : properties.keySet())
//        {
//            if (!limitNames.contains(key))
//            {
//                limits.remove(key);
//            }
//        }
//        
//        return limits;
        
        Map<String, String> limitsOnly = new HashMap<>();
        
        for (String limitName : limitNames) 
        {
            String val = properties.get(limitName);
            
            if (val != null) 
            {
                limitsOnly.put(limitName, val);
            }
        }
        
        return limitsOnly; 
    }


    /*
     * modify the passed in map, removing all usage limits.
     */
    private void removeLimits ( Map<String, String> properties )
    {
        // Need to use an iterator to remove entries while iterating
        Iterator<Map.Entry<String, String>> it = properties.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, String> entry = it.next();
            if (limitNames.contains(entry.getKey()))
            {
                it.remove();
            }
        }

        /*
         * for (String key : properties.keySet()) { if (limitNames.contains(key)) {
         * properties.remove(key); } }
         */
        return;
    }


    /*
     * Main program. Print all output (including errors) to stdout so that we can run with stderr
     * redirected to /dev/null to avoid seeing all the sdk logging.
     */
    static int index;


    public static void main ( String[] args ) throws Exception
    {
        try
        {
            processArguments(args);
        }
        catch ( Exception e )
        {
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }


    private static void processArguments ( String[] args ) throws Exception
    {
        String sval;
        long lval;
        //UsageLimitSetter setter = null;
        UsageLimitShower setter = null;

        Workbench.getInstance();
        while (index < args.length && args[index].startsWith("-") && args[index].length() == 2)
        {
            switch (args[index++].charAt(1))
            {
                case 'h':
                    printHelp();
                    break;
                case 'a':
                    sval = requireArg("-a", args);
                    Application application = Application.find(sval);
                    if (application == null)
                    {
                        System.out.println("application not found.");
                        System.exit(1);
                    }
                    //setter = new UsageLimitSetter(application);
                    break;
                case 'u':
                    sval = requireArg("-u", args);
                    User user = User.findUser(sval);
                    if (user == null)
                    {
                        System.out.println("user not found.");
                        System.exit(1);
                    }
                    //setter = new UsageLimitSetter(user);
                    setter = new UsageLimitShower(user);
                    try
                      {
                          //logger.debug("toolId: " + toolId + " task.getToolId(): " + task.getToolId());
                          // Verify SU usages.
                          //UsageLimit.getInstance().verifySULimit(user, null, task.getToolId(), loggedInViaIPlant());
                          UsageLimit.getInstance().verifySULimit(user, null, null, false);

                        System.out.println("user: " + user.getUsername() + " verified for sufficient SU\n");
                          //logger.debug(getUsernameString() + "Calling submitTask for taskId  " + taskId);
                      }
                    catch ( UsageLimitException ule )
                      {
                        //logger.error(ule.getMessage());
                        //setCurrentTask(null);
                        //refreshFolderTaskTabs();
                        //reportUserError(ule.getMessage());
                        System.out.println("user: " + user.getUsername() + " faild SU verification\n");
                        //return LIST_ERROR;
                        //print("list_error\n");
                      }
                    break;
                case 'l':
                    //list(setter);
                    break;
                case 'c':
                    //requireSetter(setter);
                    //setter.clearLimits();
                    break;
                case 'n':
                    //requireSetter(setter);
                    lval = requireLongArg("-n", args);
                    if (lval == 0)
                    {
                        //setter.setNoLimits(false);
                    }
                    else if (lval == 1)
                    {
                        //setter.setNoLimits(true);
                    }
                    else
                    {
                        System.out.println("-n requires '0' or '1' argument");
                        System.exit(1);
                    }
                    break;
                case 'x':
                    //requireSetter(setter);
                    lval = requireLongArg("-x", args);
                    //setter.setXsedeSU(lval);
                    break;
                case 'o':
                    //requireSetter(setter);
                    lval = requireLongArg("-o", args);
                    //setter.setOtherSU(lval);
                    break;
                case 's':
                    //requireSetter(setter);
                    lval = requireLongArg("-s", args);
                    //setter.setSubmittedToday(lval);
                    break;
                case 'j':
                    //requireSetter(setter);
                    lval = requireLongArg("-j", args);
                    //setter.setActive(lval);
                    break;
            }
        }
    }


    private static void requireSetter ( UsageLimitSetter setter )
    {
        if (setter == null)
        {
            System.out.println("-u username or -a application_name must be first arguments on the command line.");
            System.exit(1);
        }
    }


    private static String requireArg ( String option, String[] args )
    {
        String tmp;
        if (index < args.length)
        {
            tmp = args[index++];
            return tmp;
        }
        System.out.println(option + " requires an argument");
        System.exit(1);
        return "";
    }


    private static long requireLongArg ( String option, String[] args )
    {
        String tmp = requireArg(option, args);
        try
        {
            return new Long(tmp);
        }
        catch ( NumberFormatException nfe )
        {
            System.out.println(option + " requires an integer argument");
            System.exit(1);
        }
        return 0L;
    }


    private static void list ( UsageLimitSetter setter ) throws Exception
    {
        System.out.println("System wide limits: " + defaultLimitsAsString);
        if (setter != null)
        {
            Map<String, String> map = setter.getLimits();
            String str = StringUtils.map2String(map);
            System.out.println("Limits for " + setter.getName() + ": " + str);
            return;
        }
        return;
    }


    private static void printHelp ()
    {
        String msg
                = "You must restart tomcat after changing the limits inorder for them to take effect.\n\n"
                + "One of -a or -u must be first args on command line.\n"
                + "\t -a appname\n"
                + "\t\t which app do other args apply to\n"
                + "\t -u username\n"
                + "\t\t which user do other args apply to\n"
                + "\n"
                + "These operation arguments follow.  One or more allowed.  Performed in the order they appear.\n"
                + "\t -l\n"
                + "\t\t list settings for specified app or user\n"
                + "\t -c\n"
                + "\t\t clear all settings for specified app or user\n"
                + "\t -n 0|1\n"
                + "\t\t set no_limits = 0 or 1 (to disable limits for this app or user)\n"
                + "\t -x n\n"
                + "\t\t set xsede_su_limit to n\n"
                + "\t -o n\n"
                + "\t\t set other_su_limit to n\n"
                + "\t -s n\n"
                + "\t\t set submitted_today limit to n\n"
                + "\t -j n\n"
                + "\t\t set active jobs limit to n\n"
                + "\t -h\n"
                + "\t\t help\n";
        System.out.println(msg);
    }


}
