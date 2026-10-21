package org.hotelmanager.app;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

@CapacitorPlugin(name = "BackgroundSync")
public class BackgroundSyncPlugin extends Plugin {
    private static final String WORK = "hotelmanager-hourly-sync";
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private interface Operation { JSObject run() throws Exception; }
    private void execute(PluginCall call, Operation op) {
        executor.execute(() -> { synchronized (BackgroundSyncState.LOCK) {
            try { call.resolve(op.run()); } catch (Exception e) { call.reject("Background sync storage failed"); }
        }});
    }
    @PluginMethod public void configure(PluginCall call) {
        execute(call, () -> {
            JSONObject state = BackgroundSyncState.read(getContext());
            String url = call.getString("url", "");
            String token = call.getString("token", "");
            if (!url.equals(state.optString("url")) || !token.equals(state.optString("token"))) state = new JSONObject();
            state.put("url", url).put("token", token).put("ops", call.getArray("ops", new JSArray()));
            BackgroundSyncState.write(getContext(), state);
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(BackgroundSyncWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints).setInitialDelay(1, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build();
            WorkManager.getInstance(getContext()).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request);
            return new JSObject();
        });
    }
    @PluginMethod public void read(PluginCall call) {
        execute(call, () -> {
            JSONObject state = BackgroundSyncState.read(getContext());
            JSObject result = new JSObject();
            if (!call.getString("url", "").equals(state.optString("url")) || !call.getString("token", "").equals(state.optString("token"))) return result;
            for (String name : new String[]{"snapshot", "receipts", "checkedAt", "generation"}) if (state.has(name)) result.put(name, state.get(name));
            return result;
        });
    }
    @PluginMethod public void acknowledge(PluginCall call) {
        execute(call, () -> {
            JSONObject state = BackgroundSyncState.read(getContext());
            if (!call.getString("generation", "").equals(state.optString("generation"))) return new JSObject();
            state.remove("snapshot"); state.remove("receipts"); state.remove("checkedAt");
            BackgroundSyncState.write(getContext(), state);
            return new JSObject();
        });
    }
    @PluginMethod public void clear(PluginCall call) {
        execute(call, () -> {
            WorkManager.getInstance(getContext()).cancelUniqueWork(WORK);
            BackgroundSyncState.write(getContext(), new JSONObject());
            return new JSObject();
        });
    }
}
