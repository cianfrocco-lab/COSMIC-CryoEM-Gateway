package org.ngbw.sdk.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.Workbench;


/**
 *
 * @author mzhuang
 */
public class RegistrationManagerBase
{
    private static final String COMMENT = "#";
    private static final String TILE = "~";
    private static final String AT ="@";
    private static final String DOT = ".";
    private static final Log logger = LogFactory.getLog(RegistrationManagerBase.class.getName());
    protected String rsconfig = null;//Workbench.getInstance().getProperties().getProperty("registration.configuration.file");
    private long configLastModified = 0L;
    private Set<String> rm = new HashSet<String>();
    private Set<String> existingCheck = new HashSet<String>();

    public enum MatchType {NONE, CATEGORY, DOMAIN, MATCH};

    //static
    //{
    //    loadConfig();
    //}


    public boolean loadConfig ()
    {
        //logger.info("registration.configuration.file=" + rsconfig);
        
        if (rsconfig == null || rsconfig.isEmpty())
        {
            return false;
        }
        
        File f = new File(rsconfig);

        if (!f.exists())
        {
            logger.info("Registration configuration file: " + rsconfig + " doesn't exist");
            return false;
        }

        synchronized (RegistrationManager.class)
        {
            logger.info("f.lastModified() =" + f.lastModified() + " configLastModified = " + configLastModified);
            
            if (f.lastModified() <= configLastModified)
            {
                return true;
            }

            configLastModified = f.lastModified();
        }
        
        BufferedReader reader = null;
        
        try
        {
            reader = new BufferedReader(new FileReader(f.getAbsolutePath()));

            String line = reader.readLine();
            //int lineCnt = 0;
            rm.clear();
            while (line != null)
            {
                //logger.debug(line);
                line = line.trim();
                if (!isCommetLine(line))
                {
                    if (!isExistingCheckLine(line))
                        rm.add(line);
                    else
                    {
                        line = getCheckLineWithoutCheckSymbol(line);
                        existingCheck.add(line);
                    }
                }
                line = reader.readLine();
            }
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (reader != null)
                {
                    reader.close();
                }
            }
            catch ( Exception ex )
            {
            }
        }
        return true;
    }


    public MatchType
    inTheList ( String applicant)
    {
        loadConfig();
        if (applicant != null)
            applicant = applicant.trim();
        boolean pass = rm.contains(applicant);
        
        if (pass)
            return MatchType.MATCH;
        
        String[] appItems = applicant.split(AT);
        if (appItems != null && appItems.length >0)
            logger.info("applicant splitItems[0] = " + appItems[0]);
        if (appItems != null && appItems.length >1)
            logger.info("applicant splitItems[1] = " + appItems[1]);
        if (appItems != null && appItems.length > 1)
        {
            pass = rm.contains(AT + appItems[1]);
        
            if (pass)
                return MatchType.DOMAIN;
            
            String[] endItems = appItems[1].split("\\"+DOT);
            
            if (endItems != null && endItems.length > 0)
            {
                pass = rm.contains(endItems[endItems.length -1]);
                
                if (!pass && endItems.length > 1)
                    pass = rm.contains(endItems[endItems.length-2]);
                
                if (pass)
                    return MatchType.CATEGORY;    
            }

            if (!existingCheck.isEmpty())
            {
                boolean doExistingCheck = false;
                if (endItems != null && endItems.length > 0)
                {
                    doExistingCheck = existingCheck.contains(endItems[endItems.length -1]);
                    if (!doExistingCheck && endItems.length > 1)
                      doExistingCheck = existingCheck.contains(endItems[endItems.length -2]);  
                }
                
                if (doExistingCheck)
                {
                    //String url = "https://" + appItems[1];
                    String url = "https://" + appItems[1];
                    //logger.info("unsecured url is: " + url.replaceFirst("^https", "http"));
                    if (pingURL(url, 3000))
                    {
                        logger.info(url + " does exists");
                        return MatchType.CATEGORY;
                    }
                    else
                        logger.info(url  + " doesn't exists");
                }
            }
        }
        
        return MatchType.NONE;
    }
    
    public boolean findIt(String applicant)
    {
        return (inTheList(applicant) != MatchType.NONE);
    }
    /*
    public boolean
    inTheList ( String applicant)
    {
        loadConfig();
        if (applicant != null)
            applicant = applicant.trim();
        boolean pass = rm.contains(applicant);
        
        if (pass)
            return pass;
        
        String[] appItems = applicant.split(AT);
        if (appItems != null && appItems.length >0)
            logger.info("applicant splitItems[0] = " + appItems[0]);
        if (appItems != null && appItems.length >1)
            logger.info("applicant splitItems[1] = " + appItems[1]);
        if (appItems != null && appItems.length > 1)
        {
            pass = rm.contains(AT + appItems[1]);
        }
        return pass;
    }
    */
    private boolean isCommetLine ( String line )
    {
        if (line == null || line.isEmpty() || line.startsWith(COMMENT))
        {
            return true;
        }

        return false;
    }    
    
    private boolean isExistingCheckLine ( String line )
    {
        if (line != null && !line.isEmpty() && line.startsWith(TILE))
        {
            return true;
        }

        return false;
    }   
    
    private String getCheckLineWithoutCheckSymbol(String line)
    {
        if (line != null && !line.isEmpty() && line.startsWith(TILE))
        {
            line = line.substring(1);
        }
        return line;
    }
    
    public boolean pingURL4(String s)
    {
        boolean ret = pingURL3(s);
        
        if (!ret)
        {
            if (s.startsWith("https"))
                s = s.replaceFirst("^https", "http");
            else
                s = s.replaceFirst("^http", "https");
            ret = pingURL3(s);
        }
        return ret;
    }
    
    public boolean pingURL3(String s)
    {
        // The URL for which IP address needs to be fetched
        //String s = "https:// www.google.com/";
 
        try {
            // Fetch IP address by getByName()
            InetAddress ip = InetAddress.getByName(new URL(s)
                                                       .getHost());
 
            // Print the IP address
            logger.info("Public IP Address of " + s +" is : " + ip);
            return true;
        }
        catch (Exception e) {
            logger.error(e);
            return false;
        }
    }    
    
    public boolean pingURL2(String urlInput)
    {
        try
        {
            URL url = new URL(urlInput);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setInstanceFollowRedirects(false);

            int responseCode = huc.getResponseCode();
            
            return (responseCode == HttpURLConnection.HTTP_OK);
        }
        catch (Exception ex)
        {
            logger.error(ex);
            return false;
        }
    }

    public boolean pingURLTwoModes(String s, int timeout)
    {
        boolean ret = pingURL(s, timeout);
        
        if (!ret)
        {
            if (s.startsWith("https"))
                s = s.replaceFirst("^https", "http");
            else
                s = s.replaceFirst("^http", "https");
            ret = pingURL3(s);
        }
        return ret;
    }
    
    public boolean pingURL(String url, int timeout) {        
    //url = url.replaceFirst("^https", "http"); // Otherwise an exception may be thrown on invalid SSL certificates.

    try {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("HEAD");
        int responseCode = connection.getResponseCode();
        logger.info("responseCode = " + responseCode);      
        return ((200 <= responseCode && responseCode <= 399) || (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) || (responseCode == HttpURLConnection.HTTP_FORBIDDEN));
    } catch (IOException exception) {
        logger.error(exception);
        return false;
    }
}
}
