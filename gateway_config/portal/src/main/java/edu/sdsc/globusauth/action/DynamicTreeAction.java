package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.GoauthAuthenticator;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.globusonline.transfer.BaseTransferAPIClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ngbw.web.actions.NgbwSupport;
import org.apache.struts2.util.ServletContextAware;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.servlet.ServletContext;

import java.util.LinkedList;
import edu.sdsc.globusauth.model.TreeNode;
import edu.sdsc.globusauth.model.FileMetadata;

/**
 * Created by cyoun on 11/09/17.
 */
public class DynamicTreeAction extends NgbwSupport implements ServletContextAware {

    private static final Logger logger = Logger.getLogger(DynamicTreeAction.class.getName());
    private static final long serialVersionUID = -2886756982077980790L;
    private List<TreeNode> nodes = new ArrayList<TreeNode>();
    private String id = "";
    private ServletContext servletContext;
    private JSONTransferAPIClient client;

    public DynamicTreeAction() {
    }

    public String file_tree() throws Exception {
        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);

        logger.info("Node ID: "+id);
        generateTree(id);
        return SUCCESS;
    }

    private List<FileMetadata> get_filelist(String endpointId, String path) throws Exception {
        List<FileMetadata> files = new ArrayList<FileMetadata>();
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
                files.add(new FileMetadata(f_name, f_type, fileObject.getInt("size")));
            }
        } catch (Exception e) {
            logger.error("Display file list: "+e.toString());
            reportUserError("Error, unable to list files on the source endpoint ID, \""+endpointId+"\".");
            return null;
        }
        return files;
    }

    private void generateTree(String nodeId) throws Exception {
        String s_epid = (String) getSession().get(OauthConstants.SRC_ENDPOINT_ID);
        String s_eppath = (String) getSession().get(OauthConstants.SRC_ENDPOINT_PATH);
        if (nodeId.equals("#")) {
            nodeId = "";
        } else {
            s_eppath = s_eppath + "/" + nodeId;
            nodeId = nodeId + "/";
        }

        logger.info("SRC Endpoint ID: "+s_epid);
        logger.info("SRC Path: "+s_eppath);
        List<FileMetadata> files = get_filelist(s_epid,s_eppath);

        for(FileMetadata fm:files) {
            TreeNode node = new TreeNode();
            node.setId(nodeId + fm.getName());
            node.getState().setOpened(false);
            if (fm.getType().equals("dir")) {
                node.setHasChildren(true);
                node.setIcon("folder");
                node.setType("folder");
            } else {
                //node.getState().setDisabled(true);
                node.setIcon("file");
                node.setType("file");
            }
            node.setText(fm.getName()+"      "+fm.getSize().toString());
            nodes.add(node);
        }
    }

    public String getJSON() throws Exception {
        return file_tree();
    }
    public List<TreeNode> getNodes() {
        return nodes;
    }
    public void setId(String id) {
        this.id = id;
    }
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

}
