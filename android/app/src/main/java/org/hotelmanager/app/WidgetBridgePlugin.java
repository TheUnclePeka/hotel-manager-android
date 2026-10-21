package org.hotelmanager.app;

import android.content.Context;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Передача данных для виджета «Сегодня» из приложения. */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        String data = call.getString("data", "");
        Context context = getContext();
        context.getSharedPreferences(TodayWidget.PREFS, Context.MODE_PRIVATE).edit().putString(TodayWidget.KEY, data).apply();
        TodayWidget.updateAll(context);
        call.resolve();
    }
}
