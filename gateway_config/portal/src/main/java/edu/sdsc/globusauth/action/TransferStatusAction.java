package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.controller.ProfileManager;
import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.GoauthAuthenticator;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.json.JSONArray;
//import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.web.actions.NgbwSupport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cyoun on 11/29/16.
 */
public class TransferStatusAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(TransferStatusAction.class.getName());
    private JSONTransferAPIClient client;
    private List<Map<String,Object>> statuslist;
    private Map<String, Object> taskmap;

    public String transfer_status() throws Exception {

        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);

        String taskId = request.getParameter("taskId");
        JSONTransferAPIClient.Result r;
        statuslist = new ArrayList<>();

        if (taskId != null && !taskId.isEmpty()) {
            String resource = "/task/" + taskId;
            Map<String, String> params = new HashMap<String, String>();
            String fields = "status,source_endpoint_display_name,"
                    +"destination_endpoint_display_name,request_time,files_transferred,faults,"
                    +"sync_level,files,directories,files_skipped,bytes_transferred";
            params.put("fields", fields);
            r = client.getResult(resource, params);
            taskmap = new HashMap<String, Object>();
            taskmap.put("task_id", taskId);
            taskmap.put("source_endpoint_display_name", r.document.getString("source_endpoint_display_name"));
            taskmap.put("destination_endpoint_display_name", r.document.getString("destination_endpoint_display_name"));
            taskmap.put("request_time", r.document.getString("request_time")+" UTC");
            taskmap.put("status", r.document.getString("status"));
			try {
                String c_time = r.document.getString("completion_time");
                if (c_time != null)
                    taskmap.put("completion_time", c_time+" UTC");
                else
                    taskmap.put("completion_time", "");
            } catch (org.json.JSONException e) {
                taskmap.put("completion_time", "");
            }
            taskmap.put("files_transferred", r.document.getInt("files_transferred"));
            taskmap.put("faults", r.document.getInt("faults"));
            try {
                taskmap.put("sync_level", r.document.getInt("sync_level"));
            } catch (org.json.JSONException e) {
                taskmap.put("sync_level", "Not available");
            }
            taskmap.put("files", r.document.getInt("files"));
            taskmap.put("directories", r.document.getInt("directories"));
            taskmap.put("files_skipped", r.document.getInt("files_skipped"));
	    	taskmap.put("bytes_transferred", humanReadableByteCount(r.document.getLong("bytes_transferred"),true));
            statuslist.add(taskmap);

        } else {
            String resource = "/task_list";
            Map<String, String> params = new HashMap<String, String>();
            LocalDate today = LocalDate.now();
            LocalDate previousWeek = today.minus(2, ChronoUnit.WEEKS);
            params.put("filter", "request_time:"+previousWeek);
            r = client.getResult(resource, params);
            JSONArray data = r.document.getJSONArray("DATA");
            if (data.length() > 0) {
                for (int i = 0; i < data.length(); i++) {
                    taskmap = new HashMap<String, Object>();
                    taskmap.put("task_id", data.getJSONObject(i).getString("task_id"));
                    taskmap.put("source_endpoint_display_name", data.getJSONObject(i).getString("source_endpoint_display_name"));
                    taskmap.put("destination_endpoint_display_name", data.getJSONObject(i).getString("destination_endpoint_display_name"));
                    taskmap.put("request_time", data.getJSONObject(i).getString("request_time")+" UTC");
                    taskmap.put("status", data.getJSONObject(i).getString("status"));
					try {
                        String c_time = data.getJSONObject(i).getString("completion_time");
                        if (c_time != null)
                            taskmap.put("completion_time", c_time+" UTC");
                        else
                            taskmap.put("completion_time", "");
                    } catch (org.json.JSONException e) {
                        taskmap.put("completion_time", "");
                    }
                    taskmap.put("files_transferred", data.getJSONObject(i).getInt("files_transferred"));
                    taskmap.put("faults", data.getJSONObject(i).getInt("faults"));
                    try {
                        taskmap.put("sync_level", data.getJSONObject(i).getInt("sync_level"));
                    } catch (org.json.JSONException e) {
                        taskmap.put("sync_level", "Not available");
                    }
                    taskmap.put("files", data.getJSONObject(i).getInt("files"));
                    taskmap.put("directories", data.getJSONObject(i).getInt("directories"));
                    taskmap.put("files_skipped", data.getJSONObject(i).getInt("files_skipped"));
		    taskmap.put("bytes_transferred", humanReadableByteCount(data.getJSONObject(i).getLong("bytes_transferred"),true));
                    statuslist.add(taskmap);
                }
            }
        }

        //update transfer record
        ProfileManager profileManager = new ProfileManager();
        TransferAction txaction = new TransferAction();
        Long user_id = (Long) getSession().get("user_id");
        List<String> tr = profileManager.loadRecord(user_id);
        if (tr != null && tr.size() > 0) {
            String dest_path = ( String ) getSession().get
                ( OauthConstants.DEST_ENDPOINT_PATH );
            /*
            WorkbenchSession wbs = ( WorkbenchSession ) getSession().get
                ( "workbenchSession" );
            */
            for (String taskid: tr)
            {
                //profileManager.updateRecord(txaction.updateTask(taskid,client));
                profileManager.updateRecord (
                    txaction.updateTask ( taskid,client ), dest_path );
            }
        }

        return SUCCESS;

    }

    private String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }

    //public Map<String,Object> getTaskmap() {return taskmap;}
    //public void setTaskmap(Map<String,Object> taskmap) {this.taskmap = taskmap;}
    public List<Map<String,Object>> getStatuslist() {return statuslist;}
    public void setStatuslist(List<Map<String,Object>> statuslist) {this.statuslist = statuslist;}

}
