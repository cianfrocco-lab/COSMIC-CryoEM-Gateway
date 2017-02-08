package edu.sdsc.globusauth.intercept;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;
import edu.sdsc.globusauth.action.AuthCallbackAction;
import edu.sdsc.globusauth.util.OauthConstants;

import java.util.Map;

/**
 * Created by cyoun on 10/19/16.
 */
public class AuthneticationInterceptor extends AbstractInterceptor {

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        //Map<String, Object> session = ActionContext.getContext().getSession();
        Map<String, Object> session = invocation.getInvocationContext().getSession();

        if (session.containsKey(OauthConstants.IS_AUTHENTICATED)) {
            Boolean authenticated = (Boolean) session.get(OauthConstants.IS_AUTHENTICATED);

            if (authenticated != null) {
                return invocation.invoke();
            }
        }

        Object action = invocation.getAction();

        if (!(action instanceof AuthCallbackAction)) {
            return Action.LOGIN;
        }

        return invocation.invoke();
    }
}
