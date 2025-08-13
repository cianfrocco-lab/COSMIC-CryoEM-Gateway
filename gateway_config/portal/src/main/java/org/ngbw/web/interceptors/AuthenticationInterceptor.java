package org.ngbw.web.interceptors;

import java.util.Map;

import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.web.actions.ManageTasks;

import org.ngbw.web.controllers.SessionController;

import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.core.shared.UserRole;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

import org.apache.log4j.Logger;

/**
 * Interceptor class to ensure that an authenticated user is registered
 * in the session before proceeding with the selected action.
 * 
 * This interceptor should be referenced by any action requiring that
 * a user be logged in, and should typically be referenced last.
 * 
 * @author Jeremy Carver
 */

 /*
 	Terri Thu Nov  7 14:22:23 PST 2013

	Added a way for an ADMIN user to impersonate any other user without knowing his password.
	To use, login as an administrator, go to "my profile" (other locations work just as well,
	but you definitely don't want to do this on the login url), and then modify url in brower, 
	adding query parameter
		impersonate=username
	and tell the browser to go to that url.  Now go to Home tab and you should see that user's folders as well
	as a "Welcome username ..." message with the name of the user you're impersonating.  

	Example url: http://localhost:7070/portal2/profile.action?impersonate=test1
	to impersonate the test1 user.  Note that you have to refresh the page to see
	test1's profile.

	Be sure to logout when done!

	TODO 
		1) figure out what happens if the user you're impersonating is logged in, or logs in
		while you're impersonating. 
		2) Implement a way to enable/disable this feature.  Maybe build time so we don't add the 
		overhead of checking something to each request.
 */
@SuppressWarnings("serial")
public class AuthenticationInterceptor extends AbstractInterceptor
{
	public static Logger logger = Logger.getLogger(AuthenticationInterceptor.class);

	@SuppressWarnings("unchecked")
    @Override
	public String intercept(ActionInvocation invocation) throws Exception 
	{
		// check the session for an authenticated user
		NgbwSupport action = (NgbwSupport)invocation.getAction();

		Map session = null;
		session = action.getSession();
        
		if (session == null)
		{
			logger.debug("No http session present, returning " + NgbwSupport.LOGIN);
			return NgbwSupport.LOGIN;
		}
        
		WorkbenchSession workbenchSession = (WorkbenchSession)session.get(NgbwSupport.WORKBENCH_SESSION);
	
		// if authenticated session present, continue.
		if (workbenchSession != null)
		{
			String userLogged  = (String)session.get(NgbwSupport.USER_LOGGED);
			if (userLogged == null)
			{
				session.put(NgbwSupport.USER_LOGGED, "true");

				String clientIp = action.getClientIp();
				Task task = (Task)session.get(ManageTasks.CURRENT_TASK);

                logger.info(String.format(
                        "User=[%s], Email=[%s] just logged in from IP=[%s], User-Agent=[%s].",
                        workbenchSession.getUsername(),
                        workbenchSession.getEmail(), clientIp,
                        action.getClientUserAgent()));

				//logger.info("JUST LOGGED IN client ip = " + clientIp + 
			    //	 is user = " + workbenchSession.getUsername() + ", email = " + workbenchSession.getEmail());

				action.dumpRequestHeaders("AuthenticationInterceptor");
				if (task != null)
				{
					logger.info("Task should be null but isn't.  Task id = " + task.getTaskId() + 
						", user = " + workbenchSession.getUsername());
				}
			}
			checkForImpersonate(invocation, workbenchSession);

			return invocation.invoke();
		}  
        
		String target = invocation.getInvocationContext().getName();
		session.put(NgbwSupport.TARGET, target);

		// This constant is defined in the struts2 ActionSupport class and equals "login"
		return NgbwSupport.LOGIN;
	}

	
	/* 
		TODO: what if user we're impersonating is logged in at the same time.
		Seems like his session gets invalidated?  Does that log him out or what?
	*/
	private void checkForImpersonate(ActionInvocation invocation, WorkbenchSession session)
	{
		// TODO: test if feature is enabled.  Need some way to only enable on my
		// desktop, or only for brief period of time by looking for a file like in disabled_resources.

		NgbwSupport action = (NgbwSupport)invocation.getAction();
		User user = action.getAuthenticatedUser();
		if (!user.getRole().equals(UserRole.ADMIN))
		{
			return;
		}
		// look for request parameter 'impersonate=username'
		String impersonateUsername;
		impersonateUsername = action.getRequestParameter("impersonate");
		if (impersonateUsername == null)
		{
			return;
		}
		SessionController controller = new SessionController(session);
		try
		{
			WorkbenchSession wbSession = controller.login(impersonateUsername);
			if (wbSession == null)
			{
				throw new Exception("workbenchsession is null");
			}
			action.setWorkbenchSession(wbSession);
			action.refreshCurrentFolder();
			logger.info("IMPERSONATING USER: " + impersonateUsername);
		}
		// todo: catch UserAuthenticationException
		catch(Exception e)
		{
			logger.debug(e.toString());
		}
	}

}
