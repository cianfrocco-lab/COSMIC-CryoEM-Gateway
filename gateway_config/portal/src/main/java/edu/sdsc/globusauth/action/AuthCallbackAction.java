package edu.sdsc.globusauth.action;
/**
 * Created by cyoun on 10/6/16.
 */

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import com.google.api.client.auth.oauth2.*;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.JsonString;
import com.google.api.client.json.JsonToken;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ArrayMap;
import com.opensymphony.xwork2.inject.Inject;
import edu.sdsc.globusauth.controller.ProfileManager;
//import edu.sdsc.globusauth.model.OauthProfile;
import org.ngbw.sdk.database.OauthProfile;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;
import org.apache.log4j.Logger;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.struts2.dispatcher.mapper.ActionMapper;
import org.apache.struts2.views.util.UrlHelper;
import org.ngbw.sdk.UserAuthenticationException;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.common.util.ValidationResult;
import org.ngbw.sdk.database.TransferRecord;
import org.ngbw.sdk.database.User;
import org.ngbw.web.actions.FolderManager;
import org.ngbw.web.controllers.SessionController;

public class AuthCallbackAction extends FolderManager {

    private static final Logger logger = Logger.getLogger(AuthCallbackAction.class.getName());

    private String id;
    private String mode;
    private String authurl;
    private String profileurl;
    private Properties config;
    // private ActionMapper actionMapper;
    // private UrlHelper urlHelper;

    private JsonFactory jsonFactory = new JacksonFactory();
    private ProfileManager profileManager;
    private OauthProfile profile;

    public AuthCallbackAction() {
        profileManager = new ProfileManager();
        profile = new OauthProfile();
    }

    public String globuslogin() throws Exception {
        // Handles the interaction with Globus Auth and does oauth flow

        // checks for errors, if so redirects back to home
        Enumeration<String> paramNames = request.getParameterNames();
        if (paramNames != null) {
            while (paramNames.hasMoreElements()) {
                if (paramNames.nextElement().contains(OauthConstants.ERROR)) {
                    logger.error("You could not be logged into the portal: " + request.getParameter(OauthConstants.ERROR));
                    //response.sendRedirect("");
                    return "failure";
                }
            }
        }

        // Set up our Globus Auth/OAuth2 state
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);
        String scopeString = config.getProperty(OauthConstants.SCOPES);
        List<String> scopes = Arrays.asList(scopeString.split(","));
        String auth_uri = config.getProperty(OauthConstants.AUTH_URI);
        GenericUrl token_server_url = new GenericUrl(config.getProperty(OauthConstants.TOKEN_SERVER_URL));
        String client_id = config.getProperty(OauthConstants.CLIENT_ID);
        String client_secret = config.getProperty(OauthConstants.CLIENT_SECRET);
        String dataset_endpoint_id = config.getProperty(OauthConstants.DATASET_ENDPOINT_ID);
        String dataset_endpoint_base = config.getProperty(OauthConstants.DATASET_ENDPOINT_BASE);
        String dataset_endpoint_name = config.getProperty(OauthConstants.DATASET_ENDPOINT_NAME);
        String endpoint_activation_uri = config.getProperty(OauthConstants.ENDPOINT_ACTIVATION_URI);

        // creates builder for flow object, necessary for oauth flow
        AuthorizationCodeFlow.Builder flowBuilder =
                new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                        new NetHttpTransport(), jsonFactory, token_server_url,
                        new BasicAuthentication(client_id, client_secret), client_id, auth_uri)
                        .setScopes(scopes);

        // checks if user logged in or signed up, if signed up then adds "?signup=1" to the url
        if (Boolean.valueOf(request.getParameter(OauthConstants.SIGNUP))) {
            flowBuilder.setAuthorizationServerEncodedUrl(
                    flowBuilder.getAuthorizationServerEncodedUrl() + OauthConstants.SIGNUP_PARAM);
        }

        // Create the flow object which mediates the Oauth flow steps
        AuthorizationCodeFlow flow = flowBuilder.build();
        String redirect_uri = config.getProperty(OauthConstants.REDIRECT_URI);

        // If there's no 'code' query string parameter, we're in this route starting a Globus Auth login
        // flow

        paramNames = request.getParameterNames();
        boolean codename_check = false;
        if (paramNames != null) {
            while (paramNames.hasMoreElements()) {
                if (paramNames.nextElement().contains(OauthConstants.CODE)) {
                    codename_check = true;
                    break;
                }
            }
        }

        if (!codename_check) {

            //String state = UUID.randomUUID().toString();
            String state = new BigInteger(130, new SecureRandom()).toString(32);

            // This is building the step 1: requesting a code
            authurl = flow.newAuthorizationUrl().setState(state).setRedirectUri(redirect_uri).build();

            // Remembers the random UUID to ensure that the same login flow continues once
            // redirected back to the client
            getSession().put(OauthConstants.OAUTH2_STATE, state);

            //response.sendRedirect(url);
            reportUserMessage("Redirect auth url: "+authurl);
            return "authredirect";

        } else {
            // If we do have a "code" param, we're coming back from Globus Auth
            // and can start the process of exchanging an auth code for a token.
            String passed_state = request.getParameter(OauthConstants.STATE);
            //logger.info("Passed state: "+passed_state);
            // Makes sure the state as the browser is sent back matches the one set when sent out from the
            // client
            if (!passed_state.isEmpty() && passed_state.equals(getSession().get(OauthConstants.OAUTH2_STATE))) {

                String code = request.getParameter(OauthConstants.CODE);
                TokenResponse tokenResponse = null;
                Boolean isErrorFree = true;
                // reportUserMessage("Got authorization code: " + code);

                try {
                    // This is step 2: exchanging the code for an Auth Token
                    tokenResponse = flow.newTokenRequest(code).setRedirectUri(redirect_uri).execute();
                } catch (IOException e) {
                    isErrorFree = false;
                    logger.error("Caught: " + e);
                    logger.error("Details: " + ((TokenResponseException) e).getDetails());
                }

                boolean redirect_flag = true;
                if (isErrorFree) {

                    getSession().remove(OauthConstants.OAUTH2_STATE);
                    // Parsing about the user
                    // logger.info("Token: " + tokenResponse.toPrettyString());
                    logger.info("Token: " + tokenResponse.toPrettyString());
                    IdToken id_token = IdToken.parse(jsonFactory, (String) tokenResponse.get(OauthConstants.ID_TOKEN));
                    logger.info("Id token: " + id_token.toString());
                    logger.info("Other tokens: "+tokenResponse.get("other_tokens"));

                    ArrayList<ArrayMap> otokens = (ArrayList<ArrayMap>) tokenResponse.get("other_tokens");
                    for (ArrayMap js: otokens) {
                        for (Object k: js.keySet())
                        logger.info("JS key: "+(String)k+" value: "+ js.get(k));
                    }

                    String name = (String) id_token.getPayload().get(OauthConstants.NAME);
                    String[] names = name.split(" ");
                    String username = (String) id_token.getPayload().get(OauthConstants.PREFERRED_USERNAME);
                    String email = (String) id_token.getPayload().get(OauthConstants.EMAIL);
                    String identity = (String) id_token.getPayload().get(OauthConstants.SUB);
                    String linkusername = null;

                    // Step 3: Create the Credential object, which stores the Auth Token
                    //Credential credentials = flow.createAndStoreCredential(tokenResponse, name);
                    //logger.info("Credential: " + credentials.refreshToken());
                    //logger.info("Credential: "+credentials.getJsonFactory().toPrettyString("other_tokens"));
                    //String accesstoken = credentials.getAccessToken();
                    String accesstoken = (String) ((ArrayMap) otokens.get(0)).get("access_token");
                    // Stores the Credential and information about user as well as flags that the user has
                    // been authenticated/logged in
                    //getSession().put(OauthConstants.CREDENTIALS, credentials);

                    /*
                    //create user directory on XSEDE repository
                    TransferAction txaction = new TransferAction(accesstoken,username);
                    logger.info("XSEDE Endpoint status......");
                    if (!txaction.endpointStatus(dataset_endpoint_id)) {
                        logger.info("XSEDE Endpoint activation....");
                        if (!txaction.delegateProxyActivation(dataset_endpoint_id)) {
                            logger.error("Unable to auto activate XSEDE endpoint, exiting");
                            return "failure";
                        }
                    }
                    txaction.createUserDir(dataset_endpoint_id, dataset_endpoint_base + username);
                    */

                    OauthProfile db_profile = profileManager.load(identity);
                    if (db_profile == null) {
                        //profile.setUserId(00001L);
                        profile.setUsername(username);
                        profile.setLinkUsername(username);
                        profile.setIdentityId(identity);
                        profile.setFirstname(names[0]);
                        profile.setLastname(names[1]);
                        profile.setEmail(email);
                        profile.setInstitution("");
                        //profile = profileManager.add(profile);
                        long userid = registerUser();
                        if ( userid == -1L) return "failure";

                        profile.setUserId(userid);
                        profileManager.addProfile(profile);

                        getSession().put("user_id",userid);
                        getSession().put(OauthConstants.EMAIL, email);
                        getSession().put(OauthConstants.FIRST_NAME, names[0]);
                        getSession().put(OauthConstants.LAST_NAME, names[1]);
                        getSession().put(OauthConstants.INSTITUTION, "");
                    } else {
                        //transfer
                        redirect_flag = false;
                        //logger.info("profile identity id:"+db_profile.getIdentityId());
                        profile.setEmail(db_profile.getEmail());
                        profile.setIdentityId(db_profile.getIdentityId());
                        profile.setLinkUsername(db_profile.getLinkUsername());
                        profile.setUserId(db_profile.getUserId());
                        if (!activateLogin(null,db_profile.getLinkUsername())) return "failure";
                        //logger.info("profile user id:"+db_profile.getUserId());
                        getSession().put("user_id",db_profile.getUserId());
                        getSession().put(OauthConstants.EMAIL, db_profile.getEmail());
                        getSession().put(OauthConstants.FIRST_NAME, db_profile.getFirstname());
                        getSession().put(OauthConstants.LAST_NAME, db_profile.getLastname());
                        getSession().put(OauthConstants.INSTITUTION, db_profile.getInstitution());

                        //return "transfer";
                    }

                    linkusername = profile.getLinkUsername();
                    getSession().put(OauthConstants.CREDENTIALS, accesstoken);
                    getSession().put(OauthConstants.ID_TOKEN, id_token);
                    getSession().put(OauthConstants.IS_AUTHENTICATED, true);
                    getSession().put(OauthConstants.PRIMARY_USERNAME, username);
                    getSession().put("link_username", linkusername);
                    getSession().put(OauthConstants.PRIMARY_IDENTITY, identity);
                    getSession().put(OauthConstants.ENDPOINT_ACTIVATION_URI, endpoint_activation_uri);

                    //initial setup for source and destination endpoint
                    getSession().put(OauthConstants.DATASET_ENDPOINT_ID,dataset_endpoint_id);
                    getSession().put(OauthConstants.DATASET_ENDPOINT_BASE,dataset_endpoint_base+linkusername+"/");
                    getSession().put(OauthConstants.DATASET_ENDPOINT_NAME,dataset_endpoint_name);

                    getSession().put(OauthConstants.DEST_BOOKMARK_ID,"XSERVER");
                    getSession().put(OauthConstants.DEST_ENDPOINT_ID,dataset_endpoint_id);
                    getSession().put(OauthConstants.DEST_ENDPOINT_PATH,dataset_endpoint_base+linkusername+"/");
                    getSession().put(OauthConstants.DEST_ENDPOINT_NAME,dataset_endpoint_name);
                    getSession().put(OauthConstants.DEST_DISP_NAME,dataset_endpoint_name);

                    //in case, the source is Comet
                    /*
                    getSession().put(OauthConstants.SRC_BOOKMARK_ID,"XSERVER");
                    getSession().put(OauthConstants.SRC_ENDPOINT_ID,dataset_endpoint_id);
                    getSession().put(OauthConstants.SRC_ENDPOINT_PATH,dataset_endpoint_base+linkusername+"/");
                    getSession().put(OauthConstants.SRC_ENDPOINT_NAME,dataset_endpoint_name);
                    getSession().put(OauthConstants.SRC_DISP_NAME,dataset_endpoint_name);
                    */

                    EndpointListAction iplistaction = new EndpointListAction(accesstoken,username);
                    //iplistaction.my_endpoint_list();
                    //List<Map<String,Object>> bookmarklist = iplistaction.getBookmarklist();
                    List<Map<String,Object>> bookmarklist = iplistaction.my_bookmark_list();
                    if (bookmarklist != null && bookmarklist.size() > 0) {
                        boolean flag = false;
                        for (int i=0; i<bookmarklist.size(); i++) {
                            Map<String, Object> bmmap = bookmarklist.get(i);
                            String bname = (String) bmmap.get("name");
                            String[] bnamea = bname.split("::");
                            if (bnamea.length == 2) {
                                flag = true;
                                if (bnamea[1].equals("SOURCE")) {
                                    //in case the source is Comet
                                    /*
                                    getSession().put(OauthConstants.SRC_BOOKMARK_ID, (String) bmmap.get("id"));
                                    getSession().put(OauthConstants.SRC_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                                    getSession().put(OauthConstants.SRC_ENDPOINT_NAME, bname);
                                    getSession().put(OauthConstants.SRC_DISP_NAME, bname.split("::")[0]);
                                    getSession().put(OauthConstants.SRC_ENDPOINT_PATH, (String) bmmap.get("path"));

                                    getSession().put(OauthConstants.DEST_BOOKMARK_ID, "XSERVER");
                                    getSession().put(OauthConstants.DEST_ENDPOINT_ID, dataset_endpoint_id);
                                    getSession().put(OauthConstants.DEST_ENDPOINT_NAME, dataset_endpoint_name);
                                    getSession().put(OauthConstants.DEST_DISP_NAME, dataset_endpoint_name);
                                    getSession().put(OauthConstants.DEST_ENDPOINT_PATH, dataset_endpoint_base + linkusername + "/");
                                    */

                                    getSession().put(OauthConstants.SRC_BOOKMARK_ID, (String) bmmap.get("id"));
                                    getSession().put(OauthConstants.SRC_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                                    getSession().put(OauthConstants.SRC_ENDPOINT_NAME, bname);
                                    getSession().put(OauthConstants.SRC_DISP_NAME, bname.split("::")[0]);
                                    getSession().put(OauthConstants.SRC_ENDPOINT_PATH, (String) bmmap.get("path"));

                                } else {
                                    getSession().put(OauthConstants.SRC_BOOKMARK_ID, "XSERVER");
                                    getSession().put(OauthConstants.SRC_ENDPOINT_ID, dataset_endpoint_id);
                                    getSession().put(OauthConstants.SRC_ENDPOINT_NAME, dataset_endpoint_name);
                                    getSession().put(OauthConstants.SRC_DISP_NAME, dataset_endpoint_name);
                                    getSession().put(OauthConstants.SRC_ENDPOINT_PATH, dataset_endpoint_base + linkusername + "/");

                                    getSession().put(OauthConstants.DEST_BOOKMARK_ID, (String) bmmap.get("id"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_PATH, (String) bmmap.get("path"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_NAME, bname);
                                    getSession().put(OauthConstants.DEST_DISP_NAME, bname.split("::")[0]);

                                    //in case, the source is Comet
                                    /*
                                    getSession().put(OauthConstants.DEST_BOOKMARK_ID, (String) bmmap.get("id"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_PATH, (String) bmmap.get("path"));
                                    getSession().put(OauthConstants.DEST_ENDPOINT_NAME, bname);
                                    getSession().put(OauthConstants.DEST_DISP_NAME, bname.split("::")[0]);
                                    */
                                }
                                break;
                            }
                        }
                        if (!flag) {
                            Map<String, Object> bmmap = bookmarklist.get(0);
                            String bm_id = (String) bmmap.get("id");
                            String bname = (String) bmmap.get("name");
                            //in case, the destination is Comet
                            /*
                            bname += "::DEST";
                            logger.info("update bookmark: "+bm_id);
                            iplistaction.updateBookmark(bm_id,bname);
                            getSession().put(OauthConstants.DEST_BOOKMARK_ID, bm_id);
                            getSession().put(OauthConstants.DEST_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                            getSession().put(OauthConstants.DEST_ENDPOINT_NAME, bname);
                            getSession().put(OauthConstants.DEST_DISP_NAME, (String) bmmap.get("disp_name"));
                            getSession().put(OauthConstants.DEST_ENDPOINT_PATH, (String) bmmap.get("path"));
                            */

                            bname += "::SOURCE";
                            logger.info("update bookmark: "+bm_id);
                            iplistaction.updateBookmark(bm_id,bname);
                            getSession().put(OauthConstants.SRC_BOOKMARK_ID, bm_id);
                            getSession().put(OauthConstants.SRC_ENDPOINT_ID, (String) bmmap.get("endpoint_id"));
                            getSession().put(OauthConstants.SRC_ENDPOINT_NAME, bname);
                            getSession().put(OauthConstants.SRC_DISP_NAME, (String) bmmap.get("disp_name"));
                            getSession().put(OauthConstants.SRC_ENDPOINT_PATH, (String) bmmap.get("path"));

                        }
                    } else {
                        //return "dataendpoints";
                        return "transfer";
                    }
                    if (redirect_flag) {
                        return "profileredirect";
                    } else {
                        //update transfer record
                        List<String> trlist = profileManager.loadRecord(profile.getUserId());
                        if (trlist != null && trlist.size() > 0) {
                            String dest_path = dataset_endpoint_base +
                                    profile.getLinkUsername() + "/";
                            TransferAction txaction = new TransferAction(accesstoken,username);
                            for (String taskid: trlist) {
                                TransferRecord tr = txaction.updateTask(taskid, null);
                                profileManager.updateRecord(tr, dest_path);
                            }
                        }
                        //return SUCCESS;
                    }
                }
            } else {
                OAuthSystemException oauth_ex = new OAuthSystemException("Mismatching Oauth States");
                reportError(oauth_ex,"Mismatching Oauth States");
                return "failure";
                // Something went wrong with state value matching
                //throw new OAuthSystemException("Mismatching Oauth States");
            }
        }
        return SUCCESS;
    }

    public long registerUser() {
        long userid = -1L;
        SessionController controller = getController();
        //checking existing user
        User existing_user = controller.getUserByEmail(profile.getEmail());
        if (existing_user != null) {
            profile.setLinkUsername(existing_user.getUsername());
            if (!activateLogin(controller,existing_user.getUsername())) {
                return userid;
            }
        } else {
            String password = "Globus" + profile.getUsername() + Calendar.getInstance().getTimeInMillis();
            ValidationResult result = controller.registerUser(profile.getUsername(), password,
                    profile.getEmail(), profile.getFirstname(), profile.getLastname(),
                    profile.getInstitution(), null);
            if (result == null) {
                addActionError("Sorry, there was an error creating your account.");
                return userid;
            }
            if (result.isValid() == false) {
                reportUserError("Sorry, there was an error creating your account:");
                for (String error : result.getErrors())
                    reportUserError(error);
                return userid;
            } else {
                addActionMessage("User account \"" + getUsername() + "\" successfully created.");
                if (finalizeLogin() != true) {
                    return userid;
                }
            }
        }

        User user = controller.getAuthenticatedUser();
        return user.getUserId();
    }
/*
    public boolean activateLogin() {
        SessionController controller = getController();
        WorkbenchSession session = null;
        try {
            session = controller.login(profile.getUserName());
        } catch (UserAuthenticationException error) {
            reportUserError(error.getMessage());
            return false;
        }
        if (session == null) {
            reportUserError("There was an error authenticating your information.");
            return false;
        }
        clearSessionAttribute(SESSION_ERROR_MESSAGE);
        if (finalizeLogin() != true) {
            return false;
        }
        return true;
    }
*/
    public boolean activateLogin(SessionController controller, String username) {
        if (controller == null)
            controller = getController();

        WorkbenchSession session = null;

        if (username == null) {
            User existing_user = controller.getUserByEmail(profile.getEmail());
            if (existing_user == null) return false;
            username = existing_user.getUsername();
            //profile.setLinkUsername(username);
            //profileManager.updateLinkUsername(profile);
            try {
                profileManager.updateLinkUsername(profile.getIdentityId(), username);
            } catch (Exception e) {
                reportUserError(e.getMessage());
                return false;
            }
        } else {
            profile.setLinkUsername(username);
        }

        try {
            session = controller.login(username);
        } catch (UserAuthenticationException error) {
            reportUserError(error.getMessage());
            return false;
        }
        if (session == null) {
            reportUserError("There was an error authenticating your information.");
            return false;
        }
        clearSessionAttribute(SESSION_ERROR_MESSAGE);
        if (finalizeLogin() != true) {
            return false;
        }
        return true;
    }

    /*
    @Inject
    public void setActionMapper(ActionMapper actionMapper) {
        this.actionMapper = actionMapper;
    }

    @Inject
    public void setUrlHelper(UrlHelper urlHelper) {
        this.urlHelper = urlHelper;
    }
    */

    public String getAuthurl() {
        return authurl;
    }

    public void setUrl(final String authurl) {
        this.authurl = authurl;
    }

    public String getProfileurl() {
        return profileurl;
    }

    public void setProfileurl(final String profileurl) {
        this.profileurl = profileurl;
    }
    public void setProfile(OauthProfile profile) {
        this.profile = profile;
    }
    public OauthProfile getProfile() {
        return profile;
    }
}
