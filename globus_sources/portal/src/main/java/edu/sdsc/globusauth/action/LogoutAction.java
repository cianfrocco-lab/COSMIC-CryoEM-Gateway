package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.web.actions.SessionManager;

import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by cyoun on 10/21/16.
 */
public class LogoutAction extends SessionManager {

    private Properties config;
    private String logouturl;

    public String globuslogout() {

        getController().logout();
        invalidateSession();

        // Set up our Globus Auth/OAuth2 state
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);

        // Destroy the session state
        if (getSession() != null)
            getSession().clear();

        // Build logout_url
        String redirect_uri = config.getProperty("logout_redirect_uri");
        String redirect_name = config.getProperty("redirect_name");
        ArrayList<String> logout_url = new ArrayList<String>();
        logout_url.add((config.getProperty("logout_uri")));
        logout_url.add(String.format("?client=%s", config.getProperty(OauthConstants.CLIENT_ID)));
        logout_url.add(String.format("&redirect_uri=%s", redirect_uri));
        logout_url.add(String.format("&redirect_name=%s", redirect_name));

        // Redirect the user to the Globus Auth logout page to finish logout
        logouturl = String.join("", logout_url);
        return SUCCESS;
    }

    public String getLogouturl() {
        return logouturl;
    }
}
