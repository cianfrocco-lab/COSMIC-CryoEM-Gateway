package edu.sdsc.globusauth.action;

import com.google.api.client.auth.oauth2.Credential;
import edu.sdsc.globusauth.util.OauthConstants;
import edu.sdsc.globusauth.util.OauthUtils;
import org.apache.log4j.Logger;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.BaseTransferAPIClient;
import org.globusonline.transfer.GoauthAuthenticator;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ngbw.web.actions.NgbwSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cyoun on 11/30/16.
 */
public class EndpointListAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(EndpointListAction.class.getName());
    private JSONTransferAPIClient client;
    private List<Map<String,Object>> iplist;
    private List<Map<String,Object>> bookmarklist;
    //private Map<String, Object> ipmap;

    public EndpointListAction() {}
    public EndpointListAction(String accesstoken,
                              String username) throws Exception {
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);
        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);
    }

    public String endpoint_list() throws Exception {
        if (Boolean.valueOf(request.getParameter("empty"))) {
            bookmarklist = new ArrayList<>();
            return SUCCESS;
        }
        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);
        //my_endpoint_search("my-gcp-endpoints");
        my_endpoint_list();
        return SUCCESS;
    }

    public void my_endpoint_list() throws Exception {
        bookmarklist = new ArrayList<>();
        my_endpoint_search("my-gcp-endpoints");
        //my_endpoint_search("my-endpoints");
        if (iplist != null && iplist.size() > 0) {
            List<Map<String,Object>> bmlist = my_bookmark_list();
            if (bmlist != null && bmlist.size() > 0) {
                for (int i=0; i<iplist.size(); i++) {
                    Map<String, Object> ipmap = iplist.get(i);
                    String eid = (String) ipmap.get("id");
                    boolean flag = false;
                    for (int j=0; j<bmlist.size(); j++) {
                        Map<String, Object> bmmap = bmlist.get(j);
                        if (eid.equals((String) bmmap.get("endpoint_id"))) {
                            flag = true;
                            bookmarklist.add(bmmap);
                            break;
                        }
                    }
                    if(!flag) {
                        String disp_ipname = (String) ipmap.get("canonical_name");
                        String ipname = disp_ipname + "::MYGCPEP";
                        String bm_id = createBookmark(ipname,eid,"/~/");
                        Map<String,Object> bmmap = new HashMap<String, Object>();
                        bmmap.put("id", bm_id);
                        bmmap.put("name", ipname);
                        bmmap.put("disp_name", disp_ipname);
                        bmmap.put("endpoint_id", eid);
                        bmmap.put("path", "/~/");
                        bookmarklist.add(bmmap);
                    }
                }
            } else {
                //create bookmark
                for (int i=0; i<iplist.size(); i++) {
                    Map<String, Object> ipmap = iplist.get(i);
                    String eid = (String) ipmap.get("id");
                    String disp_ipname = (String) ipmap.get("canonical_name");
                    String ipname = disp_ipname + "::MYGCPEP";
                    String bm_id = createBookmark(ipname,eid,"/~/");
                    Map<String,Object> bmmap = new HashMap<String, Object>();
                    bmmap.put("id", bm_id);
                    bmmap.put("name", ipname);
                    bmmap.put("disp_name", disp_ipname);
                    bmmap.put("endpoint_id", eid);
                    bmmap.put("path", "/~/");
                    bookmarklist.add(bmmap);
                }
            }
        }
    }

    public void my_endpoint_search(String scope) throws Exception {
        JSONTransferAPIClient.Result r;
        iplist = new ArrayList<>();

        String resource = "/endpoint_search";
        Map<String, String> params = new HashMap<String, String>();
        //params.put("filter_scope", "my-gcp-endpoints");
        //params.put("filter_scope", "my-endpoints");
        params.put("filter_scope", scope);
        r = client.getResult(resource, params);
        JSONArray data = r.document.getJSONArray("DATA");
        if (data.length() > 0) {
            for (int i = 0; i < data.length(); i++) {
                Map<String,Object> ipmap = new HashMap<String, Object>();
                ipmap.put("canonical_name", data.getJSONObject(i).getString("canonical_name"));
                ipmap.put("id", data.getJSONObject(i).getString("id"));
                iplist.add(ipmap);
            }
        }
    }

    public List<Map<String,Object>> my_bookmark_list() throws Exception {
        JSONTransferAPIClient.Result r;
        List<Map<String,Object>> bmlist = new ArrayList<>();

        String resource = "/bookmark_list";
        r = client.getResult(resource);
        JSONArray data = r.document.getJSONArray("DATA");
        if (data.length() > 0) {
            for (int i = 0; i < data.length(); i++) {
                Map<String,Object> ipmap = new HashMap<String, Object>();
                String name = data.getJSONObject(i).getString("name");
                String[] namea = name.split("::");
                if (namea.length > 1) {
                    ipmap.put("id", data.getJSONObject(i).getString("id"));
                    ipmap.put("name", name);
                    if (name.contains("SOURCE")) {
                        ipmap.put("disp_name", namea[0]+" (Source) ");
                    } else if (name.contains("DEST")) {
                        ipmap.put("disp_name", namea[0]+" (Destination) ");
                    } else {
                        ipmap.put("disp_name", namea[0]);
                    }
                    ipmap.put("endpoint_id", data.getJSONObject(i).getString("endpoint_id"));
                    ipmap.put("path", data.getJSONObject(i).getString("path"));
                    bmlist.add(ipmap);
                }
            }
        }
        return bmlist;
    }

    public String createBookmark(String name, String endpointId, String path) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        String bm_id = null;
        try {
            String resource = "/bookmark";
            JSONObject bm_param = new JSONObject();
            bm_param.put("name", name);
            bm_param.put("endpoint_id",endpointId);
            bm_param.put("path", path);

            JSONTransferAPIClient.Result r = client.postResult(resource, bm_param, null);
            bm_id = r.document.getString("id");
        } catch (Exception e) {
            logger.error("Create bookmark: "+e.toString());
            reportUserError(e.toString());
            return null;
        }
        return bm_id;
    }

    public String updateBookmark(String id, String name) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        String bm_id = null;
        try {
            String resource = "/bookmark/"+id;
            JSONObject bm_param = new JSONObject();
            bm_param.put("name", name);

            JSONTransferAPIClient.Result r = client.putResult(resource, bm_param, null);
            bm_id = r.document.getString("id");
        } catch (Exception e) {
            logger.error("Update bookmark: "+e.toString());
            reportUserError(e.toString());
            return null;
        }
        return bm_id;
    }

    public boolean deleteBookmark(String id) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        try {
            String resource = "/bookmark/"+id;
            JSONTransferAPIClient.Result r = client.deleteResult(resource, null);
            String code = r.document.getString("code");
            if (code.startsWith("Deleted")) {
                reportUserMessage("Bookmark, "+id+" was created.");
                return true;
            }
        } catch (Exception e) {
            logger.error("Delete bookmark: "+e.toString());
            reportUserError(e.toString());
            return false;
        }
        return false;
    }

    public List<Map<String,Object>> getIplist() {return iplist;}
    public List<Map<String,Object>> getBookmarklist() {return bookmarklist;}
}
