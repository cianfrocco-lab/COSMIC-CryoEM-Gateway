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
 * Created by cyoun on 06/19/17.
 */
public class EndpointListAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(EndpointListAction.class.getName());
    private JSONTransferAPIClient client;
    private List<Map<String,Object>> iplist;
    private List<Map<String,Object>> bookmarklist;
    private Map<String,String> myendpoints;
    private String searchValue;
    private String searchLabel;
    private String myendpointName;
    private String myendpointValue;

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
        my_endpoint_search("administered-by-me");

        logger.info("search label: "+searchLabel);
        logger.info("search value: "+searchValue);
        logger.info("my emdpoint name: "+myendpointName);
        logger.info("my endpoint value: "+myendpointValue);

        if ((searchLabel != null && searchLabel.trim().length() > 0) &&
                (searchValue != null && searchValue.trim().length() > 0)) {
            add_my_endpoint(searchLabel,searchValue);
            searchLabel ="";
            searchValue ="";
        } else if ((myendpointName != null && myendpointName.trim().length() > 0) &&
                (myendpointValue != null && myendpointValue.trim().length() > 0)) {
            add_my_endpoint(myendpointName,myendpointValue);
            myendpointName="";
            myendpointValue="";
        } else {
            bookmarklist = my_bookmark_list();
        }

        return SUCCESS;
    }

    public void add_my_endpoint(String name, String value) throws Exception {
        bookmarklist = my_bookmark_list();
        if (bookmarklist != null && bookmarklist.size() > 0) {
            boolean flag = false;
            for (int i = 0; i < bookmarklist.size(); i++) {
                Map<String, Object> bmmap = bookmarklist.get(i);
                if (value.trim().equals((String) bmmap.get("endpoint_id"))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                //String ipname = name + "::MYGCPEP";
                String bm_id = createBookmark(name, value, "/~/");
                Map<String, Object> bmmap = new HashMap<String, Object>();
                bmmap.put("id", bm_id);
                bmmap.put("name", name);
                bmmap.put("disp_name", name);
                bmmap.put("endpoint_id", value);
                bmmap.put("path", "/~/");
                bookmarklist.add(bmmap);
            }
        } else {
            //create bookmark
            bookmarklist = new ArrayList<>();
            String ipname = name + "::SOURCE";

            String bm_id = createBookmark(name, value, "/~/");
            Map<String, Object> bmmap = new HashMap<String, Object>();
            bmmap.put("id", bm_id);
            bmmap.put("name", ipname);
            bmmap.put("disp_name", name);
            bmmap.put("endpoint_id", value);
            bmmap.put("path", "/~/");
            bookmarklist.add(bmmap);
        }
    }

    public List<Map<String,Object>> add_my_endpoint_transfer(String name, String value) throws Exception {
        List<Map<String,Object>> bmlist = my_bookmark_list();
        if (bmlist != null && bmlist.size() > 0) {
            boolean flag = false;
            for (int i = 0; i < bmlist.size(); i++) {
                Map<String, Object> bmmap = bmlist.get(i);
                if (value.trim().equals((String) bmmap.get("endpoint_id"))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                //String ipname = name + "::MYGCPEP";
                String bm_id = createBookmark(name, value, "/~/");
                Map<String, Object> bmmap = new HashMap<String, Object>();
                bmmap.put("id", bm_id);
                bmmap.put("name", name);
                bmmap.put("disp_name", name);
                bmmap.put("endpoint_id", value);
                bmmap.put("path", "/~/");
                bmlist.add(bmmap);
            }
        } else {
            //create bookmark
            bmlist = new ArrayList<>();
            String ipname = name + "::SOURCE";

            String bm_id = createBookmark(ipname, value, "/~/");
            Map<String, Object> bmmap = new HashMap<String, Object>();
            bmmap.put("id", bm_id);
            bmmap.put("name", ipname);
            bmmap.put("disp_name", name);
            bmmap.put("endpoint_id", value);
            bmmap.put("path", "/~/");
            bmlist.add(bmmap);
        }

        return bmlist;
    }

    public void my_endpoint_list() throws Exception {
        bookmarklist = new ArrayList<>();
        my_endpoint_search("administered-by-me");
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
                        String disp_ipname = (String) ipmap.get("display_name");
                        //String ipname = disp_ipname + "::MYGCPEP";
                        String bm_id = createBookmark(disp_ipname,eid,"/~/");
                        Map<String,Object> bmmap = new HashMap<String, Object>();
                        bmmap.put("id", bm_id);
                        bmmap.put("name", disp_ipname);
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
                    String disp_ipname = (String) ipmap.get("display_name");
                    //String ipname = disp_ipname + "::MYGCPEP";
                    String bm_id = createBookmark(disp_ipname,eid,"/~/");
                    Map<String,Object> bmmap = new HashMap<String, Object>();
                    bmmap.put("id", bm_id);
                    bmmap.put("name", disp_ipname);
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
        myendpoints = new HashMap<String, String>();

        String resource = "/endpoint_search";
        Map<String, String> params = new HashMap<String, String>();
        ////params.put("filter_scope", "my-gcp-endpoints");
        ////params.put("filter_scope", "my-endpoints");
        params.put("filter_scope", scope);
        r = client.getResult(resource, params);
        JSONArray data = r.document.getJSONArray("DATA");
        if (data.length() > 0) {
            String disp_name = null;
            for (int i = 0; i < data.length(); i++) {
                disp_name = data.getJSONObject(i).getString("display_name");
                if (disp_name == null || disp_name.equals("null")) {
                    disp_name = data.getJSONObject(i).getString("canonical_name");
                    if (disp_name == null || disp_name.equals("null"))
                        disp_name = data.getJSONObject(i).getString("username")+"#"+data.getJSONObject(i).getString("name");
                }
                myendpoints.put(data.getJSONObject(i).getString("id"),disp_name);
                Map<String,Object> ipmap = new HashMap<String, Object>();
                ipmap.put("display_name", disp_name);
                ipmap.put("id", data.getJSONObject(i).getString("id"));
                iplist.add(ipmap);
                logger.info("endpoint disp name: "+disp_name);
                logger.info("endpoint id: "+data.getJSONObject(i).getString("id"));
            }
        }
    }

    public Map<String, String> my_endpoint_search_transfer(String scope) throws Exception {
        JSONTransferAPIClient.Result r;
        Map<String, String> myeps = new HashMap<String, String>();

        String resource = "/endpoint_search";
        Map<String, String> params = new HashMap<String, String>();
        params.put("filter_scope", scope);
        r = client.getResult(resource, params);
        JSONArray data = r.document.getJSONArray("DATA");
        if (data.length() > 0) {
            String disp_name = null;
            for (int i = 0; i < data.length(); i++) {
                disp_name = data.getJSONObject(i).getString("display_name");
                if (disp_name == null || disp_name.equals("null")) {
                    disp_name = data.getJSONObject(i).getString("canonical_name");
                    if (disp_name == null || disp_name.equals("null"))
                        disp_name = data.getJSONObject(i).getString("username")+"#"+data.getJSONObject(i).getString("name");
                }
                myeps.put(data.getJSONObject(i).getString("id"),disp_name);
                logger.info("my endpoint disp name: "+disp_name);
                logger.info("my endpoint id: "+data.getJSONObject(i).getString("id"));
            }
        }
        return myeps;
    }

    public List<Map<String,Object>> my_bookmark_list() throws Exception {
        JSONTransferAPIClient.Result r;
        List<Map<String,Object>> bmlist = new ArrayList<>();

        String resource = "/bookmark_list";
        r = client.getResult(resource);
        JSONArray data = r.document.getJSONArray("DATA");
        if (data.length() > 0) {
            for (int i = 0; i < data.length(); i++) {
                Map<String, Object> ipmap = new HashMap<String, Object>();
                String name = data.getJSONObject(i).getString("name");
                ipmap.put("id", data.getJSONObject(i).getString("id"));
                ipmap.put("name", name);
                if (name.contains("SOURCE")) {
                    String[] namea = name.split("::");
                    ipmap.put("disp_name", namea[0] + " (Source) ");
                } else if (name.contains("DEST")) {
                    String[] namea = name.split("::");
                    ipmap.put("disp_name", namea[0] + " (Destination) ");
                } else {
                    ipmap.put("disp_name", name);
                }
                ipmap.put("endpoint_id", data.getJSONObject(i).getString("endpoint_id"));
                ipmap.put("path", data.getJSONObject(i).getString("path"));
                bmlist.add(ipmap);
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

    public Map<String, Object> removeBookmark(String id)throws Exception {
        Map<String, Object> bmObj = new HashMap<String, Object>();
        bmObj.put("index",-1);

        bookmarklist = my_bookmark_list();
        if (bookmarklist != null && bookmarklist.size() > 0) {
            for (int i = 0; i < bookmarklist.size(); i++) {
                Map<String, Object> bmmap = bookmarklist.get(i);
                if (id.trim().equals((String) bmmap.get("id"))) {
                    String name = (String) bmmap.get("name");
                    if (name.contains("::SOURCE")) {
                        bmObj.put("index",0);
                    } else if (name.contains("::DEST")) {
                        bmObj.put("index",1);
                    } else {
                        bmObj.put("index",2);
                    }
                    if (!deleteBookmark(id)) return bmObj;
                    break;
                }
            }

            //check source and destination
            int index = (Integer)bmObj.get("index");
            if (index != -1 || index != 2) {
                bookmarklist = my_bookmark_list();
                if (bookmarklist != null && bookmarklist.size() > 0) {
                    Map<String, Object> bmmap = bookmarklist.get(0);
                    String bid = (String) bmmap.get("id");
                    String bname = (String) bmmap.get("name");
                    String epid = (String) bmmap.get("endpoint_id");
                    String path = (String) bmmap.get("path");
                    String d_name = (String) bmmap.get("disp_name");

                    if (index == 0) {
                        bname += "::SOURCE";
                    } else if (index == 1) {
                        bname += "::DEST";
                    }
                    updateBookmark(bid,bname);
                    bmObj.put("id", bid);
                    bmObj.put("name", bname);
                    bmObj.put("disp_name", d_name);
                    bmObj.put("endpoint_id", epid);
                    bmObj.put("path", path);
                } else {
                    //no endpoint list
                    bmObj.put("index",3);
                }
            }
        }

        return bmObj;
    }

    public boolean deleteBookmark(String id) {
        //throws IOException, JSONException, GeneralSecurityException, APIError {
        try {
            String resource = "/bookmark/" + id;
            JSONTransferAPIClient.Result r = client.deleteResult(resource, null);
            String code = r.document.getString("code");
            if (code.startsWith("Deleted")) {
                reportUserMessage("Bookmark, " + id + " was created.");
                return true;
            }
        } catch (Exception e) {
            logger.error("Delete bookmark: " + e.toString());
            reportUserError(e.toString());
            return false;
        }
        return false;
    }

    public List<Map<String,Object>> getIplist() {return iplist;}
    public List<Map<String,Object>> getBookmarklist() {return bookmarklist;}
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
