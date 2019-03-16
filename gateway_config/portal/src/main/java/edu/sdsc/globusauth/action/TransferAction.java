package edu.sdsc.globusauth.action;

/**
 * Created by cyoun on 06/25/17.
 * Updated by Mona Wong
 */

import com.google.api.client.auth.oauth2.Credential;
import edu.sdsc.globusauth.controller.ProfileManager;
import org.ngbw.sdk.database.TransferRecord;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;
import org.apache.log4j.Logger;
import org.globusonline.transfer.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.ngbw.sdk.Workbench;
import org.ngbw.web.actions.NgbwSupport;
import org.ngbw.sdk.database.Folder;

import java.io.*;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.*;

public class TransferAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(TransferAction.class.getName());
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

    public TransferAction(){}
    public TransferAction(String filename,String filetype,
                          Integer filesize)
    {
        this.filename = filename;
        this.filetype = filetype;
        this.filesize = filesize;
    }

    public TransferAction(String accesstoken,
                          String username) throws Exception {
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);
        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);
    }

    public String transfer() throws Exception {
        //logger.info ( "MONA: entered TransferAction.transfer()" );

        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        //logger.info ( "MONA: accesstoken = " + accesstoken );
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        //logger.info ( "MONA: username = " + username );
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);
        EndpointListAction iplistaction = new EndpointListAction(accesstoken, username);
        setMyendpoints(iplistaction.my_endpoint_search_transfer("administered-by-me"));

        //logger.info("search label: "+searchLabel);
        //logger.info("search value: "+searchValue);
        //logger.info("my emdpoint name: "+myendpointName);
        //logger.info("my endpoint value: "+myendpointValue);

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
            }
        }

        //iplistaction.my_endpoint_list();
        //setBookmarklist(iplistaction.getBookmarklist());
        setBookmarklist(iplistaction.my_bookmark_list());

	getSourceInfo();
        //logger.info("SRC Bookmark ID: "+s_epbmid);
        //logger.info("SRC Endpoint ID: "+s_epid);
        //logger.info("SRC Path: "+s_eppath);
        //logger.info("SRC Name: "+s_epname);
        //logger.info("SRC Display Name: "+s_dispname);

        if (request.getMethod().equals(OauthConstants.HTTP_GET)) {
	    //logger.info("Source Endpoint activation....");
            String result = activationProcess(s_epbmid,s_epid,s_eppath,s_dispname);
            if (result.equals(SUCCESS)) {
                getCount(s_epid, s_eppath, s_dispname);
            }
            return SUCCESS;
        }
        // HTTP_POST is a transfer request...
        else if (request.getMethod().equals(OauthConstants.HTTP_POST)) {
            //logger.info ( "MONA: HTTP_POST" );

            Folder current_folder = getCurrentFolder();
            //logger.info ( "MONA: folder id = " + current_folder.getFolderId() );
            //logger.info ( "MONA: label = " + current_folder.getLabel() );

            getDestinationInfo();
            //logger.info("Destination Endpoint activation....");
 
            String globusRoot =
                Workbench.getInstance().getProperties().getProperty
                ( "database.globusRoot" );
            //logger.info ( "MONA: globusRoot = " + globusRoot );

            // If we are transferring TO the gateway storage, add the
            // user folder label so that the data is organized in the same
            // way as on the gateway's UI...
            if ( d_eppath.startsWith ( globusRoot ) )
                d_eppath += current_folder.getLabel() + "/";

            String d_result = activationProcess(d_epbmid,d_epid,d_eppath,d_dispname);
            //logger.info ( "MONA: d_result = " + d_result );
            if (d_result.equals("failure")) return SUCCESS;

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

            return "transferstatus";
            //return SUCCESS;
        } else {
            return "failure";
        }

    }

	public String activationProcess(String epbmid,
                                    String epid,
                                    String eppath,
                                    String dispname) throws Exception {
        //logger.info ( "MONA: entered activationProcess" );
        //logger.info ( "MONA: eppath = " + eppath );
		Map<String, Boolean> ep_status = endpointStatus(epid);
        if (epbmid.equals("XSERVER")) {
            if (!ep_status.get("activated")) {
                if (!delegateProxyActivation(epid)) {
                    //logger.error("XSEDE endpoint, " + dispname + " can't be activated using delegate proxy.");
                    reportUserError("XSEDE endpoint, " + dispname + " can't be activated using delegate proxy.");
                    return "failure";
                }
            }
           	createUserDir(epid, eppath);
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
                    	//logger.error("The endpoint, "+ dispname + ", is not connected or is paused.");
                    	reportUserError ("The endpoint, "+ dispname + ", is not connected or paused.");
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

    public boolean autoActivate(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/autoactivate";
        JSONTransferAPIClient.Result r = client.postResult(resource, null,
                null);
        String code = r.document.getString("code");
        if (code.startsWith("AutoActivationFailed")) {
            ep_act_uri = (String) getSession().get(OauthConstants.ENDPOINT_ACTIVATION_URI);
            ep_act_uri = ep_act_uri.replace(":endpoint_id",endpointId);
            return false;
        }
        return true;
    }

    public Map<String,Boolean> endpointStatus(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
        String resource = BaseTransferAPIClient.endpointPath(endpointId);
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

        String myproxy_host = config.getProperty(OauthConstants.MYPROXY_HOST);
        String username = config.getProperty(OauthConstants.USERNAME);
        String passwd = config.getProperty(OauthConstants.PASSWORD);
        String lifetime = config.getProperty(OauthConstants.LIFETIME);

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
        JSONTransferAPIClient.Result r = client.postResult(resource, req_body,
                null);
        String code = r.document.getString("code");
        if (!code.startsWith("Activated.MyProxyCredential")) {
            return false;
        }
        return true;
    }

    public boolean delegateProxyActivation(String endpointId) throws Exception {

        String proxy_chain = createProxyFromFile(endpointId);
        JSONObject req_body = new JSONObject();
        req_body.put("DATA_TYPE", "activation_requirements");
        JSONArray data_list = new JSONArray();
        req_body.put("DATA", data_list);
        addActivationItem("proxy_chain","Proxy Chain","delegate_proxy",proxy_chain,data_list);

        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activate";
        JSONTransferAPIClient.Result r = client.postResult(resource, req_body,
                null);
        String code = r.document.getString("code");
        if (!code.startsWith("Activated.ClientProxyCredential")) {
            return false;
        }
        return true;
    }

    private String getPublicKey(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/activation_requirements";
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

        String mkproxy_path = config.getProperty(OauthConstants.MKPROXY);
        String issuer_cred_file = config.getProperty(OauthConstants.ISSUER_CRED);
        String lifetime = config.getProperty(OauthConstants.LIFETIME);
        String pub_key = getPublicKey(endpointId);

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
                    //logger.info("error"+scan.nextLine());
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

        JSONObject item = new JSONObject();
        item.put("DATA_TYPE", "transfer_item");
        item.put("source_path", s_path);
        item.put("destination_path", d_path);
        item.put("recursive",flag);
        items.append("DATA", item);
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
            filecount = fileArray.length();
            //logger.info("File count:"+filecount);
            return filecount;
        } catch (Exception e) {
            logger.error("Display file list: "+e.toString());
			//reportUserError("It was failed to list files in the directory on the endpoint ID, \""+endpointId+"\".");
			reportUserError("Error, unable to get file count on the source endpoint ID, \""+disp_name+"\".");
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
        //logger.info ( "MONA: entered createUserDir" );
        //logger.info ( "MONA: endpointId = " + endpointId );
        //logger.info ( "MONA: path = " + path );
        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId) + "/mkdir";
            JSONObject dir_param = new JSONObject();
            dir_param.put("DATA_TYPE", "mkdir");
            dir_param.put("path", path);

            JSONTransferAPIClient.Result r = client.postResult(resource, dir_param, null);
            String code = r.document.getString("code");
            if (code.startsWith("DirectoryCreated")) {
                //reportUserMessage("User directory, "+path+" was created.");
                logger.info("User directory, " + path + " was created.");
                return true;
            }
            return false;
        } catch (Exception e) {
            String error_msg = e.toString();
            if (!error_msg.contains("ExternalError.MkdirFailed.Exists")) {
                logger.error("Create directory: " + error_msg);
                //reportUserError("The user directory on XSEDE Comet resource was failed to access.");
                reportUserError("Error, unable to access XSEDE Comet storage.");
                return false;
            }
            return true;
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
