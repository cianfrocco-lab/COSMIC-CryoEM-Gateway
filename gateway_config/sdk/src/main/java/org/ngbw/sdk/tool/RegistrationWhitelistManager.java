package org.ngbw.sdk.tool;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.Workbench;


/**
 *
 * @author mzhuang
 */
public class RegistrationWhitelistManager extends RegistrationManagerBase 
{
    //private static final String COMMENT = "#";
    //private static final String AT ="@";
    //private static final Log logger = LogFactory.getLog(RegistrationManager.class.getName());
    //private static String rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file");
    //private static long configLastModified = 0L;
    //private static Set<String> rm = new HashSet<String>();
    private static final Log logger = LogFactory.getLog(RegistrationWhitelistManager.class.getName());

    /*
    static
    {
        rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file.whitelist");
        logger.info("registration.configuration.file.whitelist=" + rsconfig);
        loadConfig();
    }
    */
    public RegistrationWhitelistManager()
    {
        rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file.whitelist");
        logger.info("registration.configuration.file.whitelist=" + rsconfig);
    }
    /*
    public static boolean loadConfig ()
    {
        return (RegistrationManager.loadConfig());
    }*/
}
