package org.ngbw.web.actions;

import com.opensymphony.xwork2.ActionContext;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.struts2.interceptor.validation.SkipValidation;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.tool.ToolResource;
import org.ngbw.sdk.common.util.GsiSSHProcessRunner;
import org.ngbw.sdk.common.util.SSHExecProcessRunner;
import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.Statistics;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.util.TaskSortableField;
import org.ngbw.web.model.Page;
import org.ngbw.web.model.Tab;
import org.ngbw.web.model.TabbedPanel;
import org.ngbw.web.model.impl.ListPage;


/**
 * Struts action class to manage user tasks in the NGBW web application.
 *
 * @author mzhuang
 */
@SuppressWarnings("serial")
public class AdminTaskManager extends ManageTasks
{
    private static final Log logger = LogFactory.getLog(AdminTaskManager.class.getName());
    public static final String MULTIPLEACCOUNTS = "multipleAccounts";
    public static final String ACCOUNTACTIVATION = "accountActivation";
    public static final String ACCOUNTACTIVATED = "accountActivated";

    private static final String EXPANSE = "expanse";
    private static final String EXPANSE_RUNNING_JOBS_DETAILS = ".expanseRunningJobsDetails";
    private static final String EXPANSE_INQUEUE_JOBS_DETAILS = ".expanseInQueueJobsDetails";

    private static final String EXPANSE_JOB_COUNT_CMD = "module load slurm;squeue -u cosmic2 |grep -c cosmic2";
    private static final String EXPANSE_JOB_RUNNING_COUNT_CMD = "module load slurm;squeue -u cosmic2 |grep cosmic2 |grep -c ' R '";
    private static final String EXPANSE_JOB_RUNNING_CMD = "module load slurm;squeue -u cosmic2 -o \"%.9i %.10P %.66j %.8u %.2t %.10M\" |grep ' R \\|PARTITION' > ./" + EXPANSE_RUNNING_JOBS_DETAILS;
    private static final String EXPANSE_JOB_INQUEUE_CMD = "module load slurm;squeue -u cosmic2 -o \"%.9i %.10P %.66j %.8u %.2t %.10M\" |grep -v ' R ' > ./" + EXPANSE_INQUEUE_JOBS_DETAILS;

    public static final String CRJ = "crj";
    private String searchStrAA = "";
    private String userEmailAA = "";
    private boolean hasActivated = false;
    private MultipleAccounts ma = new MultipleAccounts();

    private int expanseRunningCount = -1;
    private int expanseInQueueCount = -1;
    private String expanseRunningJobs = "";
    private String expanseInQueueJobs = "";    
    private static final String LMOD_WARNING = "'~~~~~~~'";
    private static final String NOT_AVAILABLE = "Not Available"; 
    // ================================================================
    // Action methods
    // ================================================================
    public String listTasks ()
    {
        logger.debug("BEGIN: execute()::String");

        String retVal = LIST;

        logger.debug(String.format("RetVal=[%s]", retVal));

        logger.debug("END: execute()::String");

        return retVal;
    }
    @SkipValidation
    public boolean anyReportFound ()
    {
        return ma.anyReportFound();
    
    }
   
    @SkipValidation
    public boolean anyReportExist ()
    {
        return ma.anyReportExist();

    }
 
    @SkipValidation
    public boolean hasBlockedAccounts ()
    {
        return ma.hasBlockedAccounts();
    
    }
    
    @SkipValidation
    public String getBlockedAccounts ()
    {
        return ma.getBlockedAccounts();
    
    }
    
    @SkipValidation
    public boolean hasUnblockedAccounts ()
    {
        return ma.hasUnblockedAccounts();
        
    }

    @SkipValidation
    public String getUnblockedAccounts ()
    {
        return ma.getUnblockedAccounts();
    
    }

    @SkipValidation
    public String getAccountActivation ()
    {
        return ACCOUNTACTIVATION;
    }

    @SkipValidation
    public String searchAccountActivation ()
    {
        return INPUT;
    }

    @SkipValidation
    public void setPreviousReportDate(String pReportDate)
    {
        ma.setPreviousReportDate(pReportDate);
    }

    @SkipValidation
    public String activateAccountOnBehalfOfUser ()
    {
        try
        {
            User targetUser = User.findUserAllByEmail(this.searchStrAA);

            if (targetUser != null)
            {
                targetUser.setActivationCode(null);
                targetUser.save();
                hasActivated = true;
                addActionMessage("User account associated with email address " + this.userEmailAA + " has been activated successfully");
            }
            else
            {
                hasActivated = false;
                clearActionErrors();
                addActionError("Error occurred when trying to activate account associated with email address " + userEmailAA);                    
            }
        }
        catch ( Exception ex )
        {
            logger.error(ex);
            clearActionErrors();
            addActionError("Error occurred when trying to activate account associated with email address " + userEmailAA);
        }        
        return ACCOUNTACTIVATION;
    }   

    @SkipValidation
    public String blockMultipleAccounts()
    {
        ActionContext context = ActionContext.getContext();

        Map<String, Object> params = context.getParameters();
        if (!ma.blockMultipleAccounts(params))
            addActionError("Please select at least one account to block");
        ma.searchMultipleAccountReportPrevious();
        /*
        if (params != null)
        {
            Set<Map.Entry<String, Object>> entries = params.entrySet();
            if (entries != null)
            {
                for (Map.Entry<String, Object> e : entries)
                {
                    logger.info("Parameters key = " + e.getKey());
                    Object obj = e.getValue();
                    if (obj instanceof String[])
                    {
                        String[] ss = (String[])obj;
                        for (String si: ss)
                            logger.info(" value = " + si);
                    }
                    //logger.info("Parameters key = " + e.getKey() + " value = " + ((Boolean)e.getValue()));
                }
            }
        }*/
        //String userId = findParam("idUser", params);
        return MULTIPLEACCOUNTS;
    }
   
    @SkipValidation
    public String searchMultipleAccountReport ()
    {
        ma.searchMultipleAccountReport();
        return MULTIPLEACCOUNTS;
    }

    @SkipValidation
    public String getMultipleAccounts ()
    {
        ma.loadMultipleAccountsList();
        return MULTIPLEACCOUNTS;
    }
   
    @SkipValidation
    public List<MultipleAccounts.AccountInfo> getMultipleAccountsList ()
    {
        //loadMultipleAccountsList();
        return ma.getMultipleAccountsList();
    }
   
    @SkipValidation
    public void setSearchMAStr(String searchMA)
    {
        ma.setSearchMAStr(searchMA);
    }
   
    @SkipValidation
    public String getSearchMAStr()
    {
        return (ma.getSearchMAStr());
    }

    @SkipValidation
    public boolean isSearchMAStrNotEmpty()
    {
        return (ma.getSearchMAStr() != null && !ma.getSearchMAStr().isEmpty());
    }
   
    @SkipValidation
    public String getReportDate()
    {
        logger.debug("BEGIN: getReportDate()::String");

        String reportDate = ma.getReportDate();
        logger.info("reportDate = " + reportDate);
        return reportDate;
    }


    @SkipValidation
    public String unblockUser ()
    {
        ActionContext context = ActionContext.getContext();

        Map<String, Object> params = context.getParameters();

        String user = null;
        if ((user = ma.unblockUser(params)) != null)
            addActionError("Error: failed to unblock " + user);
        ma.searchMultipleAccountReportPrevious();

        return MULTIPLEACCOUNTS;
    }   

    @SkipValidation                 
    public String refresh ()    
    {
        logger.info("BEGIN: refresh ()::String");
        refreshStatistics();
        return LIST;        
    } 
                            
                        
    @SkipValidation 
    public String getStatistics ()
    {               
        refreshStatistics();
        return LIST;
    }

    @SkipValidation
    public String getJobDetailsExpanseInQueue ()
    {
        expanseInQueueJobs = getJobDetails(EXPANSE_JOB_INQUEUE_CMD, EXPANSE_INQUEUE_JOBS_DETAILS, EXPANSE);
        return CRJ;
    }
    

    @SkipValidation
    public String getJobDetailsExpanseRunning ()
    {               
        expanseRunningJobs = getJobDetails(EXPANSE_JOB_RUNNING_CMD, EXPANSE_RUNNING_JOBS_DETAILS, EXPANSE);
        return CRJ;
    }

    @SkipValidation
    public String getExpanseRunningJobs ()
    {
        return "<pre class=\"source\"><span style=\"font-size: 12px\">" + expanseRunningJobs + "</span></pre>";
    }


    @SkipValidation
    public String getExpanseInQueueJobs ()
    {
        return "<pre class=\"source\"><span style=\"font-size: 12px\">" + expanseInQueueJobs + "</span></pre>";
    }

    public String getExpanseRunningCount ()
    {
        return formatNumber(expanseRunningCount);
    }
    
    
    public String getExpanseInQueueCount ()
    {
        return formatNumber(expanseInQueueCount);
    }

    private String formatNumber ( Number num )
    {
        if (num.doubleValue() >= 0.0)
        {
            return num.toString();
        }
        else
        {
            return NOT_AVAILABLE;
        }
    }

    private void refreshStatistics ()
    {
        ToolResource trExpanse = Workbench.getInstance().getServiceFactory().getToolRegistry().getToolResource(EXPANSE);

        logger.info("refreshStatistics(): " + (trExpanse == null ? "trExpanse is null" : "trExpanse is not null"));
        if (trExpanse != null)
        {
            String login = trExpanse.getParameters().get(new String("login"));

            logger.info("Statistics Expanse login = " + login);
            if (login != null && !login.isEmpty())
            {
                //SSHExecProcessRunner runner;
                //Class<SSHExecProcessRunner> runnerClass;
                //String className = "org.ngbw.sdk.common.util.SSHExecProcessRunner";

                GsiSSHProcessRunner runner = null;
                try
                {
                    //runnerClass = (Class<SSHExecProcessRunner>) Class.forName(className);

                    //runner = runnerClass.newInstance();
                    runner = new GsiSSHProcessRunner();
                    HashMap<String, String> cfg = new HashMap<String, String>();
                    //cfg.put(LOGIN, login);
                    cfg.put(LOGIN, "cosmic2@login.expanse.sdsc.edu");
                    if (runner.configure(cfg))
                    {
                        //Future<Integer> result = runner.start(EXPANSE_JOB_COUNT_CMD + ";" + EXPANSE_JOB_RUNNING_COUNT_CMD);
                        runner.start(EXPANSE_JOB_COUNT_CMD + ";" + EXPANSE_JOB_RUNNING_COUNT_CMD);
                        int exitCode = runner.waitForExit();
                        logger.info("Command to run:" + EXPANSE_JOB_COUNT_CMD + ";" + EXPANSE_JOB_RUNNING_COUNT_CMD);
                        //int exitCode = result.get();
                        if (exitCode == 0 || exitCode == 1)
                        {
                            String numStr = runner.getStdOut();
                            logger.info("stdout=" + numStr);
                            String[] nums = numStr.split("\n");
                            if (nums != null)
                            {
                                int allJobsCount = -1;

                                if (nums.length > 1)
                                {
                                    allJobsCount = Integer.parseInt(org.apache.commons.lang.StringUtils.chomp(nums[nums.length-2]));
                                    expanseRunningCount = Integer.parseInt(org.apache.commons.lang.StringUtils.chomp(nums[nums.length-1]));
                                }
                                else if (nums.length > 0)
                                {
                                    allJobsCount = Integer.parseInt(org.apache.commons.lang.StringUtils.chomp(nums[nums.length-1]));
                                }

                                /*
                                if (nums.length > 0)
                                {
                                    allJobsCount = Integer.parseInt(org.apache.commons.lang.StringUtils.chomp(nums[0]));
                                }

                                if (nums.length > 1)
                                {
                                    expanseRunningCount = Integer.parseInt(org.apache.commons.lang.StringUtils.chomp(nums[1]));
                                }
                                */

                                if (allJobsCount > -1 && expanseRunningCount > -1)
                                {
                                    expanseInQueueCount = allJobsCount - expanseRunningCount;
                                }
                            }
                        }
                        else
                        {
                            logger.info("expanse exitCode=" + exitCode);
                            String numStr = runner.getStdOut();
                            logger.info("expanse stdout=" + numStr);
                        }
                    }
                }
                catch ( Exception e )
                {
                    logger.error("", e);
                }
            }
        }
    }   

    private String getJobDetails ( String command, String fileName, String resourceName )
    {
        String jobs = "";
        ToolResource tr = Workbench.getInstance().getServiceFactory().getToolRegistry().getToolResource(resourceName);

        logger.info("refreshStatistics(): " + (tr == null ? "null" : "not null"));
        if (tr != null)
        {
            String login = tr.getParameters().get(new String("login"));
            //login = login.substring(atChar+1, dotChar);
            logger.info("Statistics login = " + login);
            if (login != null && !login.isEmpty())
            {
                //SSHExecProcessRunner runner;
                //Class<SSHExecProcessRunner> runnerClass;
                //String className = "org.ngbw.sdk.common.util.SSHExecProcessRunner";

                GsiSSHProcessRunner runner = null;
                try
                {
                    //runnerClass = (Class<SSHExecProcessRunner>) Class.forName(className);

                    //runner = runnerClass.newInstance();
                    runner = new GsiSSHProcessRunner();
                    HashMap<String, String> cfg = new HashMap<String, String>();
                    //cfg.put(LOGIN, login);
                    cfg.put(LOGIN, "cosmic2@login.expanse.sdsc.edu");

                    if (runner.configure(cfg))
                    {
                        //int ret = runner.run(NONAWS_JOB_COUNT_CMD);
                        //Future<Integer> result = runner.start(NONAWS_JOB_RUNNING_CMD + ";" + "wc -l ./" + COMET_RUNNING_JOBS_DETAILS);
                        //Future<Integer> result = runner.start(command + ";" + "wc -l ./" + fileName);
                        runner.start(command + ";" + "wc -l ./" + fileName);
                        logger.info("Command to run: " + command + ";" + "wc -l ./" + fileName);
                        logger.info("Command to run: " + command + ";" + "wc -l ./" + fileName);
                        //int exitCode = result.get();
                        int exitCode = runner.waitForExit();
                        if (exitCode == 0)
                        {
                            String countStr = runner.getStdOut();
                            logger.info("stdout=" + countStr);
                            String[] counts = countStr.split("\n");
                            if (counts != null && counts.length > 0)
                            {
                                String last = counts[counts.length-1];
                                String[] lasts = last.split(" ");
                                if (lasts != null && lasts.length > 0)
                                {
                                    int count = Integer.parseInt(lasts[0]);

                                    int step = 28;
                                    for (int run = 1; run <= count; run = run + step + 1)
                                    {
                                        runner.start("echo " + LMOD_WARNING + " ; sed -n " + run + "," + (run + step) + "p ./" + fileName);
                                        exitCode = runner.waitForExit();
                                        if (exitCode == 0)
                                        {
                                            String output = runner.getStdOut();
                                            String outputs[] = output.split(LMOD_WARNING.substring(1, LMOD_WARNING.length()-1)+"\n");
                                            if (outputs != null && outputs.length > 0)
                                            jobs += outputs[outputs.length-1];
                                            //logger.info("stdout=" + jobs);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch ( Exception e )
                {
                    logger.error("", e);
                }
            }
        }
        return jobs;

    }
    public void setSearchStrAA ( String searchStr )
    {
        logger.info("setSearchStrAA::searchStr = " + searchStr);
        if (searchStr == null)
        {
            return;
        }

        this.searchStrAA = searchStr.trim();

        if (this.searchStrAA.contains(".") && this.searchStrAA.contains("@"))
        {
            try
            {
                User targetUser = User.findUserAllByEmail(this.searchStrAA);

                if (targetUser != null)
                {
                    userEmailAA = this.searchStrAA;
                    hasActivated = (targetUser.getActivationCode() == null);
                }
                else
                {
                    userEmailAA = "";
                    hasActivated = false;
                    clearActionErrors();
                    addActionError("No user with that email address can be found in the database. Please make sure you enter the correct email address for current environment (Beta or Production).");     
                }
            }
            catch ( Exception ex )
            {
                logger.error(ex);
                userEmailAA = "";
            }
        }
        else
        {
            clearActionErrors();
            addActionError("The email address entered has an invalid format");
        }
    }

    public boolean hasSearchStrAA()
    {
        return (searchStrAA != null && !searchStrAA.isEmpty());
    }

    public boolean isUserFound()
    {
        return (userEmailAA != null && !userEmailAA.isEmpty());
    }

    public boolean hasAccountActivated()
    {
        return hasActivated;
    }
   
    public String getSearchStrAA ()
    {
        logger.info("getSearchStr::searchStrAA = " + searchStrAA);
        return searchStrAA;
    }


}


