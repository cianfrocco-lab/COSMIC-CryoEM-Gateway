package org.ngbw.web.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;

import org.ngbw.sdk.Workbench;
//import org.ngbw.sdk.database.SuTransactionType;
import org.ngbw.sdk.database.User;
//import org.ngbw.sdk.database.UserSuAllocationService;


public class MultipleAccounts
{
    private static final Logger logger = Logger.getLogger(MultipleAccounts.class.getName());
    public static Properties wbProperties = Workbench.getInstance().getProperties();

    // automatic email properties
    public static final String REPORT_LOCATION = wbProperties.getProperty("multiple.account.report.location");

    private static final String UNDERSCORE = "_";
    private static final String HYPHON = "-";
    private static final String SLACK = "/";
    private static final String DOT = ".";
    private static final String SPACE = " ";
    private List<MultipleAccounts.AccountInfo> multipleAccountsList = new ArrayList<MultipleAccounts.AccountInfo>();
    private String reportDate = null;
    private String searchMAStr = null;
    private String blockedAccts = null;
    private String unblockedAccts = null;
    private String previousReportDate = null;
    private boolean reportExist = false;

    public static class BlockedUser
    {
        public String user;
        public String pureUser = null;
        public boolean blocked;
        public boolean exist;
        public boolean paid;
        public long userId = 0L;


        public BlockedUser ( String user, boolean blocked, boolean exist, boolean paid, long userId )
        {
            this.user = user;
            this.blocked = blocked;
            this.exist = exist;
            this.paid = paid;
            this.userId = userId;
            if (user != null) {
                String[] pUsers = user.split("\\,");
                if (pUsers != null && pUsers.length > 0) {
                    if (pUsers[0] != null && pUsers[0].length() >= 7) {
                        pureUser = pUsers[0].substring(6, pUsers[0].length() - 1);
                    }
                }
            }
            if (pureUser == null) {
                pureUser = user;
            }
        }


        String getUser ()
        {
            return user;
        }


        String getPureUser ()
        {
            return pureUser;
        }


        boolean getBlocked ()
        {
            return blocked;
        }


        boolean getExist ()
        {
            return exist;
        }


        boolean getPaid ()
        {
            return paid;
        }


        long getUserId ()
        {
            return userId;
        }

    }

    public static class AccountInfo
    {
        public String ip;
        public String owner;
        public String location;
        public List<BlockedUser> users = null;
        //public List<Boolean> blocked = null;

        public AccountInfo ()
        {
            users = new ArrayList<BlockedUser>();
            //blocked = new ArrayList<Boolean>();
        }


        String getIp ()
        {
            return ip;
        }


        String getOwner ()
        {
            return owner;
        }


        String getLocation ()
        {
            return location;
        }

    }


    public boolean hasBlockedAccounts ()
    {
        return (blockedAccts != null && !blockedAccts.isEmpty());
    }


    public String getBlockedAccounts ()
    {
        return blockedAccts;
    }


    public boolean hasUnblockedAccounts ()
    {
        return (unblockedAccts != null && !unblockedAccts.isEmpty());
    }


    public String getUnblockedAccounts ()
    {
        return unblockedAccts;
    }


    public String unblockUser ( Map<String, Object> params )
    {
        unblockedAccts = null;
        String user = null;
        if (params != null) {
            Set<Map.Entry<String, Object>> entries = params.entrySet();
            if (entries != null) {
                for (Map.Entry<String, Object> e : entries) {
                    logger.info("Parameters key = " + e.getKey());
                    if (e.getKey() != null && e.getKey().equals("pureUser")) {

                        Object obj = e.getValue();
                        if (obj instanceof String[]) {
                            String[] ss = (String[]) obj;
                            for (String si : ss) {
                                user = si;
                                break;
                            }
                        }
                    }
                }
                if (user != null) {
                    User userFound = null;
                    try {
                        userFound = User.findUserByUsername(user);

                        if (userFound != null) {
                            userFound.setCanSubmit(true);
                            userFound.save();
                            unblockedAccts = user;
                            user = null;
                        }
                    }
                    catch ( Exception e ) {
                        logger.error(e.getMessage());
                    }
                }
            }
        }

        return user;
    }


    public boolean blockMultipleAccounts ( Map<String, Object> params )
    {
        boolean ret = false;
        StringBuffer sb = new StringBuffer();

        if (params != null) {
            Set<Map.Entry<String, Object>> entries = params.entrySet();
            if (entries != null) {
                for (Map.Entry<String, Object> e : entries) {
                    logger.info("Parameters key = " + e.getKey());
                    if (e.getKey() != null && e.getKey().startsWith("buser_")) {

                        Object obj = e.getValue();
                        if (obj instanceof String[]) {
                            String[] ss = (String[]) obj;
                            for (String si : ss) {
                                logger.info(" value = " + si);
                                if (si.equals("true")) {
                                    ret = true;
                                    String userName = e.getKey().substring(6);
                                    try {
                                        User user = User.findUserByUsername(userName);
                                        if (user != null) {
                                            user.setCanSubmit(false);
                                            user.save();
                                            sb.append(userName).append(SPACE);
                                        }
                                    }
                                    catch ( Exception ex ) {
                                        logger.error(ex.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    //logger.info("Parameters key = " + e.getKey() + " value = " + ((Boolean)e.getValue()));
                }
            }
        }
        blockedAccts = sb.toString();
        updateBlockedAccounts();
        return ret;
    }


    public List<MultipleAccounts.AccountInfo> getMultipleAccountsList ()
    {
        return multipleAccountsList;
    }


    public void setSearchMAStr ( String searchMA )
    {
        logger.info("setSearchMAStr(String searchMA) searchMA = " + searchMA);
        this.searchMAStr = searchMA;
    }


    public void setPreviousReportDate ( String pReportDate )
    {
        this.previousReportDate = pReportDate;
    }


    public String getSearchMAStr ()
    {
        return searchMAStr;
    }


    public String getReportDate ()
    {
        return reportDate;
    }


    public String getLatestReport ()
    {
        return getReport(null);
    }


    public String getReport ( String targetDate )
    {
        String latestReport = null;
        if (REPORT_LOCATION == null || REPORT_LOCATION.isEmpty()) {
            logger.error("Configuration: multiple.account.report.location cannot be found.");
            return latestReport;
        }
        else {
            logger.info("Configuration: multiple.account.report.location = " + REPORT_LOCATION);
        }

        try {
            File f = new File(REPORT_LOCATION);

            FilenameFilter filter = new FilenameFilter()
            {
                @Override
                public boolean accept ( File f, String name )
                {
                    return name.endsWith("output.txt");
                }

            };

            File[] files = f.listFiles(filter);

            long timestamp = 0l;
            long offset = 24 * 60 * 60 * 1000;
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    logger.info("File found: " + files[i].getName());
                    String[] nameParts = files[i].getName().split("\\" + UNDERSCORE);
                    logger.info("nameParts[0]: " + nameParts[0]);
                    if (nameParts != null && nameParts.length > 0) {
                        //long ts = Long.parseLong(nameParts[0]) - offset;
                        long ts = Long.parseLong(nameParts[0]);
                        if (targetDate == null) {
                            if (ts > timestamp && (files[i].length()>0) ) {
                                timestamp = ts;
                                latestReport = files[i].getAbsolutePath();
                                logger.info("lastest report is: " + files[i].getAbsolutePath());
                                Date date = new Date(ts);
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                reportDate = sdf.format(date);
                            }
                        }
                        else {
                            targetDate = targetDate.trim();
                            SimpleDateFormat sdf = null;
                            LocalDate current_date = LocalDate.now();

                            int currentYear = current_date.getYear();
                            if (targetDate.contains(HYPHON)) {
                                if (targetDate.indexOf(HYPHON) == 4) {
                                    sdf = new SimpleDateFormat("yyyy-MM-dd");
                                }
                                else {
                                    sdf = new SimpleDateFormat("MM-dd-yyyy");
                                    if (targetDate.length() == 5) {
                                        targetDate = targetDate + HYPHON + currentYear;
                                    }
                                }
                            }
                            else if (targetDate.contains(SLACK)) {
                                if (targetDate.indexOf(SLACK) == 4) {
                                    sdf = new SimpleDateFormat("yyyy/MM/dd");
                                }
                                else {
                                    sdf = new SimpleDateFormat("MM/dd/yyyy");
                                    if (targetDate.length() == 5) {
                                        targetDate = targetDate + SLACK + currentYear;
                                    }
                                }
                            }
                            else if (targetDate.contains(DOT)) {
                                if (targetDate.indexOf(DOT) == 4) {
                                    sdf = new SimpleDateFormat("yyyy.MM.dd");
                                }
                                else {
                                    sdf = new SimpleDateFormat("MM.dd.yyyy");
                                    if (targetDate.length() == 5) {
                                        targetDate = targetDate + DOT + currentYear;
                                    }
                                }
                            }
                            if (sdf != null) {
                                Date d = sdf.parse(targetDate);
                                logger.info("ts = " + ts + " d.getTime() =" + d.getTime());

                                if (ts >= d.getTime() && ts < (d.getTime() + offset)) {
                                    latestReport = files[i].getAbsolutePath();
                                    sdf = new SimpleDateFormat("yyyy-MM-dd");
                                    reportDate = sdf.format(d);
                                    //reportDate = targetDate;
                                    break;
                                }
                            }

                        }
                    }
                }
            }
        }
        catch ( Exception e ) {
            logger.error(e.getMessage());
        }
        logger.info("getReport() reportDate = " + reportDate);

        if (latestReport == null)
            reportExist = false;
        else
            reportExist = true;

        return latestReport;

    }


    //public String searchReport(String searchDate)
    //{
    //    return getReport(searchDate);
    //}
    public void searchMultipleAccountReportPrevious ()
    {
        logger.info("searchMultipleAccountReportPrevious previousReportDate = " + previousReportDate);

        String report = getReport(previousReportDate);

        if (report != null && !report.isEmpty()) {
            loadMultipleAccountsList(report);
        }
        else {
            multipleAccountsList.clear();
        }
    }


    public void searchMultipleAccountReport ()
    {
        logger.info("searchMultipleAccountReport searchMAStr = " + getSearchMAStr());

        blockedAccts = "";
        unblockedAccts = "";
        String report = getReport(getSearchMAStr());

        if (report != null && !report.isEmpty()) {
            loadMultipleAccountsList(report);
        }
        else {
            multipleAccountsList.clear();
        }
    }


    public boolean anyReportFound ()
    {
        return (multipleAccountsList != null && !multipleAccountsList.isEmpty());
    }

    public boolean anyReportExist ()
    {
        return reportExist;
    }

    public boolean loadMultipleAccountsList ()
    {
        String fileLoc = getLatestReport();
        logger.info("lastest report returned from getLatestReport() is: " + fileLoc);
        logger.info("reportDate = " + reportDate);
        if (fileLoc != null) {
            return loadMultipleAccountsList(fileLoc);
        }
        else {
            return false;
        }
    }


    public boolean loadMultipleAccountsList ( String fileLoc )
    {
        multipleAccountsList.clear();
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(fileLoc));

            String line = reader.readLine();
            MultipleAccounts.AccountInfo ai = null;
            while (line != null) {
                //logger.info(line);
                if (line.startsWith("IP=")) {
                    ai = new MultipleAccounts.AccountInfo();
                    ai.ip = line;
                }
                if (ai != null) {
                    if (line.startsWith("This ip belongs to:")) {
                        ai.owner = line;
                    }
                    if (line.startsWith("(Location:") || line.startsWith("Location:")) {
                        if (line.startsWith("(") && line.endsWith(")")) {
                            ai.location = line.substring(1, line.length() - 1);
                        }
                        else {
                            ai.location = line;
                        }
                    }
                    if (line.startsWith("User=")) {
                        //ai.users.add(line);
                        boolean blocked = true;
                        boolean exist = true;
                        boolean paid = false;
                        long userId = 0L;

                        String[] userParts = line.split("\\], Email\\=\\[");
                        if (userParts != null) {
                            for (String s : userParts) {
                                logger.info("userParts: " + s);
                            }
                        }
                        if (userParts != null && userParts.length > 0) {
                            if (userParts[0].startsWith("User=[")) {
                                String userName = userParts[0].substring(6);
                                try {
                                    User user = User.findUserByUsername(userName);

                                    if (user != null) {
                                        //ai.blocked.add(new Boolean(!user.canSubmit()));
                                        logger.info("Found user: " + userName);
                                        blocked = !user.canSubmit();
                                        exist = true;

                                        //List<UserSuAllocationService.UserSuAllocationTransaction> paidSU = UserSuAllocationService.getUserSuTransactions(user.getUserId(), SuTransactionType.PURCHASE, 0, 1);
                                        //if (paidSU != null && paidSU.size() > 0) {
                                        //    paid = true;
                                        //}
                                        userId = user.getUserId();
                                    }
                                    else {
                                        //ai.blocked.add(new Boolean(true));
                                        logger.info("Cannot find user: " + userName);
                                        blocked = true;
                                        exist = false;
                                    }
                                }
                                catch ( Throwable error ) {
                                    //ai.blocked.add(new Boolean(true));
                                    blocked = true;
                                    exist = false;
                                    logger.error("Error retrieving user with username \"" + userName + "\"", error);
                                }
                            }
                        }
                        ai.users.add(new BlockedUser(line, blocked, exist, paid, userId));
                    }
                    if (line.trim().isEmpty()) {
                        multipleAccountsList.add(ai);
                    }
                }

                // read next line
                line = reader.readLine();
            }
            reader.close();
            logger.info("The size of multipleAccountsList is: " + multipleAccountsList.size());
        }
        catch ( IOException e ) {
            logger.error(e.getMessage());
            return false;
        }

        return true;
    }


    private void updateBlockedAccounts ()
    {
        if (blockedAccts != null) {
            String[] bac = blockedAccts.trim().split(SPACE);
            if (bac != null && bac.length > 0) {
                Set<String> bbac = new HashSet<String>();
                for (String si : bac) {
                    bbac.add(si);
                }

                for (AccountInfo ai : multipleAccountsList) {
                    if (ai.users != null) {
                        for (BlockedUser bu : ai.users) {
                            if (bbac.contains(bu.pureUser)) {
                                bu.blocked = true;
                            }
                        }
                    }
                }
            }
        }
    }

}
