package org.hotelmanager.app;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BackgroundSyncWorker extends Worker {
    static volatile boolean foreground = false;
    public BackgroundSyncWorker(Context context, WorkerParameters params) { super(context, params); }

    private JSONObject request(JSONObject state, String path, JSONObject data) throws Exception {
        HttpURLConnection c = TrustedConnection.open(getApplicationContext(), new URL(state.getString("url") + path));
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000); c.setReadTimeout(60000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + state.getString("token"));
            try (java.io.OutputStream out = c.getOutputStream()) { out.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
            if (c.getResponseCode() != 200) throw new Exception("Sync HTTP " + c.getResponseCode());
            try (java.io.InputStream in = c.getInputStream()) { return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
        } finally { c.disconnect(); }
    }

    @Override public Result doWork() {
        synchronized (BackgroundSyncState.LOCK) {
            if (foreground || isStopped()) return Result.success();
            try {
                JSONObject state = BackgroundSyncState.read(getApplicationContext());
                if (!state.has("token")) return Result.success();
                JSONArray queue = state.optJSONArray("ops");
                if (queue == null) queue = new JSONArray();
                JSONArray receipts = state.optJSONArray("receipts");
                if (receipts == null) receipts = new JSONArray();
                while (queue.length() > 0) {
                    if (isStopped()) return Result.retry();
                    JSONArray batch = new JSONArray();
                    int bytes = 0;
                    for (int i = 0; i < queue.length() && i < 50; i++) {
                        JSONObject op = queue.getJSONObject(i);
                        int size = op.toString().getBytes(StandardCharsets.UTF_8).length;
                        if (i > 0 && bytes + size > 8 * 1024 * 1024) break;
                        batch.put(op); bytes += size;
                    }
                    JSONArray results = request(state, "/api/sync/push", new JSONObject().put("ops", batch)).getJSONArray("results");
                    java.util.Set<String> done = new java.util.HashSet<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        done.add(result.getString("opId"));
                        for (int j = 0; j < batch.length(); j++) if (batch.getJSONObject(j).getString("opId").equals(result.getString("opId"))) {
                            result.put("entity", batch.getJSONObject(j).getString("entity")); break;
                        }
                        receipts.put(result);
                    }
                    if (done.isEmpty()) throw new Exception("Empty sync acknowledgement");
                    JSONArray remaining = new JSONArray();
                    for (int i = 0; i < queue.length(); i++) if (!done.contains(queue.getJSONObject(i).getString("opId"))) remaining.put(queue.get(i));
                    queue = remaining;
                    // An older snapshot must not overwrite newly acknowledged edits
                    // if the subsequent pull fails and the UI opens offline.
                    state.remove("snapshot"); state.remove("checkedAt");
                    state.put("ops", queue).put("receipts", receipts);
                    state.put("generation", java.util.UUID.randomUUID().toString());
                    BackgroundSyncState.write(getApplicationContext(), state);
                }
                if (isStopped()) return Result.retry();
                JSONObject snapshot = request(state, "/api/sync/pull", new JSONObject().put("since", 0).put("window", JSONObject.NULL));
                state.put("snapshot", snapshot).put("checkedAt", java.time.Instant.now().toString());
                state.put("generation", java.util.UUID.randomUUID().toString());
                BackgroundSyncState.write(getApplicationContext(), state);
                return Result.success();
            } catch (Exception e) { return Result.retry(); }
        }
    }
}
