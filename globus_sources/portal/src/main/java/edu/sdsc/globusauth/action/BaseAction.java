package edu.sdsc.globusauth.action;

import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.ParameterNameAware;
import com.opensymphony.xwork2.ognl.accessor.ObjectAccessor;
import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.apache.struts2.interceptor.SessionAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Created by cyoun on 10/20/16.
 */
public class BaseAction extends ActionSupport implements ParameterNameAware, SessionAware,
        ServletRequestAware, ServletResponseAware {

    private static final Logger logger = Logger.getLogger(BaseAction.class.getName());

    private HttpServletRequest request;
    private HttpServletResponse response;
    private Map<String, Object> session;

    public String input() {
        return INPUT;
    }

    public boolean isAuthenticated() {
        Boolean authenticated = (Boolean) session.get(OauthConstants.IS_AUTHENTICATED);
        if (authenticated != null) {
            return true;
        }
        return false;
    }

    protected void invalidateSession() {
        if (session != null)
            session.clear();
    }

    protected void info(String message) {
        if (logger.isInfoEnabled()) {
            logger.info(getUsernameString() + message);
        }
    }

    public void debug(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug(getUsernameString() + message);
        }
    }

    protected void error(String message, Throwable t)
    {
        logger.error(message, t);
    }


    protected void debug(String message, Throwable error) {
        if (logger.isDebugEnabled()) {
            logger.debug(getUsernameString() + message, error);
        }
    }

    protected void reportUserMessage(String message) {
        addActionMessage(message);
        info(message);
    }

    public void reportUserError(String message) {
        addActionError(message);
        setSessionAttribute ( OauthConstants.SESSION_ERROR_MESSAGE, message );
        info(message);
    }

    protected void reportUserError(Throwable error, String message) {
        addActionError(message);
        reportError(error, message);
    }

    protected void reportCaughtError(Throwable error, String message) {
        debug(message + ": " + error.toString());
    }

    protected void reportError(Throwable error, String message) {
        debug(message + ": " + error.toString(), error);
    }

    private String getUsernameString() {
        String username = null;
        if (session != null) {
            username = (String)session.get(OauthConstants.PRIMARY_USERNAME);
        }
        if (username != null)
            username += " - ";
        else username = "";
        return username;
    }

    protected void setSessionAttribute(String attribute, Object value) {
        if (value == null)
            clearSessionAttribute(attribute);
        else if (attribute == null) return;
        else {
            if (session == null) return;
            else session.put(attribute, value);
        }
    }

    public void clearSessionAttribute(Object attribute) {
        if (attribute == null)
            return;
        else {
            if (session == null) return;
            else session.remove(attribute);
        }
    }

    public Object getSessionAttribute(Object attribute) {
        if (attribute == null) return null;
        else {
            if (session == null) return null;
            else return session.get(attribute);
        }
    }

    public Map<String,Object> getSession() {
        return session;
    }
    public HttpServletRequest getRequest() {
        return request;
    }
    public HttpServletResponse getResponse() {
        return response;
    }

    @Override
    public void setSession(final Map<String, Object> session) { this.session = session; }

    @Override
    public void setServletRequest(final HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(final HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public boolean acceptableParameterName(String parameterName) {
        boolean allowedParameterName = true ;
        if ( parameterName.contains("session")  || parameterName.contains("request") ) {
            allowedParameterName = false ;
        }
        return allowedParameterName;
    }
}
