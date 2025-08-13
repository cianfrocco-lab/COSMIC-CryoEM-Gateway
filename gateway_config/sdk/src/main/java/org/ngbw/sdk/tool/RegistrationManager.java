package org.ngbw.sdk.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.tool.RegistrationManagerBase.MatchType;


/**
 *
 * @author mzhuang
 */
public class RegistrationManager
{

    private static final Log logger = LogFactory.getLog(RegistrationManager.class.getName());
    private static RegistrationWhitelistManager rwm =  new RegistrationWhitelistManager();
    
    private static RegistrationBlacklistManager rbm =  new RegistrationBlacklistManager();
    
    public static RegistrationWhitelistManager getRegistrationWhitelistManager()
    {
        return rwm;
    }

    public static RegistrationBlacklistManager getRegistrationBlacklistManager()
    {
        return rbm;
    }
    
    public static boolean exemptFromMultipleAccountCheck(String email)
    {
        boolean ret = false;
        
        //MatchType blackMatch = rbm.inTheList(email);
        MatchType whiteMatch = rwm.inTheList(email);
        
        if (whiteMatch == MatchType.NONE)
            return ret;
        
        MatchType blackMatch = rbm.inTheList(email);
        
        if (whiteMatch.ordinal() >= blackMatch.ordinal())
            ret = true;
   
        return ret;
        /*
        String eExempts = getPropertyFromPropertiesFile(MULTIPLE_ACCOUNT_CHECK_EXEMPT_EMAIL, null);
        if (eExempts != null)
        {
            String[] eExemptsArr = eExempts.split(",");
            String[] splitEmail = email.split("@");
            if (eExemptsArr != null && eExemptsArr.length > 0 && splitEmail != null && splitEmail.length > 1)
            {
                for (String e : eExemptsArr)
                {
                    if (splitEmail[1].endsWith("." + e) || splitEmail[1].contains("." + e + "."))
                    {
                        ret = true;
                        break;
                    }
                }
            }
        }
        if (!ret)
        {
            ret = RegistrationManager.getRegistrationWhitelistManager().inTheList(email);
        }
        else
        {
            boolean black = RegistrationManager.getRegistrationBlacklistManager().inTheList(email);
            
            if (black)
                ret = RegistrationManager.getRegistrationWhitelistManager().inTheList(email);
        }*/
        
    }    
}
