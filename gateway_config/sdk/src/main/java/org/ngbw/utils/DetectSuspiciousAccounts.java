package org.ngbw.utils;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.text.ParseException;

/**
 *
 * 
 */
public class DetectSuspiciousAccounts
{
    private static final String DOT = ".";
    private static final String SMTP = "smtp://outbound.ucsd.edu:587";
    private static final String TODAY = "today";
    private static final String YESTERDAY = "yesterday";
    private static final String pattern = "yyyy-MM-dd";
    
    private static Map<String, Set<String>> allLogins = new HashMap<String, Set<String>>();

    public static void main ( String[] args ) throws FileNotFoundException, java.io.IOException, Exception
    {
        
        {
            if (args.length < 4)
            {
                throw new Exception("usage: DetectSuspiciousAccounts -d <log directory> -m <email list> [-t optional date range, for example, 2022-02-07,2022-02-08]");
            }

            String logDirectory = null;
            String emailList = null;
            String dateRange = null;
            String logFile = null; 
            for (int i = 0; i < args.length; ++i)
            {
                if (args[i].equals("-d"))
                {
                    if ((i+1) < args.length)
                        logDirectory = args[i+1];
                }
                if (args[i].equals("-l"))
                {
                    if ((i+1) < args.length)
                        logFile = args[i+1];
                }
                if (args[i].equals("-m"))
                {
                    if ((i+1) < args.length)
                        emailList = args[i+1];
                } 
                if (args[i].equals("-t"))
                {
                    if ((i+1) < args.length)
                        dateRange = args[i+1];
                }                
            }
            if (logDirectory == null)
            {
                System.out.println("usage: DetectSuspiciousAccounts -d <log directory> <-m optional email list> [-t optional date range, for example, 2022-02-07,2022-02-08]");
                System.exit(1);
            }
            Date tDay = new Date();
            String today = new SimpleDateFormat(pattern).format(tDay);
            if (dateRange == null || dateRange.equals(TODAY))
            {
                dateRange = today;
            }
            if (dateRange != null && dateRange.equals(YESTERDAY))
            {
                Date yDate = new Date(System.currentTimeMillis()-24*60*60*1000);
                dateRange = new SimpleDateFormat(pattern).format(yDate);
            }
            //String cmd = "grep 'just logged in' ";
            String[] logDates = dateRange.split(",");
            StringBuffer sb = new StringBuffer();
            //sb.append("grep 'just logged in' ");
            
            long millis = 0L;
            for (String s : logDates)
            {
                if (logFile == null)
                    sb.append(logDirectory).append("/all-portal2.log");
                else
                    sb.append(logDirectory).append("/" + logFile);
                if (!s.equals(today))
                    sb.append(".").append(s).append(" ");
                else
                    sb.append(" ");

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
                try {
                    Date dateReport = simpleDateFormat.parse(s);
                    millis = dateReport.getTime() + (8*60*1000);
                } catch (ParseException e) {
                    System.out.println("Error parsing date: " + s + " Error message: "+ e.getMessage());
                }
            }
            if (millis == 0L) 
                millis = System.currentTimeMillis();
            String filePath = "./" + millis + ".txt";
            sb.append(" > ").append(filePath);
            
            String[] command = new String[3];
            command[0] = "grep";
            command[1] = "'just logged in'";
            command[2] = sb.toString();

            try
            {
                /*
                ProcessBuilder processBuilder = new ProcessBuilder();  
                processBuilder.command("/bin/bash", "-c", command[0] + " " + command[1] + " " + command[2]);
                Process worker = processBuilder.start();
                */
                //Process worker = Runtime.getRuntime().exec(command);
                /*
                stdout = new BufferedReader(new InputStreamReader(worker.getInputStream()));
                stderr = new BufferedReader(new InputStreamReader(worker.getErrorStream()));
                stdin = new PrintStream(new BufferedOutputStream(worker.getOutputStream(), 8192));
                */
                /*
                final int exitCode = worker.waitFor();
                if (exitCode != 0)
                {
                    System.out.println("Command didn't run successfully: " + command[0] + " " + command[1] + " " + command[2]);
                    System.exit(2);
                    
                }  
                */
                if (runCommand(command[0] + " " + command[1] + " " + command[2]) >= 2)
                {
                    System.exit(2);
                }
            } 
            catch (Exception e)
            {
                System.out.println(e);
                throw e;
            }

            
            //String filePath = null;
            
            //if (args[0].equals("-f"))
            //{
            //    filePath = args[1];
            //}
 
            
            BufferedReader reader;
            try {
                    reader = new BufferedReader(new FileReader(filePath));

                    String line = reader.readLine();
                    while (line != null) {
                            //System.out.println(line);
                            String[] fSplits = line.split(" \\| ");
                            if (fSplits.length > 1)
                            {
                                String[] sSplits = fSplits[1].split(" just logged in from ");
                                if (sSplits.length > 1)
                                {
                                    Set<String> userEmail = allLogins.get(sSplits[1]);
                                    
                                    if (userEmail == null)
                                    {
                                        userEmail = new HashSet();
                                    }
                                    //System.out.print(sSplits[1]);
                                    //System.out.print(sSplits[2]);
                                    userEmail.add(sSplits[0]);
                                    allLogins.put(sSplits[1], userEmail);
                                }
                            }
                            // read next line
                            line = reader.readLine();
                    }
                    reader.close();
                    
                    System.out.println("size = " + allLogins.size());
                    if (allLogins.size() >= 0)
                    {
                        String outputFile = "./" + millis + "_output.txt";
                        File fout = new File(outputFile);
                        FileOutputStream fos = new FileOutputStream(fout);

                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                        Set<Entry<String, Set<String>>> values = allLogins.entrySet();
                        for (Entry<String, Set<String>> v : values)
                        {
                            if (v.getValue().size()> 1 && 
                                !mostEndedWith (v.getValue(), "edu]") && 
                                !mostEndedWith (v.getValue(), "org]") && 
                                !mostEndedWith (v.getValue(), "gov]") &&
                                !allSameID(v.getValue()))
                            {
                                String hostName = null;
                                String ip = getIPFromLoggedInfo(v.getKey());
                                hostName = getHostNameFromIP(ip);
                                System.out.println("ip = " + ip + " hostname = " + hostName);
                                if (isHostNameEduGovOrg(hostName))
                                    continue;
                                
                                StringPair pair = getLocationInfo(ip);
                                bw.write(v.getKey());
                                //bw.write("(hostname: ");
                                //bw.write(hostName);
                                //bw.write(")");
                                bw.write(" : ");
                                bw.newLine();
                                bw.write("This ip belongs to: ");
                                bw.write(pair.str2);
                                bw.newLine();
                                bw.write("(Location: ");
                                bw.write(pair.str1);
                                bw.write(")");
                                bw.newLine();
                                int count = 0;
                                for (String vv : v.getValue())
                                {
                                    //if (count > 0)
                                    //    bw.write(" | ");
                                    bw.write(vv);
                                    bw.newLine();
                                    ++count;
                                }
                                bw.newLine();
                            }
                        }
                       
                        bw.close();
                        
                        if (emailList != null)
                        {
                            String[] emails = emailList.split(",");
                            if (emails != null && emails.length > 0)
                            {
                                for (String em : emails)
                                {
                                    String mailCmd = "mailx -v -s \"Report: users who logged in multiple accounts from the same ip (" + dateRange + ")\" " +
                                         "-a " + outputFile + " -S smtp=" + SMTP + " " + em + " < /dev/null";
                                
                                    runCommand(mailCmd);
                                }
                            }
                        }
                    }
            } catch (Exception e) {
                    e.printStackTrace();
            }            
            
        }
 
    }
    
    public static int runCommand(String cmd) throws Exception
    {
        ProcessBuilder processBuilder = new ProcessBuilder();  
        processBuilder.command("/bin/bash", "-c", cmd);
        Process worker = processBuilder.start();
        //Process worker = Runtime.getRuntime().exec(command);
        /*
        stdout = new BufferedReader(new InputStreamReader(worker.getInputStream()));
        stderr = new BufferedReader(new InputStreamReader(worker.getErrorStream()));
        stdin = new PrintStream(new BufferedOutputStream(worker.getOutputStream(), 8192));
        */
        final int exitCode = worker.waitFor();
        if (exitCode != 0)
        {
            System.out.println("Command didn't run successfully: " + cmd);
        }
        return exitCode;
    }
    
    public static String getIPFromLoggedInfo(String loggedInfo)
    {
        String ip = null;
        
        if (loggedInfo != null && loggedInfo.startsWith("IP=["))
        {
            loggedInfo = loggedInfo.substring(4);
            String[] items = loggedInfo.split("],");
            if (items != null && items.length > 0)
                ip = items[0];
        }
        return ip;
    }
   
    public static String getHostNameFromIP (String ip) throws Exception 
    {
        try
        {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.getHostName();
            //System.out.println("Host name is: "+addr.getHostName());
            //System.out.println("Ip address is: "+ addr.getHostAddress());
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    public static boolean isHostNameEduGovOrg(String hostname)
    {
        boolean ret = false;
        
        if (hostname != null)
        {
            String[] items = hostname.split("\\" + DOT);
            
            if (items != null && items.length > 0)
            {
                ret = (items[items.length - 1].equals("edu") || items[items.length - 1].equals("gov") || items[items.length - 1].equals("org"));
            }
            
            if (!ret && items.length > 1)
                ret = (items[items.length - 2].equals("edu") || items[items.length - 2].equals("gov") || items[items.length - 2].equals("org"));
        }
        return ret;
    }
    
    private static boolean allEndedWith(Set<String> sSet, String end)
    {
        boolean ret = true;
        
        if (sSet == null || sSet.size() < 1)
            ret = false;
        for (String v : sSet)
        {
            if (!v.endsWith(end))
            {
                ret = false;
                break;
            }
        }
        return ret;
    }
    
    private static boolean mostEndedWith(Set<String> sSet, String end)
    {
        boolean ret = true;
        
        if (sSet == null || sSet.size() < 1)
            ret = false;
        int count = 0;
        for (String v : sSet)
        {
            if (v.endsWith(end))
            {
                count++;
            }
        }
        return ((sSet.size()-count) < count);
    }


    private static boolean allSameID(Set<String> sSet)
    {
        boolean ret = true;
        Set<String> ssSet = new HashSet<String>();
        
        for (String v : sSet)
        {
            String[] vElements = v.split(", ");
            
            if (vElements != null && vElements.length > 0)
                ssSet.add(vElements[0]);
        }
        
        if (ssSet.size() > 1)
            ret = false;
        
        return ret;
    }
    
    
    private static StringPair getLocationInfo(String ip)
    {
        try
        {
            String urlStr = "http://ipwhois.app/json/" + ip;
            URL url = new URL(urlStr);

            HttpURLConnection http = (HttpURLConnection) url.openConnection ();
            http.connect ();

            //int code = http.getResponseCode();
            BufferedReader br = null;
            if (100 <= http.getResponseCode() && http.getResponseCode() <= 399) {
                br = new BufferedReader(new InputStreamReader(http.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            }
            //if(code != 200) throw new RuntimeException("Failed to get the location. Http Status code : " + code);
            //return http.get
            String output;
            StringBuffer sb = new StringBuffer();
            StringBuffer org = new StringBuffer();
            while ((output = br.readLine()) != null)
            {
                System.out.println(output);
                if (output != null)
                {
                    String[] itemLevel1 = output.split(",");
                    if (itemLevel1 != null)
                    {
                        for (int i = itemLevel1.length -1; i >= 0; i--)
                        {
                            if (itemLevel1[i].startsWith("\"city\""))
                            {
                                String[] itemLevel2 = itemLevel1[i].split("\":\"");
                                if (itemLevel2 != null && itemLevel2.length > 1)
                                    sb.append(itemLevel2[1]);
                            }
                            if (itemLevel1[i].startsWith("\"region\""))
                            {
                                String[] itemLevel2 = itemLevel1[i].split("\":\"");
                                if (itemLevel2 != null && itemLevel2.length > 1)
                                    sb.append(itemLevel2[1]);
                            }
                            if (itemLevel1[i].startsWith("\"country\""))
                            {
                                String[] itemLevel2 = itemLevel1[i].split("\":\"");
                                if (itemLevel2 != null && itemLevel2.length > 1)
                                    sb.append(itemLevel2[1]);
                            }       
                            if (itemLevel1[i].startsWith("\"isp\""))
                            {
                                String[] itemLevel2 = itemLevel1[i].split("\":\"");
                                if (itemLevel2 != null && itemLevel2.length > 1)
                                {
                                    if (itemLevel2[1].endsWith("\""))
                                        org.append(itemLevel2[1]);
                                    else
                                        org.append(itemLevel2[1] + "\"");
                                }
                            }  
                            if (itemLevel1[i].startsWith("\"org\""))
                            {
                                String[] itemLevel2 = itemLevel1[i].split("\":\"");
                                if (itemLevel2 != null && itemLevel2.length > 1)
                                {
                                    if (itemLevel2[1].endsWith("\""))
                                        org.append(itemLevel2[1]);
                                    else
                                        org.append(itemLevel2[1] + "\"");
                                }
                            }                              
                        }
                        
                    }
                
                }
            }
            String location = sb.toString().replace('"', ',');
            String organization = org.toString().replace('"', ',');
            if (location.endsWith(","))
                location = location.substring(0, location.length()-1);
            if (organization.endsWith(","))
                organization = organization.substring(0, organization.length()-1);
            return (new StringPair(location, organization));
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
        return null;
    }
    
    private static class StringPair
    {
        public final String str1;
        public final String str2;
        
        public StringPair(String str1, String str2)
        {
            this.str1 = str1;
            this.str2 = str2;
        }
    }
}
