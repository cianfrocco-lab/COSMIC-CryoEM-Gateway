package org.ngbw.web.interceptors;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

import org.apache.log4j.Logger;

import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.database.User;
import org.ngbw.web.actions.NgbwSupport;


/**
 * Interceptor class to ensure that users who try to access Administration tasks
 * have ADMIN role.
 *
 * <p>
 * This interceptor should be referenced by any action requiring that a user be
 * logged in, and should typically be referenced last.
 *
 * @author Mark Zhuang 
 */
@SuppressWarnings("serial")
public class AdministratorRoleValidator extends AbstractInterceptor
{
    public static final Logger logger = Logger.getLogger(AdministratorRoleValidator.class);


    @SuppressWarnings("unchecked")
    @Override
    public String intercept ( ActionInvocation invocation ) throws Exception
    {
        logger.debug("BEGIN: intercept(ActionInvocation)::String");

        String retVal = NgbwSupport.HOME;

        NgbwSupport action = (NgbwSupport) invocation.getAction();

        WorkbenchSession workbenchSession =
                (action.getSession() == null)? null :
                (WorkbenchSession) action.getSession().get(NgbwSupport.WORKBENCH_SESSION);

        User currUser = (workbenchSession == null)?
                        null : workbenchSession.getUser();

        if (currUser == null)
        {
            logger.debug("No user found in Session.");
            retVal = NgbwSupport.HOME;
        }
        else if (!currUser.isAdmin())
        {
            action.addActionError("You do not appropriate role to access this function!");
            retVal = NgbwSupport.HOME;
        }
        else
        {
            logger.debug("Current user has ADMIN role.");
            retVal = invocation.invoke();
        }

        logger.debug("END: intercept(ActionInvocation)::String");

        return retVal;
    }
}
