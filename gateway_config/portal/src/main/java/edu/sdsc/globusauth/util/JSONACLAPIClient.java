/*
 * Modified version of Globus' BaseTransferAPIClient.java
 *
 * Author: Mona Wong, Nov 2020
 *
 */
package edu.sdsc.globusauth.util;

import org.globusonline.transfer.APIError;

import org.apache.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.Authenticator;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.rmi.AccessException;
import java.security.GeneralSecurityException;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * Basic client for interacting with the Globus Online ACL API.  Modelled
 * after org.globusonline.transfer.BaseTransferAPIClient class
 */
public class JSONACLAPIClient
{
    private static final Logger logger =
        Logger.getLogger ( JSONACLAPIClient.class. getName() );

    private Properties config = null;

    protected String accessToken = null;
    protected String baseTransferURL = null;
    protected String endpointID = null;

    protected Authenticator authenticator;

    protected boolean useMultiThreaded = false;

    protected int timeout = 30 * 1000; // 30 seconds, in milliseconds.

    //protected KeyManager[] keyManagers;
    //protected TrustManager[] trustManagers;
    protected SSLSocketFactory socketFactory;

    static final String VERSION = "v0.10";
    static final String PROD_BASE_URL =
                    "https://transfer.api.globusonline.org/" + VERSION;
    static final String QA_BASE_URL =
                    "https://transfer.qa.api.globusonline.org/" + VERSION;
    static final String TEST_BASE_URL =
                    "https://transfer.test.api.globusonline.org/" + VERSION;
    static final String DEFAULT_BASE_URL = PROD_BASE_URL;

    public static final String FORMAT_JSON = "application/json";
    public static final String FORMAT_HTML = "application/xhtml+xml";
    public static final String FORMAT_DEFAULT = FORMAT_JSON;

    static final String CLIENT_VERSION = "0.10.9";

    public static void main(String[] args) {
        /*
        JSONACLAPIClient c = new JSONACLAPIClient(args[0],
                                        JSONACLAPIClient.FORMAT_JSON);
        try {
            HttpsURLConnection r = c.request("GET",
                                "/endpoint_search?filter_scope=my-endpoints");
            JSONACLAPIClient.printResult(r);
            r.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    /**
     * Create a new authenticated (retrieve access token) Globus ACL client
     * object for the given endpoint ID.
     **/
    public JSONACLAPIClient ( String epid )
        throws AccessException, IllegalArgumentException
    {
        //logger.info ( "MONA: entered JSONACLAPIClient constructor!" );

        if ( epid == null || epid.trim().isEmpty() )
            throw new IllegalArgumentException
                ( "Invalid endpoint ID given!" );

        this.config = OauthUtils.getConfig ( OauthConstants.OAUTH_PORPS );
        //logger.info ( "MONA: config = " + config );
        this.endpointID = epid;
        this.baseTransferURL = config.getProperty
            ( OauthConstants.ENDPOINT_URL ) + "/" + this.endpointID + "/";

        if ( ! initAccessToken() )
            throw new AccessException ( "Cannot get access token" );
    }

    public JSONArray accessList()
    {
        //logger.info ( "MONA: entered accessList()" );
        //logger.info ( "MONA: endpoint_id = " + endpoint_id );

        if ( this.endpointID == null || this.endpointID.trim().isEmpty() )
            return ( null );

        JSONArray reply = null;

        try
        {
            JSONObject list = request ( "GET", this.baseTransferURL +
                "access_list", null, null, null );
            //logger.debug ( "MONA: list = " + list );

            if ( list == null )
                return ( null );

            reply = list.getJSONArray ( "DATA" );
            //logger.debug ( "MONA: reply = " + reply );
        }
        catch ( Exception e )
        {
            logger.error ( e );
            reply = null;
        }

        return ( reply );
    }


    /**
     * Delete Globus ACL for given user's uuid and directory
     * @return 1 - if successful; 0 if cannot setup ACL; -1 if incoming
     *             argument is invalid, -2 if exception
     **/
    /* Not completed!
    public int deleteACL ( String user_uuid, String user_directory )
    {
        if ( user_uuid == null || user_uuid.trim().isEmpty() ||
            user_directory == null || user_directory.trim().isEmpty() )
            return ( -1 );

        JSONArray access_list = accessList();
        String action = "access";
        JSONObject item = null;

        try
        {
            for ( int i = 0; i < access_list.length(); i++ )
                item = ( JSONObject ) access_list.get ( i );
        }
        catch ( Exception e )
        {
        }

        return ( 0 );
    }
    */

    /**
     * Create Globus ACL for given user's uuid and directory
     * @return 1 - if successful; 0 if cannot setup ACL; -1 if incoming
     *             argument is invalid, -2 if exception
     **/
    public int setupACL ( String user_uuid, String user_directory )
    {
        //logger.debug ( "MONA: entered setupACL" );
        //logger.debug ( "MONA: user_uuid = " + user_uuid );
        //logger.debug ( "MONA: user_directory = " + user_directory );

        if ( user_uuid == null || user_uuid.trim().isEmpty() ||
            user_directory == null || user_directory.trim().isEmpty() )
            return ( -1 );

        String action = "access";
        JSONObject document = null;
        JSONObject list = null;

        try
        {
            document = new JSONObject ();
            document.put ( "DATA_TYPE", action );
            document.put ( "principal_type", "identity" );
            document.put ( "principal", user_uuid );
            document.put ( "path", "/" + user_directory + "/" );
            document.put ( "permissions", "rw" );
            //document.put ( "notify_email", null );
 
            JSONObject request_properties = new JSONObject ();
            request_properties.put ( "Content-Length", "" +
                document.toString().length() );

            list = request ( "POST", this.baseTransferURL + action,
                document.toString(), null, null );

            if ( list != null && list.get ( "code" ) == "Created" )
                return ( 1 );
            else
                return ( 0 );
        }
        catch ( APIError e )
        {
            /*
            logger.error ( "MONA: APIError e = " + e );
            logger.debug ( "MONA: statusCode = " + e.statusCode );
            logger.debug ( "MONA: statusMessage = " + e.statusMessage );
            logger.debug ( "MONA: category = " + e.category );
            logger.debug ( "MONA: resource = " + e.resource );
            logger.debug ( "MONA: requestId = " + e.requestId );
            logger.debug ( "MONA: code = " + e.code );
            logger.debug ( "MONA: message = " + e.message );
            */

            // If the user's ACL already exists, we'll consider that a success
            if ( e.statusCode == 409 && e.code.equals ( "Exists" ) )
                return ( 1 );
            else
            {
                logger.error ( "Unable to setupACL for " + user_directory );
                return ( 0 );
            }
        }
        catch ( Exception e )
        {
            logger.error ( "Unable to setupACL for " + user_directory );
            return ( -2 );
        }
    }

    /**
     * Base function to send a request; if accessToken is set, will add it to
     * request but Authorization can be overwritten if that key is in the
     * request_properties parameter.  Will default to application/json for
     * both Content-Type and Accept header but can be overwritten in the 
     * request_properties.
     *
     * @param method - should be either "GET" or "POST"; if null or not "POST",
     *                 will be set to "GET"
     * @param data - Globus document to be sent in the request body; should
     *               be a JSON formatted String
     * @param request_properties - a list of key/value pairs to be set into
     *                             the request
     * @return null if there is a problem
        throws IOException, MalformedURLException, GeneralSecurityException,
        APIError
     **/
    private JSONObject request ( String method, String url_string,
        String data, JSONObject request_properties,
        Map<String, String> queryParams )
        throws APIError, IllegalArgumentException, IOException
    {
        //logger.info ( "MONA: entered request!" );
        //logger.info ( "MONA: method = " + method );
        //logger.info ( "MONA: url_string = " + url_string );
        //logger.info ( "MONA: data = " + data );
        //logger.info ( "MONA: request_properties = " + request_properties );
        //logger.info ( "MONA: queryParams = " + queryParams );

        if ( url_string == null || url_string.trim().isEmpty() )
            throw new IllegalArgumentException();

        if ( method != null )
        {
            method = method.trim();

            if ( method != "GET" || method != "POST" )
                method = null;
        }
        if ( method == null )
            method = "GET";

        initSocketFactory(false);
        
        SSLSocketFactory tempSocketFactory = this.socketFactory;
        if ( useMultiThreaded ) {
        	tempSocketFactory = createSocketFactory();
        }

        if ( queryParams != null )
            url_string += "?" + buildQueryString ( queryParams );

        URL url = new URL ( url_string );
        //logger.info ( "MONA: url = " + url );

        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setConnectTimeout(this.timeout);
        c.setSSLSocketFactory(tempSocketFactory);
        c.setRequestMethod ( method );
        c.setFollowRedirects(false);
        c.setUseCaches(false);
        c.setDoInput(true);
        //c.setDoOutput ( true );

        /* Stephen @ Globus says don't need these 2 settings...
        c.setRequestProperty ( "X-Transfer-API-X509-User", "cosmic2dev" );
        c.setRequestProperty ( "X-Transfer-API-Client",
            this.getClass().getName() + "/" + this.CLIENT_VERSION );
        */

        // If we already have an access token, use it; can be overwritten
        // if Authorization key is provided in the request_properties parameter
        //logger.info ( "MONA: accessToken = " + accessToken );
        if ( this.accessToken != null && ! this.accessToken.trim().isEmpty() )
            c.setRequestProperty ( "Authorization", "Bearer " + this.accessToken );

        // Default values but can be overwritten by request_properties
        c.setRequestProperty ( "Content-Type", this.FORMAT_JSON );
        c.setRequestProperty ( "Accept", this.FORMAT_JSON );

        // Now add the other request properties, if any...
        if ( request_properties != null && request_properties.length() > 0 )
        {
            String[] keys = JSONObject.getNames ( request_properties );
            String key, value;

            for ( int i = 0; i < keys.length; i++ )
            {
                key = keys[i];
                try
                {
                    value = request_properties.getString ( key );
                    //logger.info ( "MONA: adding request property = " + key + ":" + value );
                    c.setRequestProperty ( key, value );
                }
                catch ( Exception e )
                {
                    // should never have keys that can't be retrieved so 
                    // we will just ignore this
                }
            }
        }

        //c.connect();

        if ( data != null )
        {
            c.setDoOutput ( true );

            // From https://www.baeldung.com/httpurlconnection-post...
            OutputStream os = c.getOutputStream();
            byte[] input = data.getBytes ( "utf-8" );
            os.write ( input, 0, input.length );           
        }

        // See https://docs.globus.org/api/transfer/acl/#errors for list
        // of possible return code.  Note 409 also means that the ACL for the
        // user already exists; this can be ignored.
        int statusCode = c.getResponseCode();
        String statusMessage = c.getResponseMessage();
        //logger.info ( "MONA: statusCode = " + statusCode );
        //logger.info ( "MONA: message = " + statusMessage );
        //logger.info ( "MONA: headers = " + c.getHeaderFields() );

        if ( statusCode >= 400 )
        {
            String errorHeader = null;
            Map<String, List<String>> headers = c.getHeaderFields();
            if (headers.containsKey("X-Transfer-API-Error")) {
                errorHeader = ((List<String>)
                               headers.get("X-Transfer-API-Error")).get(0);
            }
            throw constructAPIError(statusCode, statusMessage, errorHeader,
                                    c.getErrorStream());
        }

        JSONObject response_data = null;

        try ( BufferedReader br = new BufferedReader
            ( new InputStreamReader ( c.getInputStream(), "utf-8" ) ) )
        {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ( ( responseLine = br.readLine()) != null )
                response.append ( responseLine.trim() );

            response_data = new JSONObject ( response.toString() );
        }
        catch ( Exception e )
        {
            logger.error ( e );
        };

        return ( response_data );
    }

    /**
     * Parse an error response and return an APIError instance containing
     * the error data.
     *
     * Subclasses should override this with a method that parses the
     * response body according to the format used and fills in all the fields
     * of APIError. See JSONTransferAPIClient for an example.
     */
    protected APIError constructAPIError(int statusCode, String statusMessage,
                                         String errorCode, InputStream input) {
        return new APIError(statusCode, statusMessage, errorCode);
    }

    public void setConnectTimeout(int milliseconds) {
        this.timeout = milliseconds;
    }

	/**
	 * Enables this client to be used in a multithreaded environement.
	 *
     * It seems the SSLSocketFactory is not threadsafe, which means that
     * if this class is used in a multithreaded environment there can be
     * ssl connection issues. In order to make it thread-safe we need to
     * create a SSLSocketFactory for every request.
     *
     * By default this is not done, since setting this options causes a
     * (small, but noticable) performance hit.
	 *
     * {@link ExampleParallel} shows an example of how to test multiple
     * threads.
	 *
     * @param multiThreaded whether to enable multi-thread support
	 */
    public void setUseMultiThreaded(boolean multiThreaded) {
    this.useMultiThreaded = multiThreaded; }

    protected SSLSocketFactory createSocketFactory() {
    	try {
    		SSLContext context = SSLContext.getInstance("TLS");
    		//context.init(this.keyManagers, this.trustManagers, null);
    		context.init ( null, null, null );
    		return context.getSocketFactory();
    	} catch (Exception e) {
    		throw new RuntimeException("Can't create SSLSocketFactory.", e);
    	}
    }

    protected synchronized void initSocketFactory(boolean force) {
        if (this.socketFactory == null || force) {
            this.socketFactory = createSocketFactory();
        }
    }

    public static void printResult(HttpsURLConnection c)
                    throws IOException, GeneralSecurityException, APIError {
        //logger.debug ( "MONA: entered printResult()" );
        int code = c.getResponseCode();
        String message = c.getResponseMessage();
        System.out.println(code + " " + message);
        Map<String, List<String>> headers = c.getHeaderFields();
        Iterator headerIt = headers.entrySet().iterator();
        while (headerIt.hasNext()) {
            Map.Entry pair = (Map.Entry)headerIt.next();
            String key = (String)pair.getKey();
            if (key == null)
                continue;
            List<String> valueList = (List<String>) pair.getValue();
            Iterator valuesIt = valueList.iterator();
            while (valuesIt.hasNext()) {
                //System.out.println(pair.getKey() + ": " + valuesIt.next());
                logger.debug ( "MONA: result = " + pair.getKey() + ": " + valuesIt.next() );
            }
        }

        InputStream inputStream = null;
        if (code < 400)
            inputStream = c.getInputStream();
        else
            inputStream = c.getErrorStream();
        InputStreamReader reader = new InputStreamReader(inputStream);
        BufferedReader in = new BufferedReader(reader);

        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            System.out.println(inputLine);
        }

        in.close();
    }

    public static String buildQueryString(Map<String, String> map)
        throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (first)
                first = false;
            else
                builder.append("&");
            builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    public static String endpointPath(String endpointName)
    throws UnsupportedEncodingException {
        return "/endpoint/" + URLEncoder.encode(endpointName);
    }

    // This is from Stephen @ Globus,
    // https://gist.github.com/sirosen/6b923c0854aeddc3468f423ce9c4e760
    private boolean initAccessToken ()
    {
		//logger.debug ( "MONA: entered initAccessToken()" );
        String auth_url =
            config.getProperty ( OauthConstants.TOKEN_SERVER_URL );
		//logger.debug ( "MONA: auth_url = " + auth_url );
        boolean result = false;

        try
        {
            String client_id = config.getProperty ( OauthConstants.CLIENT_ID );
            String client_secret =
                config.getProperty ( OauthConstants.CLIENT_SECRET );
            String auth = "Basic " + Base64.getEncoder().encodeToString
                ( ( client_id + ":" + client_secret ).getBytes ( StandardCharsets.UTF_8 ) );
            //logger.debug ( "MONA: auth = " + auth );
            JSONObject request_properties = new JSONObject ();
            request_properties.put ( "Authorization", auth );
            String payload =
                "grant_type=client_credentials&scope=urn:globus:auth:scope:transfer.api.globus.org:all";
            JSONObject reply = request ( "POST", auth_url, payload, 
                request_properties, null );
            //logger.debug ( "MONA: reply = " + reply );

            if ( reply != null && reply.has ( "access_token" ) )
            {
                this.accessToken = reply.getString ( "access_token" );
                this.accessToken = this.accessToken.trim();
                //logger.debug ( "MONA: accessToken = " + this.accessToken );
                result = true;
            }
        }
        catch ( Exception e )
        {
            logger.error ( e );
        }

        return ( result );
    }
}
