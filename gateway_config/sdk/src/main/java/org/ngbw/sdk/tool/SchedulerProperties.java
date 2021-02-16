package org.ngbw.sdk.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class SchedulerProperties
{
    private static Log logger = LogFactory.getLog(SchedulerProperties.class.getName());
    
    public static final String GPU                  = "gpu";
    public static final String RESOURCECONVERSIONID                  = "resourceconversionid";
    public static final String RUNHOURS             = "runhours";
    public static final String JOBTYPE              = "jobtype";
    public static final String NODES                = "nodes";
    public static final String CHARGE_FACTOR        = "ChargeFactor"; 
    public static final String MPI_PROCESSES        = "mpi_processes";
    public static final String NODE_EXCLUSIVE       = "node_exclusive";
    public static final String THREADS_PER_PROCESS  = "threads_per_process";
    
    public static final String JOBTYPE_MPI          = "mpi";
    public static final String JOBTYPE_DIRECT       = "direct";
    
    
    /** 
     * Returns the correct charge factor base on the property 'gpu' in the 
     * configurations file.  If gpu = 1|2|4, charge factor of 1.5 is returned,
     * otherwise 1.0 is returned.
     * 
     * @param p
     * @param defaultValue
     * @return 
     */
    public static Double getChargeFactor ( Properties p, Double defaultValue ) 
    {   
        Double chargeFactor = 1.00; 
        
        try
        {
            String cf = p.getProperty(CHARGE_FACTOR); 
            
            if (cf != null && !cf.trim().isEmpty()) 
            {
                logger.debug(String.format("scheduler.conf.ChargeFactor=[%s]", cf));
                chargeFactor = Double.parseDouble(cf);
            }
            else 
            {
                logger.error("Property 'ChargeFactor' not defined in scheduler.conf.");
                chargeFactor = (SchedulerProperties.isGpu(p))? 1.50 : 1.00;
            }
        }
        catch ( NumberFormatException e )
        {
            logger.error("Error parsing ChargeFactor:\n" + e.getMessage(), e);
            chargeFactor = defaultValue;
        }
        
        return chargeFactor;
    }


    public static Double getRunHours ( Properties p, Double defaultValue )
    {
        try
        {
			logger.debug("RUNHOURS is (" + p.getProperty(RUNHOURS) + ")");
            return Double.parseDouble(p.getProperty(RUNHOURS));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }
    
    
    public static Long getGpu ( Properties p, Long defaultValue )
    {
        try
        {
            return Long.parseLong(p.getProperty(GPU));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }

    public static Long getResourceConversionId ( Properties p, Long defaultValue )
    {
        try
        {
            return Long.parseLong(p.getProperty(RESOURCECONVERSIONID));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }


    public static Long getNodes ( Properties p, Long defaultValue )
    {
        try
        {
            return Long.parseLong(p.getProperty(NODES));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }


    public static Long getMpiProcesses ( Properties p, Long defaultValue )
    {
        try
        {
            return Long.parseLong(p.getProperty(MPI_PROCESSES));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }


    public static Long getThreadsPerProcess ( Properties p, Long defaultValue )
    {
        try
        {
            return Long.parseLong(p.getProperty(THREADS_PER_PROCESS));
        }
        catch ( NumberFormatException e )
        {
            return defaultValue;
        }
    }
    
    
    public static boolean isMpi ( Properties p )
    {
        String jt = p.getProperty(JOBTYPE);
        jt = (jt == null)? "" : jt.trim();
        return jt.equals(JOBTYPE_MPI);
    }


    public static boolean isDirect ( Properties p )
    {
        String jt = p.getProperty(JOBTYPE);
        jt = (jt == null)? "" : jt.trim();
        return jt.equals(JOBTYPE_DIRECT);
    }
    
    
    public static boolean isGpu ( Properties p )
    {
        Long gpu = getGpu(p, null);
        return (gpu != null) && (gpu.intValue() > 0);
    }


    public static boolean isNodeExclusive ( Properties p )
    {
        String ne = p.getProperty(NODE_EXCLUSIVE);
        ne = (ne == null)? "" : ne.trim();
        return ne.equals("1") || ne.equalsIgnoreCase("true");
    }


    /**
     * Convert string of format name=value;name=value;name=value ... to Properties object.
     * 
     * @param pstring
     * @return 
     * @throws java.io.IOException
     */
    public static Properties string2Properties ( String pstring ) throws IOException
    {
        Properties properties = null; 
        
        if (pstring != null && !pstring.trim().isEmpty())
        {
            // Converts it to one property per line. 
            pstring = pstring.replace(';', '\n');
            
            properties = new Properties();
            properties.load(new ByteArrayInputStream(pstring.getBytes()));
        }
        
        return properties;
    }


    public static String properties2String ( Properties p ) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        
        if (p != null)
        {
            for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements();)
            {
                String name = (String) e.nextElement();
                sb.append(String.format("%s=%s;", name, p.getProperty(name)));
            }
        }
        
        return sb.toString();
    }
    
    
    public static String printInfo ( Properties p ) 
    {
        StringBuilder sb = new StringBuilder();
        sb.append("GPU = ").append(p.getProperty(GPU)).append("\n");
        sb.append("RunHours = ").append(p.getProperty(RUNHOURS)).append("\n");
        sb.append("JobType = ").append(p.getProperty(JOBTYPE)).append("\n");
        sb.append("Nodes = ").append(p.getProperty(NODES)).append("\n");
        sb.append("MPI Processes = ").append(p.getProperty(MPI_PROCESSES)).append("\n");
        sb.append("None Exclusive = ").append(p.getProperty(NODE_EXCLUSIVE)).append("\n");
        sb.append("Thread/Process = ").append(p.getProperty(THREADS_PER_PROCESS)).append("\n");
        sb.append("JobType MPI = ").append(p.getProperty(JOBTYPE_MPI)).append("\n");
        sb.append("JobType Direct = ").append(p.getProperty(JOBTYPE_DIRECT)).append("\n");
        return sb.toString();
    }

}
