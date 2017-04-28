package edu.sdsc.globusauth.action;

/**
 * Created by cyoun on 10/26/16.
 */

import com.google.api.client.auth.oauth2.Credential;
import edu.sdsc.globusauth.controller.ProfileManager;
import edu.sdsc.globusauth.model.TransferRecord;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;
import org.apache.log4j.Logger;
import org.globusonline.transfer.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ngbw.web.actions.NgbwSupport;

import java.io.*;
import java.security.GeneralSecurityException;
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
    //private String s_dispname;

    //destination endpoint info
    private String d_epbmid;
    private String d_epid;
    private String d_eppath;
    private String d_epname;
    //private String d_dispname;

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

        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        config = OauthUtils.getConfig(OauthConstants.OAUTH_PORPS);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);

        getSourceInfo();
        if (s_epid == null || s_epid.trim().isEmpty()) return "dataendpoints";

        EndpointListAction iplistaction = new EndpointListAction(accesstoken, username);
        String s_eptype = request.getParameter("bookmarkId");
        if (s_eptype != null && !s_eptype.equals(s_epbmid)) {
            if (s_eptype.equals("XSERVER")) {
                s_epname = s_epname.replace("SOURCE", "DEST");
                iplistaction.updateBookmark(s_epbmid, s_epname);
                setDestinationInfo(s_epbmid,s_epid,s_eppath,s_epname);

                s_epbmid = "XSERVER";
                s_epid = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
                s_eppath = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
                s_epname = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_NAME);
                setSourceInfo(s_epbmid,s_epid,s_eppath,s_epname);

            } else {
                if (s_epbmid.equals("XSERVER")) {

                    getDestinationInfo();
                    setDestinationInfo(s_epbmid,s_epid,s_eppath,s_epname);

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
                        setSourceInfo(s_epbmid,s_epid,s_eppath,s_epname);

                    } else {
                        exchangeSourceInfo(iplistaction,s_eptype,d_epbmid,d_epname,"DEST");
                    }
                } else {
                    exchangeSourceInfo(iplistaction,s_eptype,s_epbmid,s_epname,"SOURCE");
                }
            }
        } else {
            if (s_eptype != null && !s_eptype.equals("XSERVER")) {
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
                            setSourceInfo(s_epbmid,s_epid,s_eppath,s_epname);
                        }
                    }
                }
            }
        }

        logger.info("SRC Bookmark ID: "+s_epbmid);
        logger.info("SRC Endpoint ID: "+s_epid);
        logger.info("SRC Path: "+s_eppath);
        logger.info("SRC Name: "+s_epname);

        //XSEDE endpoint
        String xsede_id = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_ID);
        String xsede_path = (String) getSession().get(OauthConstants.DATASET_ENDPOINT_BASE);
        Map<String,Boolean> ep_status = endpointStatus(xsede_id);

        if (!ep_status.get("activated")) {
            if (!delegateProxyActivation(xsede_id)) {
                logger.error("Unable to activate destination endpoint using delegate proxy, exiting");
                reportUserError("XSEDE endpoint, "+xsede_id+" is unable to activate destination endpoint using delegate proxy.");
                return SUCCESS;
            }
            ep_status = endpointStatus(xsede_id);
        }

        if(ep_status.get("is_connected")) {
            createUserDir(xsede_id, xsede_path);
        } else {
            logger.error("XSEDE endpoint, "+xsede_id+" is not connected.");
            reportUserMessage("XSEDE endpoint, "+xsede_id+" is not connected.");
            return SUCCESS;
        }

        if (request.getMethod().equals(OauthConstants.HTTP_GET)) {

            logger.info("Source Endpoint activation....");
            if (s_epname.contains("::")) {
                logger.info("Source Endpoint status......");
                ep_status = endpointStatus(s_epid);
                if (!ep_status.get("activated")) {
                    //My GCP endpoint
                    if (!autoActivate(s_epid)) {
                        logger.error("Unable to auto activate source endpoint, exiting");
                        reportUserError("Unable to auto activate source endpoint, "+s_epid);
                        return SUCCESS;
                    }
                    ep_status = endpointStatus(s_epid);
                }
                /*
                else {
                    //XSEDE endpoint
                    if (!delegateProxyActivation(s_epid)) {
                        logger.error("Unable to activate destination endpoint using delegate proxy, exiting");
                        return "failure";
                    }
                }
                */
            }

            if(ep_status.get("is_connected")) {
                displayLs(s_epid, s_eppath);
            } else {
                reportUserMessage("Source endpoint, "+s_epid+" is not connected.");
            }

            return SUCCESS;
        } else if (request.getMethod().equals(OauthConstants.HTTP_POST)) {

            //check the file/directory selection
            List<String> session_files = getFileList("session_files");
            List<String> session_dirs = getFileList("session_dirs");

            List<String> filter_filenames = new ArrayList<>();
            List<String> filter_dirnames = new ArrayList<>();

            if (filenames != null && filenames.size() > 0) {
                for (String file : filenames) {
                    logger.info("File name:" + file);
                    for (String s: session_files)
                        if (s.equals(file)) filter_filenames.add(file);
                }
            }

            if (directorynames != null && directorynames.size() > 0) {
                for (String dir : directorynames) {
                    logger.info("Directory name:"+dir);
                    for (String s: session_dirs)
                        if (s.equals(dir)) filter_dirnames.add(dir);
                }
            }

            /*
            for (String file: filter_filenames)
                logger.info("filter file name: "+file);
            for (String dir: filter_dirnames)
                logger.info("filtered dir name: "+dir);

            boolean empty_flag = true;
            if (getFilenames() != null && getFilenames().size() > 0) {
                empty_flag = false;
            } else if (getDirectorynames() != null && getDirectorynames().size() > 0){
                empty_flag = false;
            }
            if (empty_flag) {
                reportUserError("Please select at least one file.");
                createUserDir(xsede_id,xsede_path);
                displayLs(s_epid, s_eppath);
                return SUCCESS;
            }
            */

            getDestinationInfo();

            logger.info("Destination Endpoint activation....");
            if (d_epname.contains("::")) {
                logger.info("Destination Endpoint status......");
                ep_status = endpointStatus(d_epid);
                if (!ep_status.get("activated")) {
                    //My GCP endpoint
                    if (!autoActivate(d_epid)) {
                        logger.error("Unable to auto activate destination endpoint, exiting");
                        reportUserError("Unable to auto activate destination endpoint, "+d_epid);
                        return SUCCESS;
                    }
                    ep_status = endpointStatus(d_epid);
                }

                if(!ep_status.get("is_connected")) {
                    reportUserMessage("Destination endpoint, "+d_epid+" is not connected.");
                    return SUCCESS;
                }
            }

            /*
            logger.info("Destination Endpoint status......");
            if (!endpointStatus(d_epid)) {
                logger.info("Destination Endpoint activation....");
                if (d_epname.contains("::")) {
                    //My GCP endpoint
                    if (!autoActivate(s_epid)) {
                        logger.error("Unable to auto activate destination endpoint, exiting");
                        return "failure";
                    }
                } else {
                    //XSEDE endpoint
                    if (!delegateProxyActivation(d_epid)) {
                        logger.error("Unable to activate destination endpoint using delegate proxy, exiting");
                        return "failure";
                    }
                }
            }
            */

            JSONTransferAPIClient.Result r = client.getResult("/submission_id");
            String submissionId = r.document.getString("value");
            int sync_level = Integer.parseInt(config.getProperty("sync_level"));
            JSONObject transfer = new JSONObject();
            transfer.put("DATA_TYPE", "transfer");
            transfer.put("submission_id", submissionId);
            transfer.put("source_endpoint", s_epid);
            transfer.put("destination_endpoint", d_epid);
            transfer.put("sync_level", sync_level);

            if (filter_filenames.size() > 0) {
                for (String file : filter_filenames) {
                    logger.info("Filtered file name:" + file);
                    addTransferItem(s_eppath+file, d_eppath+file, false, transfer);
                }
            }
            if (filter_dirnames.size() > 0) {
                for (String dir : filter_dirnames) {
                    logger.info("Filtered directory name:"+dir);
                    addTransferItem(s_eppath+dir, d_eppath+dir, true, transfer);
                }
            }
            r = client.postResult("/transfer", transfer, null);
            taskId = r.document.getString("task_id");
            saveTask(taskId);
            return "transferstatus";
        } else {
            return "failure";
        }

    }

    @SuppressWarnings("unchecked")
    private List<String> getFileList(String id){
        return (List<String>) getSession().get(id);
    }

    private void getSourceInfo() {
        s_epbmid = (String) getSession().get(OauthConstants.SRC_BOOKMARK_ID);
        s_epid = (String) getSession().get(OauthConstants.SRC_ENDPOINT_ID);
        s_eppath = (String) getSession().get(OauthConstants.SRC_ENDPOINT_PATH);
        s_epname = (String) getSession().get(OauthConstants.SRC_ENDPOINT_NAME);
        //s_dispname = (String) getSession().get(OauthConstants.SRC_DISP_NAME);
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

    public boolean autoActivate(String endpointId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
        String resource = BaseTransferAPIClient.endpointPath(endpointId)
                + "/autoactivate";
        JSONTransferAPIClient.Result r = client.postResult(resource, null,
                null);
        String code = r.document.getString("code");
        if (code.startsWith("AutoActivationFailed")) {
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
        boolean is_connected = true;
        boolean is_paused = true;

        logger.info("activated: "+activated);
        JSONArray data = r.document.getJSONArray("DATA");
        for (int i=0; i< data.length(); i++) {
            is_connected = data.getJSONObject(i).getBoolean("is_connected");
            is_paused = data.getJSONObject(i).getBoolean("is_paused");

            logger.info(i+" is_connected: "+is_connected);
            logger.info(i+" is_paused: "+is_paused);
        }
        ep_status.put("activated",activated);
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
            logger.info(i+" type: "+ar_type);
            logger.info(i+" name: "+ar_name);
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
                    logger.info("error"+scan.nextLine());
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

        logger.info(sb.toString());
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
            logger.info("Contents of " + path + " on "
                    + endpointId + ":");

            JSONArray fileArray = r.document.getJSONArray("DATA");
            files = new ArrayList<>();
            List<String> session_files = new ArrayList<>();
            List<String> session_dirs = new ArrayList<>();

            for (int i = 0; i < fileArray.length(); i++) {
                JSONObject fileObject = fileArray.getJSONObject(i);
                String f_name = fileObject.getString("name");
                String f_type = fileObject.getString("type");

                logger.info("  " + f_name);
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
            reportUserError(e.toString());
            return false;
        }
    }

    public boolean createUserDir(String endpointId, String path) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        try {
            String resource = BaseTransferAPIClient.endpointPath(endpointId) + "/mkdir";
            JSONObject dir_param = new JSONObject();
            dir_param.put("DATA_TYPE", "mkdir");
            dir_param.put("path", path);

            JSONTransferAPIClient.Result r = client.postResult(resource, dir_param, null);
            String code = r.document.getString("code");
            if (code.startsWith("DirectoryCreated")) {
                //reportUserMessage("User directory, "+path+" was created.");
                logger.debug("User directory, " + path + " was created.");
                return true;
            }
            return false;
        } catch (Exception e) {
            String error_msg = e.toString();
            if (!error_msg.contains("ExternalError.MkdirFailed.Exists")) {
                logger.error("Create directory: " + error_msg);
                reportUserError(e.toString());
                return false;
            }
            return true;
        }
    }

    public void saveTask(String taskId)
            throws IOException, JSONException, GeneralSecurityException, APIError {
        TransferRecord tr = new TransferRecord();
        String resource = "/task/" +  taskId;
        Map<String, String> params = new HashMap<String, String>();
        String fields = "status,source_endpoint_display_name,"
                +"destination_endpoint_display_name,request_time,files_transferred,faults,"
                +"sync_level,files,directories,files_skipped,bytes_transferred";
        params.put("fields", fields);
        JSONTransferAPIClient.Result r = client.getResult(resource, params);

        tr.setTaskId(taskId);
        tr.setUserId((Long) getSession().get("user_id"));
        tr.setSrcEndpointname(r.document.getString("source_endpoint_display_name"));
        tr.setDestEndpointname(r.document.getString("destination_endpoint_display_name"));
        tr.setRequestTime(r.document.getString("request_time"));
        tr.setStatus(r.document.getString("status"));
        try {
            tr.setCompletionTime(r.document.getString("completion_time"));
        } catch (org.json.JSONException e) {
            tr.setCompletionTime("Not available");
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
            tr.setCompletionTime(r.document.getString("completion_time"));
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

    public List<String> getDirectorynames() {return directorynames;}
    public void setDirectorynames(List<String> directorynames) {this.directorynames = directorynames;}
    public List<String> getFilenames() {return filenames;}
    public void setFilenames(List<String> filenames) {this.filenames = filenames;}
    public String getTaskId() {return taskId;}
    public void setTaskId(String taskId) {this.taskId = taskId;}

}
