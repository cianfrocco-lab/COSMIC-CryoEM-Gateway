package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.GoauthAuthenticator;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ngbw.web.actions.NgbwSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by cyoun on 06/19/17.
 */
public class EndpointListJSONAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(EndpointListJSONAction.class.getName());
    private JSONTransferAPIClient client;
    private Map<String, String> endpointlist = new HashMap<String, String>();
    private String fulltext;

    public EndpointListJSONAction() {}

    public String endpoint_list() throws Exception {

        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);
        logger.info("full text: "+fulltext);
        my_endpoint_search(fulltext);
        return SUCCESS;
    }

    public void my_endpoint_search(String search_keyword) throws Exception {
        JSONTransferAPIClient.Result r;

        String resource = "/endpoint_search";
        Map<String, String> params = new HashMap<String, String>();
        params.put("filter_scope", "all");
        params.put("filter_fulltext",search_keyword);
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
                endpointlist.put(data.getJSONObject(i).getString("id"),disp_name);
            }
        }
    }

    public Map<String, String> getEndpointlist() {
        return endpointlist;
    }
    public void setEndpointlist(Map<String, String> endpointlist) {
        this.endpointlist = endpointlist;
    }
    public String getFulltext() {
        return fulltext;
    }
    public void setFulltext(String fulltext) {
        this.fulltext = fulltext;
    }

}
