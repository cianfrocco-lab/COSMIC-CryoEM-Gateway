package org.ngbw.sdk;

import org.ngbw.sdk.jobs.UsageLimit;


public class UsageLimitException extends RuntimeException
{
    public UsageLimit.LimitStatus status;
    
    
    public UsageLimitException ()
    {
        super();
    }
    
    
    public UsageLimitException ( String message )
    {
        super(message);
    }
    
    
    public UsageLimitException ( String message, UsageLimit.LimitStatus status )
    {
        super(message);
        this.status = status;
    }
    
    
    public static synchronized UsageLimitException 
    buildOverActiveJobCountException ( long limit )
    {
        return new UsageLimitException(
                    String.format(
                            "Sorry, each user can have no more than %d " 
                            + "jobs running simultaneously. Please try submitting after some of your running jobs complete.", 
                            limit));
    }
    
    
    public static synchronized UsageLimitException 
    buildOverXsedeSuQuotaException ( String userType, long limit )
    {
        return new UsageLimitException(
                    String.format(
                            "Sorry, you have reached %s user XSEDE SU quota. " 
                            + "Quota is %d SUs from July 1st to June 30th each year.", 
                            userType, limit));
    }
    
    
    public static synchronized UsageLimitException 
    buildOverNonXsedeSuQuotaException ( String userType, long limit )
    {
        return new UsageLimitException(
                    String.format(
                            "Sorry, you have reached %s user Non-XSEDE SU quota. " 
                            + "Quota is %d SUs from July 1st to June 30th each year.", 
                            userType, limit));
    }
    
}
