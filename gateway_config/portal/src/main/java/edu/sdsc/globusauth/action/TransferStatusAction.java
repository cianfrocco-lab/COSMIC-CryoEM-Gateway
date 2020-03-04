package edu.sdsc.globusauth.action;

import edu.sdsc.globusauth.controller.ProfileManager;
import edu.sdsc.globusauth.controller.Transfer2DataManager;
import edu.sdsc.globusauth.util.OauthConstants;
import org.apache.log4j.Logger;
import org.globusonline.transfer.Authenticator;
import org.globusonline.transfer.GoauthAuthenticator;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.json.JSONArray;
import org.ngbw.sdk.database.TransferRecord;
import org.ngbw.web.actions.NgbwSupport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cyoun on 11/29/16.
 * Update by Mona Wong
 */
public class TransferStatusAction extends NgbwSupport {

    private static final Logger logger = Logger.getLogger(TransferStatusAction.class.getName());
    private JSONTransferAPIClient client;
    private List<Map<String,Object>> statuslist;
    private Map<String, Object> taskmap;

    /**
     * This function is called by transfer_status.jsp.  Originally it was
     * enough to just get the status from Globus. However, we are now
     * changing the transfer status in the database to FAILED under certain
     * conditions, even if the Globus transfer is SUCCEEDED.  So this means,
     * this function has been modified to display the status from the 
     * database, rather than the Globus API call.
     **/
    public String transfer_status() throws Exception {

        //logger.debug ( "MONA: entered transfer_status()" );
        String accesstoken = (String) getSession().get(OauthConstants.CREDENTIALS);
        //logger.debug ( "MONA: accesstoken = " + accesstoken );
        String username = (String) getSession().get(OauthConstants.PRIMARY_USERNAME);
        //logger.debug ( "MONA: username = " + username );
        Authenticator authenticator = new GoauthAuthenticator(accesstoken);

        client = new JSONTransferAPIClient(username, null, null);
        client.setAuthenticator(authenticator);

        String taskId = request.getParameter("taskId");
        //logger.debug ( "MONA: taskId = " + taskId );
        TransferRecord tr = null;
        JSONTransferAPIClient.Result r;
        statuslist = new ArrayList<>();

        // First, update transfer record
        ProfileManager profileManager = new ProfileManager();
        TransferAction txaction = new TransferAction();
        Transfer2DataManager transfer_manager = null;
        Long user_id = (Long) getSession().get("user_id");
        //logger.debug ( "MONA: user_id = " + user_id );
        List<String> trlist = profileManager.loadRecord(user_id);
        //logger.debug ( "MONA: trlist = " + trlist );
        if (trlist != null && trlist.size() > 0) {
            String dest_path = ( String ) getSession().get
                    ( OauthConstants.DEST_ENDPOINT_PATH );
            //logger.debug ( "MONA: dest_path = " + dest_path );
            int val = 0;
            ArrayList<String> list = null;

            for (String taskid: trlist) {
                //logger.debug ( "MONA: taskid = " + taskid );
                tr = txaction.updateTask(taskid, client);
                transfer_manager = profileManager.updateRecord
                    ( tr, dest_path );

                // First, display the user errors
                list = transfer_manager.getUserSystemErrorMessages();
                if ( list != null && ! list.isEmpty() )
                    for ( String msg : list )
                        reportUserError ( msg );

                val = transfer_manager.getNumFilesSaved();
                if ( val == 1 )
                    reportUserMessage ( val +
                        " file was successfully saved" );
                else if ( val > 1 )
                    reportUserMessage ( val +
                        " files were successfully saved" );

                list = transfer_manager.getFailedFilesMessages();
                if ( list != null && ! list.isEmpty() )
                    for ( String msg : list )
                        reportUserError ( msg );

                val = transfer_manager.getNumDirectoriesSaved();
                if ( val == 1 )
                    reportUserMessage ( val +
                        " directory was successfully saved" );
                else if ( val > 1 )
                    reportUserMessage ( val +
                        " directories were successfully saved" );

                list = transfer_manager.getFailedDirectoriesMessages();
                if ( list != null && ! list.isEmpty() )
                    for ( String msg : list )
                        reportUserError ( msg );
            }
        }

        // If getting status for a single transfer...
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
            tr = TransferRecord.findTransferRecordByTaskId ( taskId );
            //logger.debug ( "MONA: tr = " + tr );
            //taskmap.put("status", r.document.getString("status"));
            taskmap.put ( "status", tr.getStatus() );
            //log.debug ( "MONA: status = " + tr.getStatus() );
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
            // Show last 7 days of user transfers
            String resource = "/task_list";
            Map<String, String> params = new HashMap<String, String>();
            LocalDate today = LocalDate.now();
            //LocalDate previousWeek = today.minus(2, ChronoUnit.WEEKS);
            LocalDate previousWeek = today.minusDays ( 7L );
            //logger.debug ( "MONA: previousWeek = " + previousWeek );
            params.put("filter", "request_time:"+previousWeek);
            r = client.getResult(resource, params);
            //logger.debug ( "MONA: r = " + r );
            JSONArray data = r.document.getJSONArray("DATA");
            //logger.debug ( "MONA: data = " + data );
            //logger.debug ( "MONA: data.length() = " + data.length() );
            String task_id = null;

            if (data.length() > 0) {
                for (int i = 0; i < data.length(); i++) {
                    taskmap = new HashMap<String, Object>();
                    //taskmap.put("task_id", data.getJSONObject(i).getString("task_id"));
                    task_id = data.getJSONObject(i).getString ( "task_id" );
                    //logger.debug ( "MONA: task_id = " + task_id );
                    taskmap.put ( "task_id", task_id );
                    tr = TransferRecord.findTransferRecordByTaskId ( task_id );
                    //logger.debug ( "MONA: tr = " + tr );

                    // This can happen if the gateway database is down when
                    // the transfer happened?
                    if ( tr == null )
                        continue;

                    taskmap.put("source_endpoint_display_name", data.getJSONObject(i).getString("source_endpoint_display_name"));
                    taskmap.put("destination_endpoint_display_name", data.getJSONObject(i).getString("destination_endpoint_display_name"));
                    taskmap.put("request_time", data.getJSONObject(i).getString("request_time")+" UTC");
                    //taskmap.put("status", data.getJSONObject(i).getString("status"));
                    taskmap.put ( "status", tr.getStatus() );

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
                } // for (int i = 0; i < data.length(); i++)
            } // if (data.length() > 0)
        } // else

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
