package edu.sdsc.globusauth.action;

/**
 * Created by cyoun on 06/25/17.
 * Updated by Mona Wong
 */

import com.google.api.client.auth.oauth2.Credential;
import edu.sdsc.globusauth.controller.ProfileManager;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;

/*
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
*/
import org.apache.log4j.Logger;

import org.globusonline.transfer.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import org.ngbw.sdk.common.util.SendError;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.TransferRecord;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.web.actions.NgbwSupport;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;

public class TransferAction extends NgbwSupport {

    private static final Logger logger =
        Logger.getLogger ( TransferAction.class.getName() );
    private JSONTransferAPIClient client;
    private Properties config;
    private List<TransferAction> files;
    private String filename;
    private String filetype;
    private Integer filesize;
    private List<String> directorynames;
    private List<String> filenames;
    private String taskId;

    //source endpoint info
    private String s_epbmid;
    private String s_epid;
    private String s_eppath;
    private String s_epname;
    private String s_dispname;

    //destination endpoint info
    private String d_epbmid;
    private String d_epid;
    private String d_eppath;
    private String d_epname;
    private String d_dispname;

    //endpoint activation uri
    private String ep_act_uri = null;

    private List<Map<String,Object>> bookmarklist;
    private String actionType;

    private Map<String,String> myendpoints;
    private String searchValue;
    private String searchLabel;
    private String myendpointName;
    private String myendpointValue;
    private Integer filecount = 0;
    private String selectedFiles;
    private String shared_endpoint_name = "COSMIC2 Shared Endpoint 2";

    // Temporary until we setup ACL for new users!
    private boolean tmp_can_transfer = true;

    public TransferAction(){
        logger.info ( "MONA: entered TransferAction constructor 1!" );
    }
    public TransferAction(String filename,String filetype,
                          Integer filesize)
    {
        logger.info ( "MONA: entered TransferAction constructor 2!" );
        this.filename = filename;
        this.filetype = filetype;
        this.filesize = filesize;
    }

    public TransferAction(String accesstoken,
                          String username) throws Exception {
        logger.info ( "MONA: entered TransferAction constructor 3!" );
        logger.info ( "MONA: accesstoken = " + accesstoken );
        logger.info ( "MONA: username = " + username );
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        logger.info ( "MONA: authenticator = " + authenticator );
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);
        logger.info ( "MONA: config = " + config );
        client = new JSONTransferAPIClient(username, null, null);
        //logger.info ( "MONA: client = " + client );
        client.setAuthenticator(authenticator);
    }

	/**
	 * Originally this code can throw an Exception.  To be more user-friendly,
	 * instead now it will display user error message and if appropriate,
	 * return ERROR.
     * public String transfer() throws Exception {
	 **/
    public String transfer()
	{
        logger.info ( "MONA: entered TransferAction.transfer()" );

        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        //logger.info ( "MONA: accesstoken = " + accesstoken );
        String globusRoot = Workbench.getInstance().getProperties().getProperty
            ( "database.globusRoot" );
        logger.info ( "MONA: globusRoot = " + globusRoot );

        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        logger.info ( "MONA: username = " + username );
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);

		try
		{
        	client = new JSONTransferAPIClient(username, null, null);
        	client.setAuthenticator(authenticator);
		}
		catch ( Exception e )
		{
			reportUserError ( "System Error: cannot get your access credential!" );
			info ( e.toString() );
			return SUCCESS;
		}

        EndpointListAction iplistaction;
		try
		{
        	iplistaction = new EndpointListAction(accesstoken, username);
        	setMyendpoints(iplistaction.my_endpoint_search_transfer("administered-by-me"));
		}
		catch ( Exception e )
		{
			reportUserError ( "System Error: cannot get endpoint listing!" );
			info ( e.toString() );
			return SUCCESS;
		}

        //logger.info("search label: "+searchLabel);
        //logger.info("search value: "+searchValue);
        //logger.info("my emdpoint name: "+myendpointName);
        //logger.info("my endpoint value: "+myendpointValue);

		try
		{
        	if ((searchLabel != null && searchLabel.trim().length() > 0) &&
                (searchValue != null && searchValue.trim().length() > 0)) {
            	bookmarklist = iplistaction.add_my_endpoint_transfer(searchLabel,searchValue);
            	if (bookmarklist.size() == 1) {
                	setIntialLocation(bookmarklist.get(0));
            	}
        	} else if ((myendpointName != null && myendpointName.trim().length() > 0) &&
                (myendpointValue != null && myendpointValue.trim().length() > 0)) {
            	bookmarklist = iplistaction.add_my_endpoint_transfer(myendpointName,myendpointValue);
            	if (bookmarklist.size() == 1) {
                	setIntialLocation(bookmarklist.get(0));
            	}
        	}
		}
		catch ( Exception e )
		{
			reportUserError ( "System Error: cannot update your bookmark!" );
			info ( e.toString() );
		}

        String transferlocation = request.getParameter("transferLocation");
        //logger.info("Exchange Location: "+transferlocation);

        if (transferlocation != null) {
            if (Boolean.valueOf(transferlocation)) {
                getSourceInfo();
                getDestinationInfo();
                if (!s_epbmid.equals("XSERVER")) {
                    s_epname = s_epname.replace("SOURCE", "DEST");
                    iplistaction.updateBookmark(s_epbmid, s_epname);
                    setDestinationInfo(s_epbmid, s_epid, s_eppath, s_epname);

                    s_epbmid = "XSERVER";
                    s_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
                    s_eppath = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
                    s_epname = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_NAME);
                    setSourceInfo(s_epbmid, s_epid, s_eppath, s_epname);

                } else {
                    d_epname = d_epname.replace("DEST", "SOURCE");
                    iplistaction.updateBookmark(d_epbmid, d_epname);
                    setSourceInfo(d_epbmid, d_epid, d_eppath, d_epname);

                    s_epbmid = "XSERVER";
                    s_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
                    s_eppath = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
                    s_epname = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_NAME);
                    setDestinationInfo(s_epbmid, s_epid, s_eppath, s_epname);
                }
            }
        }

        getSourceInfo();
        if (s_epid == null || s_epid.trim().isEmpty()) {
            bookmarklist = new ArrayList<>();
            return SUCCESS;
        }
        //in case the source is Comet
        //getDestinationInfo();
        //if (d_epid == null || d_epid.trim().isEmpty()) return "dataendpoints";

        String s_eptype = request.getParameter("bookmarkId");
        //logger.info("Transfer Bookmark ID: "+s_eptype);
        //logger.info("Transfer Action Type: "+actionType);
	
        if (s_eptype != null) {
  	        if (!s_eptype.equals("XSERVER")) {
	            String src_epid = request.getParameter("endpointId");
                String xsede_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);	
	   if (src_epid.equals(xsede_epid)) {
	      String src_name = request.getParameter("endpointName");
	      //logger.info(src_name+" is managed by the COSMIC2 gateway and in order to protect all users' data, it cannot be used as your endpoint.");
              reportUserError(src_name+" is managed by the COSMIC2 gateway and in order to protect all users' data, it cannot be used as your endpoint.");
              s_eptype = null;
	   }
         }
    	} // if (s_eptype != null)

        if (s_eptype != null) {
            if (actionType != null) {
                if (actionType.equals("List")) {
                    if (!s_eptype.equals(s_epbmid)) {
                        if (s_eptype.equals("XSERVER")) {
                            s_epname = s_epname.replace("SOURCE", "DEST");
                            iplistaction.updateBookmark(s_epbmid, s_epname);
                            setDestinationInfo(s_epbmid, s_epid, s_eppath, s_epname);

                            s_epbmid = "XSERVER";
                            s_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
                            s_eppath = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
                            s_epname = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_NAME);
                            s_dispname = s_epname;
                            setSourceInfo(s_epbmid, s_epid, s_eppath, s_epname);

                        } else {
                            if (s_epbmid.equals("XSERVER")) {

                                getDestinationInfo();
                                setDestinationInfo(s_epbmid, s_epid, s_eppath, s_epname);

                                if (s_eptype.equals(d_epbmid)) {
                                    d_epname = d_epname.replace("DEST", "SOURCE");
                                    s_eppath = request.getParameter("endpointPath");
                                    if (s_eppath != null && !s_eppath.isEmpty()) {
                                        s_eppath = s_eppath.trim();
                                        char lastChar = s_eppath.charAt(s_eppath.length() - 1);
                                        if (lastChar != '/') {
                                            s_eppath += "/";
                                        }
                                        if (d_eppath.equalsIgnoreCase(s_eppath)) {
                                            iplistaction.updateBookmark(d_epbmid, d_epname);
                                        } else {
                                            if (iplistaction.deleteBookmark(d_epbmid)) {
                                                d_epbmid = iplistaction.createBookmark(d_epname, d_epid, s_eppath);
                                            }
                                        }
                                    } else {
                                        s_eppath = d_eppath;
                                        iplistaction.updateBookmark(d_epbmid, d_epname);
                                    }

                                    s_epbmid = d_epbmid;
                                    s_epid = d_epid;
                                    //s_eppath = d_eppath;
                                    s_epname = d_epname;
                                    s_dispname = d_epname.split("::")[0];
                                    setSourceInfo(s_epbmid, s_epid, s_eppath, s_epname);

                                } else {
                                    exchangeSourceInfo(iplistaction, s_eptype, d_epbmid, d_epname, "DEST");
                                }
                            } else {
                                exchangeSourceInfo(iplistaction, s_eptype, s_epbmid, s_epname, "SOURCE");
                            }
                        }
                    } else {
                        if (!s_eptype.equals("XSERVER")) {
                            d_eppath = request.getParameter("endpointPath");
                            if (d_eppath != null && !d_eppath.isEmpty()) {
                                d_eppath = d_eppath.trim();
                                char lastChar = d_eppath.trim().charAt(d_eppath.trim().length() - 1);
                                if (lastChar != '/') {
                                    d_eppath += "/";
                                }
                                if (!d_eppath.equalsIgnoreCase(s_eppath)) {
                                    if (iplistaction.deleteBookmark(s_epbmid)) {
                                        s_eppath = d_eppath;
                                        s_epbmid = iplistaction.createBookmark(s_epname, s_epid, s_eppath);
                                        s_dispname = s_epname;
                                        setSourceInfo(s_epbmid, s_epid, s_eppath, s_epname);
                                    }
                                }
                            }
                        }
                    }
                } else if (actionType.equals("Delete")) {

					try
					{
                    	Map<String, Object> bmObj = iplistaction.removeBookmark(s_eptype);
                    	int srcType = (Integer)bmObj.get("index");
                    	if (srcType == -1 || srcType == 3) {
                        	bookmarklist = new ArrayList<>();
                        	return SUCCESS;
                    	} else if (srcType == 0) {
                        	String bid = (String) bmObj.get("id");
                        	String bname = (String) bmObj.get("name");
                        	String epid = (String) bmObj.get("endpoint_id");
                        	String path = (String) bmObj.get("path");
                        	//String d_name = (String) bmObj.get("disp_name");
                        	setSourceInfo(bid,epid,path,bname);
                        	getSourceInfo();
                    	} else if (srcType == 1) {
                        	String bid = (String) bmObj.get("id");
                        	String bname = (String) bmObj.get("name");
                        	String epid = (String) bmObj.get("endpoint_id");
                        	String path = (String) bmObj.get("path");
                        	//String d_name = (String) bmObj.get("disp_name");
                        	setDestinationInfo(bid,epid,path,bname);
                        	getDestinationInfo();
                    	}
					}
					catch ( Exception e )
					{
						reportUserError
							( "System Error: unable to delete your bookmark!" );
						info ( e.toString() );
					}
                }
            }
        }

        //iplistaction.my_endpoint_list();
        //setBookmarklist(iplistaction.getBookmarklist());
		try
		{
        	setBookmarklist(iplistaction.my_bookmark_list());
		}
		catch ( Exception e )
		{
			reportUserError ( "System Error: unable to set your bookmark!" );
			info ( e.toString() );
		}

        getSourceInfo();
        //logger.info("SRC Bookmark ID: "+s_epbmid);
        //logger.info("SRC Endpoint ID: "+s_epid);
        //logger.info("SRC Path: "+s_eppath);
        //logger.info("SRC Name: "+s_epname);
        //logger.info("SRC Display Name: "+s_dispname);
        //logger.debug ( "MONA: request = " + request );
        logger.debug ( "MONA: request.getMethod() = " + request.getMethod() );

        // Temporary until we setup ACL for new users!
        String user_root_dir = globusRoot + "/" + username;
        logger.debug ( "MONA: user_root_dir = " + user_root_dir );
        if ( ! Files.isDirectory ( Paths.get ( user_root_dir ) ) )
        {
            /*
            Properties wbProperties = Workbench.getInstance().getProperties();
            String admin = wbProperties.getProperty ( "email.adminAddr");
            User user = session.getUser();

            sendEmail ( admin, "New user directory needed!!", 
                "User " + username + " need " + user_root_dir +
                " directory and access setup!" );
            */
            reportUserError ( "Sorry, you cannot transfer to the gateway at this time.  We will enable your transfer as soon as possible and email you when ready." );
            tmp_can_transfer = false;
            logger.debug ( "MONA: User " + username + " need " + user_root_dir + " directory and access setup!" );
            //SendError.send ( "User " + username + " need " + user_root_dir + " directory and access setup!" );
        }

        if (request.getMethod().equals(OauthConstants.HTTP_GET)) {
	    //logger.info("Source Endpoint activation....");
			try
			{
            	String result = activationProcess ( username, globusRoot,
                    s_epbmid, s_epid, s_eppath, s_dispname, "source" );
            	if (result.equals(SUCCESS)) {
                	getCount(s_epid, s_eppath, s_dispname);
            	}
            	return SUCCESS;
			}
			catch ( Exception e )
			{
				reportUserError
					( "System Error: unable to activate the source endpoint \""
					+ s_dispname + "\"" );
				info ( e.toString() );
				return SUCCESS;
			}
        }
        // HTTP_POST is a transfer request...
        else if (request.getMethod().equals(OauthConstants.HTTP_POST)) {
            //logger.info ( "MONA: HTTP_POST" );

            Folder current_folder = getCurrentFolder();
            //logger.info ( "MONA: folder id = " + current_folder.getFolderId() );
            logger.info ( "MONA: label = " + current_folder.getLabel() );

            getDestinationInfo();
            //logger.info("Destination Endpoint activation....");
 
            // If we are transferring TO the gateway storage, add the
            // user folder label so that the data is organized in the same
            // way as on the gateway's UI...
            //if ( d_eppath.startsWith ( globusRoot ) )
            if ( d_epbmid.equals ( "XSERVER" ) )
                d_eppath += current_folder.getLabel() + "/";
            logger.info ( "MONA: d_eppath = " + d_eppath );

			try
			{
            	String d_result = activationProcess ( username, globusRoot,
                    d_epbmid, d_epid, d_eppath, d_dispname, "destination" );
            	logger.info ( "MONA: d_result = " + d_result );
            	if (d_result.equals("failure")) return SUCCESS;
			}
			catch ( Exception e )
			{
				reportUserError
					( "System Error: unable to activate the destination endpoint!" );
				info ( e.toString() );
			}

            List<String> filter_filenames = new ArrayList<>();
            List<String> filter_dirnames = new ArrayList<>();
            //logger.info("Selected Files: "+selectedFiles);
            String parts[] = selectedFiles.split(",");
            for (int i = 0; i < parts.length; i += 2) {
                if (parts[i].equals("folder")) {
                    filter_dirnames.add(parts[i+1]);
                } else {
                    //file
                    filter_filenames.add(parts[i+1]);
                }
            }

            logger.info ( "MONA: d_epid = " + d_epid );
			try
			{
            	JSONTransferAPIClient.Result r = client.getResult("/submission_id");
            	//logger.info ( "MONA: r = " + r );
            	String submissionId = r.document.getString("value");
            	//logger.info ( "MONA: submissionId = " + submissionId );
            	int sync_level = Integer.parseInt(config.getProperty("sync_level"));
            	boolean encrypt_data = Boolean.parseBoolean(config.getProperty("encrypt_data"));

            	JSONObject transfer = new JSONObject();
            	transfer.put("DATA_TYPE", "transfer");
            	transfer.put("submission_id", submissionId);
            	transfer.put("source_endpoint", s_epid);
            	transfer.put("destination_endpoint", d_epid);
            	transfer.put("sync_level", sync_level);
            	transfer.put("encrypt_data", encrypt_data);

            	String file_names = null;
            	String dir_names = null;
            	String delim = "";
            	logger.info ( "MONA: filter_filenames = " + filter_filenames );
            	if (filter_filenames.size() > 0) {
                	file_names = "";
                	for (String file : filter_filenames) {
                    	//logger.info("Filtered file name:" + file);
                    	file_names += delim + file;
                    	delim = "|";
                    	addTransferItem(s_eppath+file, d_eppath+file, false, transfer);
                	}
            	}
            	if (filter_dirnames.size() > 0) {
                	dir_names = "";
                	delim = "";
                	for (String dir : filter_dirnames) {
                    	//logger.info("Filtered directory name:"+dir);
                    	dir_names += delim + dir;
                    	delim = "|";
                    	addTransferItem(s_eppath+dir, d_eppath+dir, true, transfer);
                	}
            	}
            	//logger.info("File names: "+file_names);
            	//logger.info("Directory names: "+dir_names);

            	r = client.postResult("/transfer", transfer, null);
            	taskId = r.document.getString("task_id");
            	saveTask(taskId,file_names,dir_names);
            	return "transferstatus";
            	//return SUCCESS;
			}
			catch ( Exception e )
			{
				reportUserError ( "System Error: unable to transfer!" );
				info ( e.toString() );
				return SUCCESS;
			}

            /*
            //update transfer record
            new Thread() {
                public void run() {
                    while (true) {
                        try {
                            // still queued - sleep for 5 seconds
                            Thread.sleep(5000);
                            String tstat = updateRecord(taskId);
                            if (tstat.equals("SUCCEEDED") || tstat.equals("FAILED")) break;
                        } catch (Exception e) {
                            String msg = "Can't wait for job to update - " + e.getMessage();
                            logger.warn(msg);
                            break;
                        }
                    }
                }
            }.start();
            */
        } else {
            return "failure";
        }
    }

    /**
     * Due to security hole that Kenneth found, we've changed our Globus
     * endpoint to use sharing and therefore changed the activation process.
     * See
     * https://docs.google.com/document/d/1yXYBrFwzXobK8Y3uN5yD8U2koWQrD4P2Bn04ZTRBNCg/edit# for instruction
     **/
	public String activationProcess
        ( String username, String globus_root_path, String epbmid,
          String epid, String eppath, String dispname, String type )
        throws Exception
    {
        logger.info ( "MONA: entered activationProcess" );
        logger.info ( "MONA: username = " + username );
        logger.info ( "MONA: globus_root_path = " + globus_root_path );
        logger.info ( "MONA: epbmid = " + epbmid );
        logger.info ( "MONA: epid = " + epid );
        logger.info ( "MONA: eppath = " + eppath );
        logger.info ( "MONA: dispname = " + dispname );
        logger.info ( "MONA: type = " + type );

		Map<String, Boolean> ep_status = endpointStatus(epid);
        logger.info ( "MONA: ep_status 1 = " + ep_status );

        if (epbmid.equals("XSERVER")) {
            //if (!ep_status.get("activated")) {
            if ( ! ep_status.get ( "activated" ) && ! autoActivate ( epid ) )
                return "failure";

            //String user_root_dir = globus_root_path + eppath;
            //logger.info ( "MONA: user_root_dir = " + user_root_dir );

                //activateCAProxy ( "81e90a20-aa7e-11ea-8f0a-0a21f750d19b" );
                //activateCAProxy ( "de463f97-6d04-11e5-ba46-22000b92c6ec" );
                //getAppAccessToken4();

                /*
                String root_shared_endpoint_id = ( String ) getSession().get
                    ( OauthConstants.DATASET_ENDPOINT_ID );	
                logger.info ( "MONA: root_shared_endpoint_id = " + root_shared_endpoint_id );
                */

                /*
                // If it is the user's shared endpoint, just auto-activate
                if ( ! epid.equals ( root_shared_endpoint ) )
                {
                    if ( autoActivate ( epid ) )
                        return SUCCESS;
                    else
                        return "failure";
                }

                // Otherwise, it is the gateway's shared root endpoint;
                // first auto-active the root endpoint, then create user's
                // shared endpoint
                */

                //String shared_endpoint_id = null;

                /*
                boolean tmp = autoActivate ( root_shared_endpoint );
                logger.info ( "MONA: tmp = " + tmp );
                */

                /*
                if ( ! delegateProxyActivation ( epid ) ||
                    ! createUserDir ( epid, eppath ) )
                */

                /*
                if ( ! setupUserDir ( user_root_dir ) )
                {
                    logger.error ( "ERROR: cannot create user directory " +
                        user_root_dir );
                    reportUserError
                        ( "Error: your data directory cannot be created" );
                    return "failure";
                }
		        logger.debug ( "MONA: user directory created" );

                Map session = getSession();
		        logger.debug ( "MONA: session = " + session );

                String globus_user_uuid =
                    (String) getSession().get ( "primary_identity" );
		        logger.debug ( "MONA: globus_user_uuid = " + globus_user_uuid );

                //checkACL ( root_shared_endpoint_id, globus_user_uuid );
                checkACL2 ( epid );
                */

                /*
                if ( ! setupACL() )
                {
                    logger.error ( "ERROR: cannot create user directory " +
                        activation_path );
                    reportUserError
                        ( "Error: your data directory cannot be created" );
                    return "failure";
                }
                */

                /*
                if ( ( shared_endpoint_id =
                    findSharedEndpointId ( eppath ) ) == null )
                */
                /*
                if ( ( shared_endpoint_id =
                    findSharedEndpointId ( activation_path ) ) == null )
                {
                    shared_endpoint_id =
                        setupSharedEndpoint ( epid, eppath );
                        //setupSharedEndpoint ( epid, activation_path );
                }
                logger.info ( "MONA: shared_endpoint_id = " + shared_endpoint_id );

                if ( ! autoActivate ( shared_endpoint_id ) )
                    return "failure";

                if ( type.equals ( "source" ) )
                    setSourceInfo ( epbmid, shared_endpoint_id, "/",
                        shared_endpoint_name );
                else
                    setDestinationInfo ( epbmid, shared_endpoint_id, "/",
                        shared_endpoint_name );
                */
                //return SUCCESS;
            //}
            //myproxyActivation ( epid );
           	//createUserDir(epid, eppath);
        } else {
            if (!ep_status.get("activated")) {
				//My GCP endpoint
                if (!autoActivate(epid)) {
                    //logger.error("My endpoint, " + dispname + " can't be activated.");
                    reportUserMessage("Unable to auto activate an endpoint, \"" + dispname + "\". Please activate your endpoint, <a href=\"" + ep_act_uri + "\" target=\"_blank_\"> Activate </a>");
                    return "failure";
                }
            } else {
				if(ep_status.get("is_globus_connect")) {
					if(!ep_status.get("is_connected") || ep_status.get("is_paused")) {
                    	reportUserError ( "Warning, the endpoint, " +
                            dispname + ", is not connected or paused." );
						return "failure";
                	}
				}
			}
        }
        return SUCCESS;
    }

    /* Not used since we are not using the above thread code to update the
	** transfer record
    private String updateRecord(String taskId) throws Exception {
        TransferRecord tr = updateTask(taskId, null);
        ProfileManager profileManager = new ProfileManager();
        profileManager.updateTransferRecord(tr.getTaskId(),
                tr.getStatus(),
                tr.getCompletionTime(),
                tr.getFilesTransferred(),
                tr.getFaults(),
                tr.getDirectories(),
                tr.getFiles(),
                tr.getFilesSkipped(),
                tr.getByteTransferred());
        return tr.getStatus();
    }
    */

    @SuppressWarnings("unchecked")
    private List<String> getFileList(String id){
        return (List<String>) getSession().get(id);
    }

    private void getSourceInfo() {
        s_epbmid = (String) getSession().get(OauthConstants.SRC_BOOKMARK_ID);
        s_epid = (String) getSession().get(OauthConstants.SRC_ENDPOINT_ID);
        s_eppath = (String) getSession().get(OauthConstants.SRC_ENDPOINT_PATH);
        s_epname = (String) getSession().get(OauthConstants.SRC_ENDPOINT_NAME);
        s_dispname = (String) getSession().get(OauthConstants.SRC_DISP_NAME);
        //logger.info("Get SRC Bookmark ID: "+s_epbmid);
        //logger.info("Get SRC Endpoint ID: "+s_epid);
        //logger.info("Get SRC Path: "+s_eppath);
        //logger.info("Get SRC Name: "+s_epname);
    }

    private void setSourceInfo(String epbmid, String epid, String eppath, String epname){
        getSession().put(OauthConstants.SRC_BOOKMARK_ID, epbmid);
        getSession().put(OauthConstants.SRC_ENDPOINT_ID, epid);
        getSession().put(OauthConstants.SRC_ENDPOINT_PATH, eppath);
        getSession().put(OauthConstants.SRC_ENDPOINT_NAME, epname);
        getSession().put(OauthConstants.SRC_DISP_NAME, epname.split("::")[0]);
    }

    private void getDestinationInfo() {
        d_epbmid = (String) getSession().get(OauthConstants.DEST_BOOKMARK_ID);
        d_epid = (String) getSession().get(OauthConstants.DEST_ENDPOINT_ID);
        d_eppath = (String) getSession().get(OauthConstants.DEST_ENDPOINT_PATH);
        d_epname = (String) getSession().get(OauthConstants.DEST_ENDPOINT_NAME);
        d_dispname = (String) getSession().get(OauthConstants.DEST_DISP_NAME);
    }

    private void setDestinationInfo(String epbmid, String epid, String eppath, String epname) {
        getSession().put(OauthConstants.DEST_BOOKMARK_ID, epbmid);
        getSession().put(OauthConstants.DEST_ENDPOINT_ID, epid);
        getSession().put(OauthConstants.DEST_ENDPOINT_PATH, eppath);
        getSession().put(OauthConstants.DEST_ENDPOINT_NAME, epname);
        getSession().put(OauthConstants.DEST_DISP_NAME, epname.split("::")[0]);
    }

    private void exchangeSourceInfo(EndpointListAction iplistaction,
                                    String src_bm_id,
                                    String dest_bm_id,
                                    String dest_name,
                                    String indicator) {

        dest_name = dest_name.replace("::"+indicator, "");
        iplistaction.updateBookmark(dest_bm_id, dest_name);

        s_epid = request.getParameter("endpointId");
        s_eppath = request.getParameter("endpointPath");
        s_epname = request.getParameter("endpointName");
        s_dispname = s_epname;
        s_epname += "::SOURCE";
        //iplistaction.updateBookmark(src_bm_id, s_epname);
        if (s_eppath == null || s_eppath.isEmpty()) {
            s_eppath = "/~/";
        } else {
            s_eppath = s_eppath.trim();
            char lastChar = s_eppath.charAt(s_eppath.length() - 1);
            if (lastChar != '/') {
                s_eppath += "/";
            }
        }
        if (iplistaction.deleteBookmark(src_bm_id)) {
            src_bm_id = iplistaction.createBookmark(s_epname, s_epid, s_eppath);
        }
        setSourceInfo(src_bm_id,s_epid,s_eppath,s_epname);
        //getSession().put(OauthConstants.SRC_BOOKMARK_ID, s_epbmid);
        //getSession().put(OauthConstants.SRC_ENDPOINT_ID, s_epid);
        //getSession().put(OauthConstants.SRC_ENDPOINT_PATH, s_eppath);
        //getSession().put(OauthConstants.SRC_ENDPOINT_NAME, s_epname);
    }

    private void setIntialLocation(Map<String, Object> bmmap) {
        String si_bid = (String) bmmap.get("id");
        String si_bname = (String) bmmap.get("name");
        String si_epid = (String) bmmap.get("endpoint_id");
        String si_path = (String) bmmap.get("path");
        //String si_d_name = (String) bmmap.get("disp_name");
        setSourceInfo(si_bid, si_epid, si_path, si_bname);

        String di_epbmid = "XSERVER";
        String di_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
        String di_eppath = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
        String di_epname = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_NAME);
        setDestinationInfo(di_epbmid, di_epid, di_eppath, di_epname);
    }

    private String findSharedEndpointId ( String path )
    {
		logger.debug ( "MONA: entered searchSharedEndpoint()" );
        logger.info ( "MONA: path = " + path );

        String username = config.getProperty ( OauthConstants.USERNAME );
        logger.info ( "MONA: username = " + username );
        String primary_username =
            ( String ) getSession().get ( OauthConstants.PRIMARY_USERNAME );
        logger.info ( "MONA: primary_username = " + primary_username );
        String host_epid =
            ( String ) getSession().get ( OauthConstants.DATASET_ENDPOINT_ID );
        logger.info ( "MONA: host_epid = " + host_epid );
        /* Can't set filter_host_endpoint search parameter as it returns:
         * PermissionDenied(403 Forbidden) on request 'tQUwtxUbI' resource '/endpoint_search': Not authorized for that endpoint
        String resource =
            "/endpoint_search?filter_fulltext=COSMIC2 shared endpoint&filter_host_endpoint=" +
            host_epid;
        */
        String resource =
            "/endpoint_search?filter_fulltext=" + shared_endpoint_name;
        logger.info ( "MONA: resource = " + resource );

        try
        {
            JSONTransferAPIClient.Result r = client.getResult ( resource );
            logger.info ( "MONA: r.document = " + r.document );
            String host_endpoint_id = null;
            String host_path = null;
            String owner_string = null;
            JSONArray data = r.document.getJSONArray ( "DATA" );

            for ( int i = 0; i < data.length(); i++ )
            {
                JSONObject item = data.getJSONObject ( i ); 
                host_endpoint_id = item.getString ( "host_endpoint_id" );
                logger.info ( "MONA: host_endpoint_id = " + host_endpoint_id );
                host_path = item.getString ( "host_path" );
                logger.info ( "MONA: host_path = " + host_path );
                owner_string = item.getString ( "owner_string" );
                logger.info ( "MONA: owner_string = " + owner_string );

                if ( host_epid.equals ( host_endpoint_id ) &&
                    primary_username.equals ( owner_string ) )
                {
                    if ( path != null && path.equals ( host_path ) )
                        return ( item.getString ( "id" ) );
                }
            }

            return ( null );
        }
        catch ( Exception e )
        {
            logger.error ( "ERROR: findSharedEndpointId : " + e.toString() );
            return ( null );
        }
    }

    // From https://www.logicbig.com/tutorials/core-java-tutorial/net/url-connection-basic-authentication.html
    /*
    private String getAppAccessToken()
    {
		logger.debug ( "MONA: entered getAppAccessToken()" );
        try
        {
            URL myURL = new URL ( "https://auth.globus.org/v2/oauth2/token" );
		    logger.debug ( "MONA: myURL = " + myURL );
            URLConnection c = myURL.openConnection();

            String authStr = Base64.getEncoder().encodeToString
                ( "f71acbf8-a984-4a33-bfe5-c27b543c01ba:3B4cyAnv5KdR8UrTI1WFSqEselsnern+cZoN1sLD968=".getBytes() );
		    logger.debug ( "MONA: authStr = " + authStr );

            //setting Authorization header
            c.setRequestProperty("Authorization", "Basic " + authStr);
            Map<String, List<String>> headers = c.getHeaderFields();
		    logger.debug ( "MONA: headers = " + headers );
            System.out.println("-- Response headers --");
            headers.entrySet().forEach(e -> System.out.printf("%s: %s%n", e.getKey(), e.getValue()));

            System.out.println("-- Response body --");
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(c.getInputStream()))) {
                //reader.lines().forEach(System.out::println);
                reader.lines().forEach(System.out::println);
            }
            return ( "something" );
        }
        catch ( Exception e )
        {
            logger.error ( "ERROR: getAppAccessToken: " + e.toString() );
            return ( null );
        }
    }

    // From https://mkyong.com/java/apache-httpclient-examples/
    private String getAppAccessToken2()
    {
		logger.debug ( "MONA: entered getAppAccessToken2()" );

        HttpPost post =
            new HttpPost ( "https://auth.globus.org/v2/oauth2/token" );
        //post.setEntity ( "grant_type=client_credentials&scope=urn:globus:auth:scope:transfer.api.globus.org:all" );
        CredentialsProvider provider = new BasicCredentialsProvider();
        credsProvider.setCredentials ( AuthScope.ANY,
            new UsernamePasswordCredentials
            ( "f71acbf8-a984-4a33-bfe5-c27b543c01ba",
            "3B4cyAnv5KdR8UrTI1WFSqEselsnern+cZoN1sLD968=" ) );

        try
        (
            CloseableHttpClient httpClient =
                HttpClientBuilder.create().setDefaultCredentialsProvider
                ( provider ).build();
            CloseableHttpResponse response = httpClient.execute ( post ) )
            {
                // 401 if wrong user/password
                logger.debug ( "MONA: code = " + response.getStatusLine().getStatusCode() );

                HttpEntity entity = response.getEntity();
                if ( entity != null )
                {
                    // return it as a String
                    String result = EntityUtils.toString ( entity );
                    logger.debug ( "MONA: result = " + result );
                }
            }

            return ( "something" );
    }
    */

    private boolean getAppAccessToken3()
    {
		logger.debug ( "MONA: entered getAppAccessToken3()" );
        try
        {
            URL url = new URL ( "https://auth.globus.org/v2/oauth2/token" );
            logger.info ( "MONA: url = " + url );

            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            //c.setConnectTimeout(this.timeout);
            //c.setSSLSocketFactory(tempSocketFactory);
            c.setRequestMethod( "POST" );
            c.setFollowRedirects(false);
            c.setUseCaches(false);
            c.setDoInput(true);
            /*
            c.setRequestProperty("X-Transfer-API-X509-User", this.username);
            logger.info ( "MONA: username = " + this.username );
            c.setRequestProperty("X-Transfer-API-Client", this.getClass().getName()
            + "/" + this.CLIENT_VERSION);
            c.setRequestProperty("Accept", this.format);

            if (this.authenticator != null) {
                this.authenticator.authenticateConnection(c);
            }
            */

            String data =
                "grant_type=client_credentials&scope=urn:globus:auth:scope:transfer.api.globus.org:all";
            if (data != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain" );
                c.setRequestProperty("Content-Length", "" + data.length());
            }
            c.connect();
            if (data != null) {
                DataOutputStream out = new DataOutputStream(c.getOutputStream());
                out.writeBytes(data);
                out.flush();
                out.close();
            }
            int statusCode = c.getResponseCode();
            logger.info ( "MONA: statusCode = " + statusCode );
            return ( true );
        }
        catch ( Exception e )
        {
		    logger.debug ( "MONA: e = " + e );
            return ( false );
        }
    }

    private void getAppAccessToken4()
    {
		logger.debug ( "MONA: entered getAppAccessToken4()" );
        try
        {
            URL url = new URL ( "https://auth.globus.org/v2/oauth2/token" );
		    logger.debug ( "MONA: url = " + url );
            URLConnection uc = url.openConnection();
            String userpass = "f71acbf8-a984-4a33-bfe5-c27b543c01ba:3B4cyAnv5KdR8UrTI1WFSqEselsnern+cZoN1sLD968=";
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
		    logger.debug ( "MONA: basicAuth = " + basicAuth );
            uc.setRequestProperty ("Authorization", basicAuth);
            InputStream in = uc.getInputStream();
		    logger.debug ( "MONA: in = " + in );
        }
        catch ( Exception e )
        {
            logger.debug ( "MONA: e = " + e );
        }
    }

    private boolean checkACL
        ( String root_shared_endpoint_id, String globus_user_uuid )
    {
		logger.debug ( "MONA: entered checkACL()" );
		logger.debug ( "MONA: root_shared_endpoint_id = " + root_shared_endpoint_id );
		logger.debug ( "MONA: globus_user_uuid = " + globus_user_uuid );

        if ( root_shared_endpoint_id == null ||
            root_shared_endpoint_id.isEmpty() || globus_user_uuid == null ||
            globus_user_uuid.isEmpty() )
            return ( false );

        Authenticator authenticator =
            new GoauthAuthenticator ( "x" );
            //new GoauthAuthenticator ( "9GqGSY4IuXpAw9cQKNFvjeoASrslMp" );
        //config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);

		try
		{
        	JSONTransferAPIClient client = new JSONTransferAPIClient ( "cosmic2@xsede.org", null, null);
        	client.setAuthenticator(authenticator);

            String resource = BaseTransferAPIClient.endpointPath
                ( root_shared_endpoint_id ) + "/access/" +
                "35821616-15e7-46c8-b6ff-fc24a899859a";
            logger.info ( "MONA: resource = " + resource );
            //JSONObject dir_param = new JSONObject();
            /*
            dir_param.put ( "DATA_TYPE", "access" );
            dir_param.put ( "principal_type", "identity" );
            dir_param.put ( "principal", globus_user_uuid );
            dir_param.put ( "path", subdir );
            dir_param.put ( "permission", true );
            dir_param.put ( "notify_email",
                Workbench.getInstance().getProperties().getProperty
                ( "email.adminAddr" ) );
            logger.info ( "MONA: dir_param = " + dir_param );
            */

            JSONTransferAPIClient.Result r = client.getResult ( resource );
            logger.info ( "MONA: r.document = " + r.document );
            /*
            String code = r.document.getString ( "code" );
            logger.info ( "MONA: code = " + code );
            if ( code.equals ( "Created" ) )
            {
                String shared_endpoint_id = r.document.getString ( "id" );
                logger.info ( "MONA: shared_endpoint_id = " + shared_endpoint_id );
                logger.info ( r.document.getString ( "message" ) + " " +
                    shared_endpoint_id );

                return ( shared_endpoint_id );
            }
            */

            return ( false );
        }
        catch ( Exception e )
        {
            logger.info ( "ERROR: checkACL: " + e.toString() );
            return ( false );
        }
    }

    //throws IOException, JSONException, GeneralSecurityException, APIError {
    private boolean checkACL2 ( String endpointId )
    {
		logger.debug ( "MONA: entered checkACL2()" );
		logger.debug ( "MONA: endpointId = " + endpointId );

        if ( endpointId == null || endpointId.isEmpty() )
            return ( false );

        try
        {
            String resource =
                BaseTransferAPIClient.endpointPath ( endpointId ) +
                "/access_list";
                //"/access/" + "35821616-15e7-46c8-b6ff-fc24a899859a";
		    logger.debug ( "MONA: resource = " + resource );
            JSONTransferAPIClient.Result r = client.getResult(resource);
		    logger.debug ( "MONA: r.document = " + r.document );
            JSONArray data = r.document.getJSONArray("DATA");
		    logger.debug ( "MONA: data = " + data );
            /*
            String pub_key = null;
            for (int i=0; i< data.length(); i++) {
                String ar_type = data.getJSONObject(i).getString("type");
                String ar_name = data.getJSONObject(i).getString("name");
                //logger.info(i+" type: "+ar_type);
                //logger.info(i+" name: "+ar_name);
                if (ar_type.equalsIgnoreCase("delegate_proxy") &&
                    (ar_name.equalsIgnoreCase("public_key"))) {
                pub_key = data.getJSONObject(i).getString("value");
                break;
                }
            }
            return pub_key;
            */
            return ( false );
        }
        catch ( Exception e )
        {
		    logger.debug ( "MONA: e = " + e );
            return ( false );
        }
    }

    private boolean setupACL ( String globus_user_uuid, String subdir )
    {
		logger.debug ( "MONA: entered setupACL()" );
		logger.debug ( "MONA: globus_user_uuid = " + globus_user_uuid );
		logger.debug ( "MONA: subdir = " + subdir );

        if ( globus_user_uuid == null || globus_user_uuid.isEmpty() || subdir == null ||
            subdir.isEmpty() )
            return ( false );

        String resource = "/access";
        logger.info ( "MONA: resource = " + resource );
        JSONObject dir_param = new JSONObject();

        try
        {
            dir_param.put ( "DATA_TYPE", "access" );
            dir_param.put ( "principal_type", "identity" );
            dir_param.put ( "principal", globus_user_uuid );
            dir_param.put ( "path", subdir );
            dir_param.put ( "permission", true );
            dir_param.put ( "notify_email",
                Workbench.getInstance().getProperties().getProperty
                ( "email.adminAddr" ) );
            logger.info ( "MONA: dir_param = " + dir_param );

            JSONTransferAPIClient.Result r = client.postResult ( resource, dir_param, null);
            logger.info ( "MONA: r.document = " + r.document );
            String code = r.document.getString ( "code" );
            /*
            logger.info ( "MONA: code = " + code );
            if ( code.equals ( "Created" ) )
            {
                String shared_endpoint_id = r.document.getString ( "id" );
                logger.info ( "MONA: shared_endpoint_id = " + shared_endpoint_id );
                logger.info ( r.document.getString ( "message" ) + " " +
                    shared_endpoint_id );

                return ( shared_endpoint_id );
            }
            */

            return ( false );
        }
        catch ( Exception e )
        {
            String error_msg = e.toString();
            logger.info ( "MONA: error_msg = " + error_msg );
            return ( false );
        }
    }

    /**
     * Create a new shared endpoint from the given endpointId for the given
     * path.  Note, shared endpoint can only be created from a host endpoint,
     * not another shared endpoint.
     * @return the new shared endpoint ID or null if there is a problem
     **/
    private String setupSharedEndpoint ( String endpointId, String path )
    {
		logger.debug ( "MONA: entered setupSharedEndpoint()" );
		logger.debug ( "MONA: endpointId = " + endpointId );
		logger.debug ( "MONA: path = " + path );

        String resource = "/shared_endpoint";
        logger.info ( "MONA: resource = " + resource );
        JSONObject dir_param = new JSONObject();

        try
        {
            // See
            // https://docs.globus.org/api/transfer/endpoint/#endpoint_updatable_fields
            // for a list of fields for shared endpoints 
            dir_param.put ( "DATA_TYPE", "shared_endpoint" );
            dir_param.put ( "display_name", shared_endpoint_name );
            dir_param.put ( "host_endpoint", endpointId );
            dir_param.put ( "host_path", path );
            dir_param.put ( "force_encryption", true );
            dir_param.put ( "contact_email",
                Workbench.getInstance().getProperties().getProperty
                ( "email.adminAddr" ) );
            dir_param.put ( "disable_verify", false );
            logger.info ( "MONA: dir_param = " + dir_param );

            JSONTransferAPIClient.Result r = client.postResult ( resource, dir_param, null);
            logger.info ( "MONA: r.document = " + r.document );
            String code = r.document.getString ( "code" );
            logger.info ( "MONA: code = " + code );
            if ( code.equals ( "Created" ) )
            {
                String shared_endpoint_id = r.document.getString ( "id" );
                logger.info ( "MONA: shared_endpoint_id = " + shared_endpoint_id );
                logger.info ( r.document.getString ( "message" ) + " " +
                    shared_endpoint_id );

                return ( shared_endpoint_id );
            }

            return ( null );
        }
        catch ( Exception e )
        {
            String error_msg = e.toString();
            logger.info ( "MONA: error_msg = " + error_msg );
            return ( null );
        }
    }

    public boolean autoActivate(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
		logger.debug ( "MONA: entered autoActivate()" );
		logger.debug ( "MONA: endpointId = " + endpointId );
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
            + "/autoactivate";
		//logger.debug ( "MONA: resource = " + resource );
        JSONTransferAPIClient.Result r = client.postResult(resource, null,
                null);
		logger.debug ( "MONA: r.document = " + r.document );
        String code = r.document.getString("code");
		//logger.debug ( "MONA: code = " + code );
        if (code.startsWith("AutoActivationFailed")) {
            ep_act_uri = (String) getSession().get(OauthConstants.ENDPOINT_ACTIVATION_URI);
            ep_act_uri = ep_act_uri.replace(":endpoint_id",endpointId);
            return false;
        }
        return true;
    }

    public Map<String,Boolean> endpointStatus(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
		//logger.debug ( "MONA: entered endpointStatus()" );
		//logger.debug ( "MONA: endpointId = " + endpointId );
        String resource = BaseTransferAPIClient.endpointPath(endpointId);
		//logger.debug ( "MONA: resource = " + resource );
        JSONTransferAPIClient.Result r = client.getResult(resource);

        Map<String,Boolean> ep_status = new HashMap<>();
        boolean activated = r.document.getBoolean("activated");
        boolean is_connected = false;
        boolean is_paused = false;
        boolean is_globus_connect = r.document.getBoolean("is_globus_connect");
		if (is_globus_connect) {
        	is_connected = r.document.getBoolean("gcp_connected");;
        	is_paused = r.document.getBoolean("gcp_paused");
		}

        //logger.info("activated: "+activated);
        //logger.info("is_globus_connect: "+is_globus_connect);
        //logger.info("is_connected: "+is_connected);
        //logger.info("is_paused: "+is_paused);
		/*
        JSONArray data = r.document.getJSONArray("DATA");
        for (int i=0; i< data.length(); i++) {
        	is_connected = data.getJSONObject(i).getBoolean("is_connected");
            is_paused = data.getJSONObject(i).getBoolean("is_paused");

            logger.info(i+" is_connected: "+is_connected);
            logger.info(i+" is_paused: "+is_paused);
        }
		*/
        ep_status.put("activated",activated);
        ep_status.put("is_globus_connect",is_globus_connect);
        ep_status.put("is_connected",is_connected);
        ep_status.put("is_paused",is_paused);

        return ep_status;

        //if (activated && is_connected && is_paused) return true;
        //return false;
    }

    public boolean myproxyActivation(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError, NullPointerException {
		logger.debug ( "MONA: entered myproxyActivation()" );

        String myproxy_host = config.getProperty(OauthConstants.MYPROXY_HOST);
		//logger.debug ( "MONA: myproxy_host = " + myproxy_host );
        String username = config.getProperty(OauthConstants.USERNAME);
		//logger.debug ( "MONA: username = " + username );
        String passwd = config.getProperty(OauthConstants.PASSWORD);
        String lifetime = config.getProperty(OauthConstants.LIFETIME);
		//logger.debug ( "MONA: lifetime = " + lifetime );

        JSONObject req_body = new JSONObject();
        req_body.put("DATA_TYPE", "activation_requirements");
        JSONArray data_list = new JSONArray();
        req_body.put("DATA", data_list);
        addActivationItem("hostname","MyProxy Server","myproxy",myproxy_host,data_list);
        addActivationItem("username","Username","myproxy",username,data_list);
        addActivationItem("passphrase","Passphrase","myproxy",passwd,data_list);
        addActivationItem("lifetime_in_hours","Credential Lifetime (hours)","myproxy",lifetime,data_list);

        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activate";
		//logger.debug ( "MONA: resource = " + resource );
        JSONTransferAPIClient.Result r = client.postResult(resource, req_body,
                null);
        String code = r.document.getString("code");
		//logger.debug ( "MONA: code = " + code );
        if (!code.startsWith("Activated.MyProxyCredential")) {
            return false;
        }
        return true;
    }

    public boolean delegateProxyActivation(String endpointId) throws Exception {
		logger.debug ( "MONA: entered delegateProxyActivation()" );
        String proxy_chain = createProxyFromFile(endpointId);
		//logger.debug ( "MONA: proxy_chain = " + proxy_chain );
        JSONObject req_body = new JSONObject();
        req_body.put("DATA_TYPE", "activation_requirements");
		//logger.debug ( "MONA: req_body 1 = " + req_body );
        JSONArray data_list = new JSONArray();
        req_body.put("DATA", data_list);
		//logger.debug ( "MONA: req_body 2 = " + req_body );
        addActivationItem("proxy_chain","Proxy Chain","delegate_proxy",proxy_chain,data_list);
		//logger.debug ( "MONA: data_list = " + data_list );
		//logger.debug ( "MONA: req_body 3 = " + req_body );

        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activate";
		logger.debug ( "MONA: resource = " + resource );
		logger.debug ( "MONA: req_body = " + req_body );
        JSONTransferAPIClient.Result r = client.postResult(resource, req_body,
                null);
        logger.info ( "MONA: r.document = " + r.document );
        String code = r.document.getString("code");
		logger.debug ( "MONA: code = " + code );
        if (!code.startsWith("Activated.ClientProxyCredential")) {
            return false;
        }
        return true;
    }

    /* Doesn't work as XSEDE does NOT allow community account to authenticate
     * using this mechanism
     */
    public boolean activateCAProxy ( String endpointId ) throws Exception
    {
		logger.debug ( "MONA: entered activateCAProxy()" );
		logger.debug ( "MONA: endpointId = " + endpointId );

        if ( endpointId == null || endpointId.isEmpty() )
            return ( false );

        JSONTransferAPIClient c =
            new JSONTransferAPIClient ( "a191b50a-c1cf-4eb8-b52b-22bff7679744@clients.auth.globus.org" );
        String proxy_chain = createProxyFromFile2(c, endpointId);
		logger.debug ( "MONA: proxy_chain = " + proxy_chain );
        JSONObject req_body = new JSONObject();
        req_body.put("DATA_TYPE", "activation_requirements");
		//logger.debug ( "MONA: req_body 1 = " + req_body );
        JSONArray data_list = new JSONArray();
        req_body.put("DATA", data_list);
		//logger.debug ( "MONA: req_body 2 = " + req_body );
        addActivationItem("proxy_chain","Proxy Chain","delegate_proxy",proxy_chain,data_list);
		logger.debug ( "MONA: data_list = " + data_list );
		//logger.debug ( "MONA: req_body 3 = " + req_body );

        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activate";
		logger.debug ( "MONA: resource = " + resource );
		//logger.debug ( "MONA: req_body = " + req_body );
		logger.debug ( "MONA: c = " + c );
        JSONTransferAPIClient.Result r = c.postResult(resource, req_body,
                null);
        logger.info ( "MONA: r.document = " + r.document );
        String code = r.document.getString("code");
		logger.debug ( "MONA: code = " + code );
        if (!code.startsWith("Activated.ClientProxyCredential")) {
            return false;
        }
        return true;
    }

    /**
     * Deactivate the given endpoint
     * @return true if successful; false otherwise
     **/
    private boolean deactivateEndpoint ( String endpointId )
        throws Exception
    {
		logger.debug ( "MONA: entered delegateProxyDeactivation()" );
        String resource = BaseTransferAPIClient.endpointPath ( endpointId )
                + "/deactivate";
        JSONTransferAPIClient.Result r =
            client.postResult ( resource, null, null);
        String code = r.document.getString ( "code" );
		logger.debug ( "MONA: code = " + code );
        if ( code.equals ( "Deactivated" ) || code.equals ( "NotActivated" ) )
            return true;
        else
            return false;
    }

    private String getPublicKey(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
		logger.debug ( "MONA: entered getPublicKey()" );
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activation_requirements";
		logger.debug ( "MONA: resource = " + resource );
        JSONTransferAPIClient.Result r = client.getResult(resource);
        JSONArray data = r.document.getJSONArray("DATA");
        String pub_key = null;
        for (int i=0; i< data.length(); i++) {
            String ar_type = data.getJSONObject(i).getString("type");
            String ar_name = data.getJSONObject(i).getString("name");
            //logger.info(i+" type: "+ar_type);
            //logger.info(i+" name: "+ar_name);
            if (ar_type.equalsIgnoreCase("delegate_proxy") &&
                    (ar_name.equalsIgnoreCase("public_key"))) {
                pub_key = data.getJSONObject(i).getString("value");
                break;
            }
        }
        return pub_key;
    }

    private String getPublicKey2(JSONTransferAPIClient c, String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
		logger.debug ( "MONA: entered getPublicKey2()" );
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activation_requirements";
		logger.debug ( "MONA: resource = " + resource );
        JSONTransferAPIClient.Result r = c.getResult(resource);
		logger.debug ( "MONA: r.document = " + r.document );
        JSONArray data = r.document.getJSONArray("DATA");
		logger.debug ( "MONA: data = " + data );
        String pub_key = null;
        for (int i=0; i< data.length(); i++) {
            String ar_type = data.getJSONObject(i).getString("type");
            String ar_name = data.getJSONObject(i).getString("name");
            //logger.info(i+" type: "+ar_type);
            //logger.info(i+" name: "+ar_name);
            if (ar_type.equalsIgnoreCase("delegate_proxy") &&
                    (ar_name.equalsIgnoreCase("public_key"))) {
                pub_key = data.getJSONObject(i).getString("value");
                break;
            }
        }
        return pub_key;
    }

    private void pipeStream(InputStream input, OutputStream output) throws IOException {
        byte buffer[] = new byte[1024];
        int numRead = 0;

        do
        {
            numRead = input.read(buffer);
            output.write(buffer, 0, numRead);
        } while (input.available() > 0);
        output.flush();
    }

    private String createProxyFromFile(String endpointId) throws Exception {
		logger.debug ( "MONA: entered createProxyFromFile()" );
		logger.debug ( "MONA: endpointId = " + endpointId );
        String mkproxy_path = config.getProperty(OauthConstants.MKPROXY);
		//logger.debug ( "MONA: mkproxy_path = " + mkproxy_path );
        String issuer_cred_file = config.getProperty(OauthConstants.ISSUER_CRED);
		logger.debug ( "MONA: issuer_cred_file = " + issuer_cred_file );
        String lifetime = config.getProperty(OauthConstants.LIFETIME);
		logger.debug ( "MONA: lifetime = " + lifetime );
        String pub_key = getPublicKey(endpointId);
		logger.debug ( "MONA: pub_key = " + pub_key );

        StringBuffer sb = new StringBuffer();
        FileInputStream user_cert = new FileInputStream(issuer_cred_file);
        ByteArrayInputStream pub_cert = new ByteArrayInputStream(pub_key.getBytes());
        String[] cmd_array = { mkproxy_path, lifetime };

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd_array);
        Process p = pb.start();

        final InputStream errStream = p.getErrorStream();
        new Thread(new Runnable() {
            public void run() {
                InputStreamReader reader = new InputStreamReader(errStream);
                Scanner scan = new Scanner(reader);
                while (scan.hasNextLine()) {
                    logger.info("error "+scan.nextLine());
                }
            }
        }).start();

        OutputStream outStream = p.getOutputStream();
        InputStream inStream = p.getInputStream();

        pipeStream(pub_cert, outStream);
        pipeStream(user_cert,outStream);
        outStream.close();

        Scanner scanner = new Scanner(inStream);
        while (scanner.hasNextLine()) {
            //logger.info(scanner.nextLine());
            sb.append(scanner.nextLine());
            sb.append("\n");
        }

        inStream.close();
        scanner.close();

        //logger.info(sb.toString());
        return sb.toString();
    }

    private String createProxyFromFile2(JSONTransferAPIClient client, String endpointId) throws Exception {
		logger.debug ( "MONA: entered createProxyFromFile2()" );
		logger.debug ( "MONA: endpointId = " + endpointId );
        String mkproxy_path = config.getProperty(OauthConstants.MKPROXY);
		logger.debug ( "MONA: mkproxy_path = " + mkproxy_path );
        String issuer_cred_file = config.getProperty(OauthConstants.ISSUER_CRED);
		logger.debug ( "MONA: issuer_cred_file = " + issuer_cred_file );
        String lifetime = config.getProperty(OauthConstants.LIFETIME);
		logger.debug ( "MONA: lifetime = " + lifetime );
        String pub_key = getPublicKey2(client, endpointId);
		logger.debug ( "MONA: pub_key = " + pub_key );

        StringBuffer sb = new StringBuffer();
        FileInputStream user_cert = new FileInputStream(issuer_cred_file);
        ByteArrayInputStream pub_cert = new ByteArrayInputStream(pub_key.getBytes());
        String[] cmd_array = { mkproxy_path, lifetime };

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd_array);
        Process p = pb.start();

        final InputStream errStream = p.getErrorStream();
        new Thread(new Runnable() {
            public void run() {
                InputStreamReader reader = new InputStreamReader(errStream);
                Scanner scan = new Scanner(reader);
                while (scan.hasNextLine()) {
                    logger.info("error "+scan.nextLine());
                }
            }
        }).start();

        OutputStream outStream = p.getOutputStream();
        InputStream inStream = p.getInputStream();

        pipeStream(pub_cert, outStream);
        pipeStream(user_cert,outStream);
        outStream.close();

        Scanner scanner = new Scanner(inStream);
        while (scanner.hasNextLine()) {
            //logger.info(scanner.nextLine());
            sb.append(scanner.nextLine());
            sb.append("\n");
        }

        inStream.close();
        scanner.close();

        //logger.info(sb.toString());
        return sb.toString();
    }

    private void addActivationItem(String name, String ui_name, String type,
                                   String value, JSONArray items)
            throws JSONException {
        JSONObject data = new JSONObject();
        data.put("DATA_TYPE", "activation_requirement");
        data.put("name", name);
        data.put("type", type);
        data.put("ui_name", ui_name);
        data.put("value", value);
        items.put(data);
    }

    private void addTransferItem(String s_path, String d_path,
                                 boolean flag, JSONObject items)
            throws JSONException {
		logger.debug ( "MONA : entered TransferAction.addTransferItem()" );
		logger.debug ( "MONA : s_path = " + s_path );
		logger.debug ( "MONA : d_path = " + d_path );
		logger.debug ( "MONA : flag = " + flag );
		logger.debug ( "MONA : items = " + items );

        JSONObject item = new JSONObject();
        item.put("DATA_TYPE", "transfer_item");
        item.put("source_path", s_path);
        item.put("destination_path", d_path);
        item.put("recursive",flag);
        items.append("DATA", item);
    }

	public boolean transfer2Gateway()
	{
		//logger.debug ( "MONA : entered TransferAction.transfer2Gateway()" );
        String globusRoot = Workbench.getInstance().getProperties().getProperty
        	( "database.globusRoot" );
        //logger.info ( "MONA: globusRoot = " + globusRoot );

		getDestinationInfo();
        //logger.info ( "MONA: d_epbmid = " + d_epbmid );
        //logger.info ( "MONA: d_epid = " + d_epid );
        //logger.info ( "MONA: d_eppath = " + d_eppath );
        //logger.info ( "MONA: d_epname = " + d_epname );
        //logger.info ( "MONA: d_dispname = " + d_dispname );

		if ( d_eppath.startsWith ( globusRoot ) )
			return ( true );
		else
			return ( false );
	}

	public boolean userCanTransfer()
	{
		//logger.debug ( "MONA : entered TransferAction.userCanTransfer()" );
        // Temporary until we setup ACL for new users!
        if ( ! tmp_can_transfer )
            return ( false );

		boolean answer = false;
		getDestinationInfo();
		
        if ( d_epbmid.equals ( "XSERVER" ) )
        {
			try
			{
				WorkbenchSession session = getWorkbenchSession();
				User user = session.getUser();
				//logger.debug ( "MONA: user = " + user.getUsername() );
				Long user_current_size_gb = getUserDataSize ( "gb" );
				//logger.debug ( "MONA: current size (GB) = " + user_current_size_gb );
				int user_max_size_gb = user.getMaxUploadSizeGB();
				//logger.debug ( "MONA: max size (GB) = " + user_max_size_gb );
				
				if ( user_max_size_gb <= 0 )
				{
					reportUserMessage (
						"Sorry, you do not have permission to upload data via Globus at this time. If you are a new user, please email us at " + 
						Workbench.getInstance().getProperties().getProperty
						( "email.adminAddr" ) );
				}
				else if ( user_current_size_gb.longValue() >=
					( long ) user_max_size_gb )
					reportUserError (
						"You have exceeded your upload limit of " +
						user_max_size_gb +
						" GB.  You must delete some data before you can upload.  Note, you can still download data if you click on the 'Switch Source and Destination' button below." );				
				else
					answer = true;
			}
			catch ( Exception e ) {}
        }
        else
        	answer = true;

		//logger.debug ( "MONA: answer = " + answer );
		return ( answer );
	}

    public boolean displayLs(String endpointId, String path) {
            //throws IOException, JSONException, GeneralSecurityException, APIError {
        Map<String, String> params = new HashMap<String, String>();
        if (path != null) {
            params.put("path", path);
            params.put("show_hidden","0");
        }
        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId)
                    + "/ls";
            JSONTransferAPIClient.Result r = client.getResult(resource, params);
            //logger.info("Contents of " + path + " on "
            //        + endpointId + ":");

            JSONArray fileArray = r.document.getJSONArray("DATA");
            files = new ArrayList<>();
            List<String> session_files = new ArrayList<>();
            List<String> session_dirs = new ArrayList<>();

            for (int i = 0; i < fileArray.length(); i++) {
                JSONObject fileObject = fileArray.getJSONObject(i);
                String f_name = fileObject.getString("name");
                String f_type = fileObject.getString("type");

                //logger.info("  " + f_name);
                files.add(new TransferAction(f_name, f_type, fileObject.getInt("size")));
                if (f_type.equals("dir")) session_dirs.add(f_name);
                if (f_type.equals("file")) session_files.add(f_name);
                /*
                Iterator keysIter = fileObject.sortedKeys();
                while (keysIter.hasNext()) {
                    String key = (String)keysIter.next();
                    if (!key.equals("DATA_TYPE") && !key.equals("LINKS")
                            && !key.endsWith("_link") && !key.equals("name")) {
                        System.out.println("    " + key + ": "
                                + fileObject.getString(key));
                    }
                }
                */
            }

            getSession().put("session_files",session_files);
            getSession().put("session_dirs",session_dirs);

            return true;
        } catch (Exception e) {
            logger.error("Display file list: "+e.toString());
            //reportUserError("It was failed to list files in the directory on the endpoint ID, \""+endpointId+"\".");
            reportUserError("Error, unable to list files on the source endpoint ID, \""+endpointId+"\".");
            return false;
        }
    }

	public int getCount(String endpointId, String path, String disp_name) {
		//throws IOException, JSONException, GeneralSecurityException, APIError {
		logger.debug ( "MONA: entered getCount()" );
		logger.debug ( "MONA: endpointId = " + endpointId );
		logger.debug ( "MONA: path = " + path );
		logger.debug ( "MONA: disp_name = " + disp_name );
		Map<String, String> params = new HashMap<String, String>();
        if (path != null) {
            params.put("path", path);
            params.put("show_hidden","0");
        }
		logger.debug ( "MONA: params = " + params );
        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId)
                    + "/ls";
		    logger.debug ( "MONA: resource = " + resource );
            JSONTransferAPIClient.Result r = client.getResult(resource, params);
            //logger.info("Contents of " + path + " on "
            //        + endpointId + ":");

		    //logger.debug ( "MONA: r.document = " + r.document );
            JSONArray fileArray = r.document.getJSONArray("DATA");
		    //logger.debug ( "MONA: fileArray = " + fileArray );
            filecount = fileArray.length();
		    logger.debug ( "MONA: filecount = " + filecount );
            //logger.info("File count:"+filecount);
            return filecount;
        } catch (Exception e) {
            logger.error("Display file list: "+e.toString());
			//reportUserError("It was failed to list files in the directory on the endpoint ID, \""+endpointId+"\".");
			reportUserError
				( "Error, unable to get access on the source endpoint \""
				+ disp_name + "\"." );
            return filecount;
        }
    }

    public boolean checkLs(String endpointId, String path) {
        Map<String, String> params = new HashMap<String, String>();
        if (path != null) {
            params.put("path", path);
            params.put("show_hidden","0");
        }
        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId)
                    + "/ls";
            JSONTransferAPIClient.Result r = client.getResult(resource, params);
            //logger.info("Check contents of " + path + " on "
            //        + endpointId + ":");

            JSONArray fileArray = r.document.getJSONArray("DATA");
            for (int i = 0; i < fileArray.length(); i++) {
                JSONObject fileObject = fileArray.getJSONObject(i);
                String f_name = fileObject.getString("name");
                String f_type = fileObject.getString("type");
                //logger.info("Name:" + f_name + " Type:"+f_type);
            }
            return true;
        } catch (Exception e) {
            logger.error("Check file list: "+e.toString());
            reportUserError("Error, unable to find the current path of the endpoint ID, \""+endpointId+"\".");
            return false;
        }
    }

    public boolean createUserDir(String endpointId, String path) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        logger.info ( "MONA: entered createUserDir" );
        logger.info ( "MONA: endpointId = " + endpointId );
        logger.info ( "MONA: path = " + path );

        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId) + "/mkdir";
            logger.info ( "MONA: resource = " + resource );
            JSONObject dir_param = new JSONObject();
            dir_param.put("DATA_TYPE", "mkdir");
            dir_param.put("path", path);
            logger.info ( "MONA: dir_param = " + dir_param );

            JSONTransferAPIClient.Result r = client.postResult(resource, dir_param, null);
            logger.info ( "MONA: r.document = " + r.document );
            String code = r.document.getString("code");
            if (code.startsWith("DirectoryCreated")) {
                //reportUserMessage("User directory, "+path+" was created.");
                logger.info("User directory, " + path + " was created.");
                return true;
            }
            return false;
        } catch (Exception e) {
            String error_msg = e.toString();
            logger.error ( "ERROR: createUserDir: " + error_msg );
            if (!error_msg.contains("ExternalError.MkdirFailed.Exists")) {
                logger.error("Create directory: " + error_msg);
                //reportUserError ( "Error, unable to create your data directory on XSEDE Comet storage.");
                reportUserError ( "Error, unable to create your data directory on " + s_dispname );
                return false;
            }
            return true;
        }
    }

    // Instead of using Globus API to create the user directory (as the
    // createUserDir() function does), this function will do it directly
    // since we have access to the filesystem
    private boolean setupUserDir ( String path )
    {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        logger.info ( "MONA: entered setupUserDir" );
        logger.info ( "MONA: path = " + path );

        if ( Files.isDirectory ( Paths.get ( path ) ) )
        {
            logger.info ( "MONA: directory exist" );
            return true;
        }
        else
        {
            logger.info ( "MONA: creating " + path );
            //new File ( path ).mkdir();
            Path dir = Paths.get ( path );
            try
            {
                Files.createDirectory ( dir );
                return true;
            }
            catch ( IOException e )
            {
                logger.error ( "ERROR: cannot create directory " + path );
                return false;
            }
        }
    }

    public void saveTask(String taskId,String fileNames, String dirNames)
            throws IOException, JSONException, GeneralSecurityException, APIError, SQLException {
        TransferRecord tr = new TransferRecord();
        String resource = "/task/" +  taskId;
        Map<String, String> params = new HashMap<String, String>();
        String fields = "status,source_endpoint_display_name,"
                +"destination_endpoint_display_name,request_time,files_transferred,faults,"
                +"sync_level,encrypt_data,files,directories,files_skipped,bytes_transferred";
        params.put("fields", fields);
        JSONTransferAPIClient.Result r = client.getResult(resource, params);

        try {
            logger.info("Encrypt data type: " + r.document.getBoolean("encrypt_data"));
        } catch (org.json.JSONException e) {
            logger.info("Encrypt data type is not specified.....");
        }

        tr.setTaskId(taskId);
        tr.setUserId((Long) getSession().get("user_id"));
        tr.setSrcEndpointname(r.document.getString("source_endpoint_display_name"));
        tr.setDestEndpointname(r.document.getString("destination_endpoint_display_name"));
        tr.setRequestTime(r.document.getString("request_time"));
        tr.setStatus(r.document.getString("status"));
        try {
            String c_time = r.document.getString("completion_time");
            if (c_time != null)
                tr.setCompletionTime(c_time);
            else
                tr.setCompletionTime("");
        } catch (org.json.JSONException e) {
            tr.setCompletionTime("");
        }
        tr.setFilesTransferred(r.document.getInt("files_transferred"));
        tr.setFaults(r.document.getInt("faults"));
        try {
            tr.setSyncLevel(r.document.getInt("sync_level"));
        } catch (org.json.JSONException e) {
            tr.setSyncLevel(-1);
        }
        tr.setFiles(r.document.getInt("files"));
        tr.setDirectories(r.document.getInt("directories"));
        tr.setFilesSkipped(r.document.getInt("files_skipped"));
        tr.setByteTransferred(r.document.getLong("bytes_transferred"));
        logger.debug("butes_transferred: "+r.document.getLong("bytes_transferred"));

        Folder current_folder = getCurrentFolder();
        long folder_id = current_folder.getFolderId();
        //logger.info("Current Folder ID: "+folder_id);
        tr.setEnclosingFolderId(folder_id);
        tr.setFileNames(fileNames);
        tr.setDirectoryNames(dirNames);

        ProfileManager pm = new ProfileManager();
        pm.addRecord(tr);
    }

    public TransferRecord updateTask(String taskId, JSONTransferAPIClient local_client)
            throws IOException, JSONException, GeneralSecurityException, APIError {

        if (local_client == null) local_client = client;
        TransferRecord tr = new TransferRecord();
        String resource = "/task/" +  taskId;
        Map<String, String> params = new HashMap<String, String>();
        String fields = "status,files_transferred,faults,completion_time,"
                +"files,directories,files_skipped,bytes_transferred";
        params.put("fields", fields);
        JSONTransferAPIClient.Result r = local_client.getResult(resource, params);

        tr.setTaskId(taskId);
        //tr.setUserId((Long) getSession().get("user_id"));
        //tr.setSrcEndpointname(r.document.getString("source_endpoint_display_name"));
        //tr.setDestEndpointname(r.document.getString("destination_endpoint_display_name"));
        //tr.setRequestTime(r.document.getString("request_time"));
        tr.setStatus(r.document.getString("status"));
        try {
            String c_time = r.document.getString("completion_time");
            if (c_time != null)
                tr.setCompletionTime(c_time);
            else
                tr.setCompletionTime("");
        } catch (org.json.JSONException e) {
            tr.setCompletionTime("Not available");
        }
        tr.setFilesTransferred(r.document.getInt("files_transferred"));
        tr.setFaults(r.document.getInt("faults"));
        /*
        try {
            tr.setSyncLevel(r.document.getInt("sync_level"));
        } catch (org.json.JSONException e) {
            tr.setSyncLevel(-1);
        }
        */
        tr.setFiles(r.document.getInt("files"));
        tr.setDirectories(r.document.getInt("directories"));
        tr.setFilesSkipped(r.document.getInt("files_skipped"));
        tr.setByteTransferred(r.document.getLong("bytes_transferred"));

        return tr;
    }

    public String getFilename() {return filename;}
    public void setFilename(String filename) {this.filename = filename;}
    public String getFiletype() {return filetype;}
    public void setFiletype(String filetype) {this.filetype = filetype;}
    public Integer getFilesize() {return filesize;}
    public void setFilesize(Integer filesize) {this.filesize = filesize;}
    public List<TransferAction> getFiles() {return files;}
    public void setFiles(List<TransferAction> files) {this.files = files;}
    public Integer getFilecount() {return filecount;}
    public void setFilecount(Integer filecount) {this.filecount = filecount;}

    public List<String> getDirectorynames() {return directorynames;}
    public void setDirectorynames(List<String> directorynames) {this.directorynames = directorynames;}
    public List<String> getFilenames() {return filenames;}
    public void setFilenames(List<String> filenames) {this.filenames = filenames;}
    public String getTaskId() {return taskId;}
    public void setTaskId(String taskId) {this.taskId = taskId;}

    public void setBookmarklist(List<Map<String,Object>> bookmarklist) {this.bookmarklist = bookmarklist;}
    public List<Map<String,Object>> getBookmarklist() {return bookmarklist;}

    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getActionType() { return actionType; }

    public void setSelectedFiles(String selectedFiles) {this.selectedFiles = selectedFiles;}
    public String getSelectedFiles() {return selectedFiles;}

    public String getSearchLabel() { return searchLabel; }
    public String getSearchValue() { return searchValue; }
    public String getMyendpointName() { return myendpointName; }
    public String getMyendpointValue() { return myendpointValue; }

    public Map<String,String> getMyendpoints() {return myendpoints;}
    public void setMyendpoints(Map<String,String> myendpoints) { this.myendpoints = myendpoints; }
    public void setSearchLabel(String searchLabel) { this.searchLabel = searchLabel; }
    public void setSearchValue(String searchValue) { this.searchValue = searchValue; }
    public void setMyendpointName(String myendpointName) { this.myendpointName = myendpointName; }
    public void setMyendpointValue(String myendpointValue) { this.myendpointValue = myendpointValue; }

}
