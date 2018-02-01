package edu.sdsc.globusauth.util;

/**
 * Created by cyoun on 10/24/16.
 */
public final class OauthConstants {

    public static final String SESSION_ERROR_MESSAGE = "sessionErrorMsg";
    public static final String ERROR = "error";
    public static final String OAUTH_PORPS = "oauth_consumer.properties";
    public static final String SCOPES = "scopes";
    public static final String AUTH_URI = "auth_uri";
    public static final String TOKEN_SERVER_URL = "token_server_url";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String SIGNUP = "signup";
    public static final String SIGNUP_PARAM = "?signup=1";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String CODE = "code";
    public static final String OAUTH2_STATE = "oauth2_state";
    public static final String STATE = "state";

    public static final String ID_TOKEN = "id_token";
    public static final String CREDENTIALS = "credentials";
    public static final String IS_AUTHENTICATED = "is_authenticated";

    public static final String PRIMARY_USERNAME = "primary_username";
    public static final String PRIMARY_IDENTITY = "primary_identity";
    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String LINK_USERNAME = "link_username";
    public static final String SUB = "sub";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String FIRST_NAME = "first_name";
    public static final String LAST_NAME = "last_name";
    public static final String INSTITUTION = "institution";
    public static final String ENDPOINT_TYPE = "endpoint_type";
    public static final String SRC_BOOKMARK_ID = "src_bookmark_id";
    public static final String SRC_ENDPOINT_ID = "src_endpoint_id";
    public static final String SRC_ENDPOINT_PATH = "src_endpoint_path";
    public static final String SRC_ENDPOINT_NAME = "src_endpoint_name";
    public static final String SRC_DISP_NAME = "src_disp_name";
    public static final String DEST_BOOKMARK_ID = "dest_bookmark_id";
    public static final String DEST_ENDPOINT_ID = "dest_endpoint_id";
    public static final String DEST_ENDPOINT_PATH = "dest_endpoint_path";
    public static final String DEST_ENDPOINT_NAME = "dest_endpoint_name";
    public static final String DEST_DISP_NAME = "dest_disp_name";
    public static final String DATASET_ENDPOINT_ID = "dataset_endpoint_id";
    public static final String DATASET_ENDPOINT_BASE = "dataset_endpoint_base";
    public static final String DATASET_ENDPOINT_NAME = "dataset_endpoint_name";
    public static final String MYPROXY_HOST = "myproxy_host";
    public static final String LIFETIME = "lifetime";
    public static final String MKPROXY = "mkproxy_path";
    public static final String ISSUER_CRED = "issuer_cred_file";

    public static final String HTTP_GET = "GET";
    public static final String HTTP_POST = "POST";

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    public static final String CALLER = "caller";
    public static final String AUTHENTICATION_SERVER_URL = "authentication_server_url";
    //public static final String CONFIG_FILE_PATH = "/Users/cyoun/globusauth/OAuth2.0_clientCredentials/resources/com/ibm/oauth/Oauth2Client.config";
    public static final String RESOURCE_SERVER_URL = "resource_server_url";
    public static final String GRANT_TYPE = "grant_type";
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";
    public static final String BASIC = "Basic";
    public static final String JSON_CONTENT = "application/json";
    public static final String XML_CONTENT = "application/xml";
    public static final String URL_ENCODED_CONTENT = "application/x-www-form-urlencoded";

    public static final int HTTP_OK = 200;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_UNAUTHORIZED = 401;

	public static final String ENDPOINT_ACTIVATION_URI = "activate_endpoint_uri";

    //Prevents instantialtion
    private OauthConstants() {
    }

}
