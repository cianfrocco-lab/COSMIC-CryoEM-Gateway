package org.ngbw.sdk.tool;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
//import java.nio.file.Path;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.Workbench;


/**
 *
 * @author mzhuang
 */
public class RegistrationBlacklistManager extends RegistrationManagerBase
{
    //private static final String COMMENT = "#";
    //private static final String AT ="@";
    //private static final Log logger = LogFactory.getLog(RegistrationManager.class.getName());
    //private static String rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file");
    //private static long configLastModified = 0L;
    //private static Set<String> rm = new HashSet<String>();
    private static final Log logger = LogFactory.getLog(RegistrationBlacklistManager.class.getName());

    /*
    static
    {
        rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file.blacklist");
        logger.info("registration.configuration.file.blacklist=" + rsconfig);
        loadConfig();
    }
    */
    public RegistrationBlacklistManager()
    {
        rsconfig = Workbench.getInstance().getProperties().getProperty("registration.configuration.file.blacklist");
        logger.info("registration.configuration.file.blacklist=" + rsconfig);
    }
    
    synchronized public boolean
    addToTheList ( String applicant)
    {
        if (inTheList(applicant) == MatchType.MATCH)
            return false;
                
        boolean ret = write(applicant);
        if (ret)
            loadConfig();
        return ret;
    }

    synchronized public MatchType
    inTheList ( String applicant)
    {
        return (super.inTheList(applicant));
    }
    
    private boolean write(final String s)
    {
        boolean ret = false;
        if (rsconfig != null)
        {
            try(FileWriter fw = new FileWriter(rsconfig, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
            {
                out.println(s);
                ret = true;
            } catch (IOException e) {
                logger.error("IOException thrown while trying to write: " + s + " into: " + rsconfig );
                logger.error(e);
            }        
        }
        
        return ret;
        /*
        if (rsconfig != null)
        {
            File f = new File(rsconfig);
            Files.
            Files.writeString(
                Path.of(f.getParent(), f.getName()),
                s + System.lineSeparator(),
                CREATE, APPEND
            );
        }*/
    }

}
