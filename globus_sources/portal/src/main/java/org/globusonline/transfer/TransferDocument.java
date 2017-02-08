package org.globusonline.transfer;

import java.util.ArrayList;

import org.json.*;

/**
 * Helper class for contructing JSON transfer documents.
 */
public class TransferDocument {
    private JSONObject json;
    private JSONArray items;

    public TransferDocument(String submissionId, String sourceEndpoint,
                            String destinationEndpoint)
        throws JSONException {
        json = new JSONObject();
        json.put("DATA_TYPE", "transfer");
        json.put("submission_id", submissionId);
        json.put("source_endpoint", sourceEndpoint);
        json.put("destination_endpoint", destinationEndpoint);
        items = new JSONArray();
        json.put("DATA", items);
    }

    /**
     * Set top level transfer options.
     *
     *  Example options: encrypt_data, sync_level
     */
    public void setOption(String name, String value) throws JSONException {
        json.put(name, value);
    }

    public void addItem(JSONObject item) throws JSONException {
        item.put("DATA_TYPE", "transfer_item");
        items.put(item);
    }

    public void addItem(String sourcePath, String destinationPath,
                        boolean recursive) throws JSONException {
        addItem(sourcePath, destinationPath, recursive, -1);
    }

    public void addItem(String sourcePath, String destinationPath,
                        boolean recursive, int verifySize)
        throws JSONException {
        JSONObject item = new JSONObject();
        item.put("DATA_TYPE", "transfer_item");
        item.put("source_path", sourcePath);
        item.put("destination_path", destinationPath);
        item.put("recursive", recursive);
        if (verifySize >= 0)
            item.put("verify_size", verifySize);
        items.put(item);
    }

    public JSONObject getJSONObject() {
        return json;
    }
}
