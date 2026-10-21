package org.hotelmanager.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(WidgetBridgePlugin.class);
        registerPlugin(BackgroundSyncPlugin.class);
        registerPlugin(SecureHttpPlugin.class);
        super.onCreate(savedInstanceState);
    }
    @Override public void onStart() {
        BackgroundSyncWorker.foreground = true;
        super.onStart();
    }
    @Override public void onStop() {
        super.onStop();
        BackgroundSyncWorker.foreground = false;
    }
}
