/*
 * JobCharge.java
 *
 * On trestles and gordon:
 * - jobs that run less than 10 seconds are free (this should be specified in _JOBINFO.TXT, but isn't.)
 * - charge is rounded to the nearest SU, but jobs that get rounded to 0 are charged 1.  In other
 * words, any job that runs over 10 sec is charged at least 1 SU.
 * -5/15/2014 UPDATE: looking at db where su_charged = 0 and su_computed =1, we do seem to be charged for
 * jobs that run less than 10sec.  Maybe it's more than 10 sec by the time the system is finished with it,
 * so I'm going to remove our check for < 10 seconds.  *
 * - _JOBINFO.TXT has :
 * - ChargeFactor, a float.  ChargeFactor happens to be 1 for trestles and gordon.
 * - cores, an integer >=1.  *
 * - We get runtime, ideally by subtracting the timestamp in start.txt from the timestamp in
 * done.txt (or term.txt).  However, we are having trouble reliably getting the timestamp into these files
 * when jobs timeout or are killed.  Sometimes we seem to get it, sometimes we don't.  When it
 * isn't available we'll use the runhours property from scheduler.conf, which is the max the
 * job could have run.
 *
 * Per Glenn Lockwood, the actual xsede charge is cacl'd in perl as:
 *
 * $charge = $h->{Processors} * ( $h->{WallDuration_S} / 3600.0 ) * $h->{ChargeFactor};
 * $charge = 0 if $charge < 0;
 *
 * $h{Charge} = $charge;
 *
 * if ( $h{Charge} < 1 ) {
 * $h{Charge} = 1;
 * }
 *
 * This is carried as a double precision and then rounded (either up or down) to the nearest integer before going to the XDCDB.
 * I verified this myself against a bunch of jobs and they all checked out, so you can do
 *
 * nodes * ppn * (wallhr + wallmin/60.0 + wallsec/3600.0)
 * and round to get the exact charge.
 *
 * So sounds to me(Terri) like SU must be an integer (since they round to the nearest SU)  and we can do:
 * if (seconds < 10)
 * return SU = 0;
 * return SU = min(round(cores * hours * chargeFactor), 1)
 * where hours is a double and round means round to the nearest.
 */
package org.ngbw.sdk.tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.common.util.FileUtils;
import java.lang.Math;


public class JobCharge
{
    private static final Log logger = LogFactory.getLog(JobCharge.class.getName());
    
    private static final String CHARGEFACTOR = "ChargeFactor";
    private static final String CORES = "cores";
    private static final String JOBINFO_TXT = "_JOBINFO.TXT";
    private static final String START_TXT = "start.txt";
    private static final String DONE_TXT = "done.txt";
    private static final String TERM_TXT = "term.txt";
    
    private static final String GPU = "gpu";
    private static final String SCHEDULER_CONF = "scheduler.conf";
    
    // No charge for runs < 10 seconds (convert to hours here).
    // UPDATE - not using these, since we do seem to be charged for at least some of these jobs
    private static final double MIN_CHARGE_HOURS = 10.0 / 3600.0;

    // max size we expect these files to be - we'll truncate after this:
    private static final int START_DONE_FILESIZE = 100;
    private static final int JOBINFO_FILESIZE = 4000;
    
    private String startString = null;
    private String doneString = null;
    private String termString = null;
    private String jobInfoString = null;
//    private Properties properties;
    private FileHandler fileHandler;
    private Double maxRunHours;
    String workingDir;
    String doneFilename;
    
    /*
     * Todo: we pass in pw just so we can call its readFile method. See comment in
     * BaseProcessWorker.readFile about how this should be restructured.
     */
    BaseProcessWorker processWorker;


    JobCharge ( BaseProcessWorker pw, Double maxRunHours )
    {
        this.processWorker = pw;
        this.fileHandler = pw.fh;
        this.workingDir = pw.m_workingDir;
        this.maxRunHours = maxRunHours;
    }


    /**
     * Returns Sus. 0 is a valid value if there is no charge (eg. job ran &lt; 10 seconds). Returns
     * NULL if we can't parse date from done.txt. Not throwing an exception in this case because
     * this happens too often and we don't want it to stop loadResults from working. Throws
     * exception in other cases, eg. can't read files from remote, can't find working dir, etc.
     * 
     * @return 
     * @throws java.lang.Exception
     */
    public Long getCharge () throws Exception
    {
        logger.debug("BEGIN: getCharge()::Long");
        
        Long su = 0L;
        Long gpu = null;
        int cores = 0;
        double chargeFactor = 0.00;
        
        double runHours = getRunHours();
        
        if (runHours > 0.00) 
        {
            // Load properties in scheduler.conf file. 
            Properties properties = new Properties();
            properties.load(new StringReader(readSchedulerFile()));
            
            gpu = SchedulerProperties.getGpu(properties, null);
            chargeFactor = SchedulerProperties.getChargeFactor(properties, 1.00);
            
            // If 'gpu' is 1|2|4, ==> cores = gpu 
            if (gpu != null && gpu.intValue() > 0) 
            {
                cores = gpu.intValue();
            }
            else // If not, read from _JOBINFO.TXT file ... 
            {
                properties = new Properties();
                properties.load(new StringReader(readJobInfoFile()));
                
                try {
                    cores = Integer.valueOf(properties.getProperty(CORES));
                }
                catch ( NumberFormatException e ) {
                    throw new Exception(JOBINFO_TXT + " invalid or  missing integer property: " + CORES);
                }
            }
            
            logger.debug(String.format("RunHrs[%f], GPU[%s], Cores[%d]", 
                                       runHours, ((gpu == null)? "null" : gpu.toString()), cores));
            
            su = calculateSUs(runHours, cores, chargeFactor);
        }
        
        logger.debug(String.format("Calculated SUs = [%d]", su));
        
        logger.debug("END: getCharge()::Long");
        
        return su;
    }

    
    /** 
     * Calculates SUs for a job. This is also called from Tool.getPredictedSus(). 
     * 
     * @param runHours number of hours run 
     * @param cores total cores used 
     * @param chargeFactor the charge factor 
     * @return 
     */
    public static Long calculateSUs ( double runHours, int cores, double chargeFactor )
    {
        logger.debug("BEGIN: calculateSUs(double, int, double)::Long");
        
        /*
         * if (runHours < MIN_CHARGE_HOURS) { log.debug("Run hours of " + runHours + " is less than
         * 10.0 seconds = " + MIN_CHARGE_HOURS + " hrs, so charge is 0"); return 0L; } else {
         * log.debug("runHours=" + runHours + " is not less than 10 seconds=" + MIN_CHARGE_HOURS + "
         * hours"); }
         */
        
        
        // Nicole Wolter, July 27, 2018 
        // We charge (and allocation) per GPU hour (not SUs) so no X14 anymore.
        // GPU SUs = (Number of K80 GPUs) + (Number of P100 GPUS)*1.5) x (wallclock time)
        // The min charge for any job running longer then 10 secs is 1.  But after that we round down.
        double product = (cores * runHours * chargeFactor);
        
        // Rounding the value down to an integer as Nicole Wolter stated. 
        long round = (new Double(Math.floor(product))).longValue();
        
        // Returned value is 1 or the larger one. 
        long retval = Math.max(round, 1);
        
        logger.debug(String.format("Cores[%d] * ChargeFactor[%.3f] * RunHrs[%.3f] = product[%.3f], rounds to=[%d], retval=[%d]", 
                                   cores, chargeFactor, runHours, product, round, retval));
        
        logger.debug("END: calculateSUs(double, int, double)::Long");
        
        return retval;
    }


    public Double getRunHours () throws Exception
    {
        try
        {
            return getActualRunHours();
        }
        catch ( Exception e )
        {
            if (this.maxRunHours == null)
            {
                throw new Exception("Couldn't get actual runHours and couldn't get maximum runhours from scheduler.conf");
            }
            
            logger.debug("Unable to get actual runhours: " + e.toString() 
                            + ", using maximum value of " + this.maxRunHours);
            
            return this.maxRunHours;
        }
    }


    /**
     * Returns runhours or 0L if start.txt not found. 
     * 
     * @return run hours 
     * @throws java.lang.Exception if other files not found or if unable to parse date from them
     */
    public double getActualRunHours () throws Exception
    {
        long start;
        long end;
        
        try
        {
            start = readTimeStamp(workingDir + "/" + START_TXT);
        }
        catch ( FileNotFoundException fe )
        {
            logger.info(START_TXT + " not found.  Job was probably cancelled before it started");
            return 0L;
        }
        
        try
        {
            end = readTimeStamp(workingDir + "/" + DONE_TXT);
        }
        catch ( Exception e )
        {
            end = readTimeStamp(workingDir + "/" + TERM_TXT);
        }
        
        long runSeconds = end - start;
        double runHrs = runSeconds / 3600.00;
        
        logger.debug(String.format("end[%d] - start[%d] = [%d] seconds, [%.3f] Hours", 
                                   end, start, runSeconds, runHrs));
        
        return runHrs;
    }


    /**
     * Reads the timestamp of a file. 
     * 
     * @param filename the file 
     * @return the timestamp of the file 
     * 
     * @throws Exception 
     */
    public long readTimeStamp ( String filename ) throws Exception
    {
        InputStream is = null;
        String str;
        long date;
        
        try
        {
            logger.debug("Reading TimeStamp from file: " + filename);
            is = processWorker.readFile(filename, this.fileHandler);
            
            if (is == null)
            {
                logger.debug("TimeStamp file: " + filename + " is null.");
                throw new FileNotFoundException(filename);
            }
            
            str = FileUtils.streamToString(is, START_DONE_FILESIZE);
            date = parseDate(str);
            
            logger.debug("Read TimeStamp: " + date);
            
            return date;
        }
        catch ( Exception e )
        {
            logger.warn("Error extracting date from " + filename + ": " + e.toString());
            throw e;
        }
        finally
        {
            if (is != null)
            {
                try {
                    is.close();
                }
                catch ( Exception ee ) {}
            }
        }
    }
    
    
    private String readSchedulerFile () throws FileNotFoundException, Exception
    {
        InputStream is = null;
        
        try
        {
            logger.debug("BEGIN: readSchedulerFile()::String");
            
            String fileName = workingDir + "/" + SCHEDULER_CONF;
            
            logger.debug(String.format("Reading file '%s' ...%s", fileName, ""));
            is = processWorker.readFile(fileName, this.fileHandler);
            logger.debug(String.format("Reading file '%s' ...%s", fileName, "done."));
            
            if (is == null)
            {
                throw new FileNotFoundException(SCHEDULER_CONF);
            }
            
            String jobInfo = FileUtils.streamToString(is, JOBINFO_FILESIZE);
            logger.debug(String.format("Scheduler.conf file content:\n%s", jobInfo));
            
            logger.debug("END: readSchedulerFile()::String");
            
            return jobInfo;
        }
        finally
        {
            if (is != null)
            {
                try {
                    is.close();
                }
                catch ( IOException ee ) {}
            }
        }
    }
    
    
    private String readJobInfoFile () throws FileNotFoundException, Exception
    {
        InputStream is = null;
        
        try
        {
            logger.debug("BEGIN: readJobInfoFile()::String");
            
            String fileName = workingDir + "/" + JOBINFO_TXT;
            
            logger.debug(String.format("Reading file '%s' ...%s", fileName, ""));
            is = processWorker.readFile(fileName, this.fileHandler);
            logger.debug(String.format("Reading file '%s' ...%s", fileName, "done."));
            
            if (is == null)
            {
                throw new FileNotFoundException(JOBINFO_TXT);
            }
            
            String jobInfo = FileUtils.streamToString(is, JOBINFO_FILESIZE);
            logger.debug(String.format("JobInfo file content:\n%s", jobInfo));
            
            logger.debug("END: readJobInfoFile()::String");
            
            return jobInfo;
        }
        finally
        {
            if (is != null)
            {
                try {
                    is.close();
                }
                catch ( IOException ee ) {}
            }
        }
    }


//    public String getJobInfo () throws FileNotFoundException, Exception
//    {
//        logger.debug("BEGIN: getJobInfo()::String");
//        
//        InputStream is = null;
//        
//        try
//        {
//            String fileName = workingDir + "/" + JOBINFO_TXT;
//            
//            logger.debug(String.format("Reading file '%s' ...%s", fileName, ""));
//            is = pw.readFile(fileName, this.fileHandler);
//            logger.debug(String.format("Reading file '%s' ...%s", fileName, "done."));
//            
//            if (is == null)
//            {
//                throw new FileNotFoundException(JOBINFO_TXT);
//            }
//            
//            String jobInfo = FileUtils.streamToString(is, JOBINFO_FILESIZE);
//            logger.debug(String.format("JobInfo from file:\n%s", jobInfo));
//            logger.debug("END: getJobInfo()::String");
//            
//            return jobInfo;
//        }
//        finally
//        {
//            if (is != null)
//            {
//                try {
//                    is.close();
//                }
//                catch ( IOException ee ) {}
//            }
//        }
//    }


    /**
     * First line in start.txt, done.txt, term.txt is expected to seconds since epoch - a long
     * value.
     */
    private long parseDate ( String str ) throws Exception
    {
        // Bryan put seconds since beginning of epoch in these files (Java Date(long) takes milliseconds). 
        Scanner scanner = new Scanner(str);
        return scanner.nextLong();
    }

}
