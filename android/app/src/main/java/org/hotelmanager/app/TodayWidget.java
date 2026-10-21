package org.hotelmanager.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Виджет «Сегодня»: заезды, выезды, проживающие и свободные номера.
 * Данные готовит приложение при каждой синхронизации (WidgetBridgePlugin) — на несколько дней вперёд,
 * виджет показывает запись для текущей даты по Москве. Сам виджет в сеть не ходит.
 */
public class TodayWidget extends AppWidgetProvider {
    static final String PREFS = "hotelmanager_widget";
    static final String KEY = "data";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = build(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodayWidget.class));
        if (ids.length == 0) return;
        RemoteViews views = build(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    private static String today(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        return f.format(new Date());
    }

    static RemoteViews build(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        v.setTextViewText(R.id.w_title, "Сегодня, " + today("dd.MM"));
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        JSONObject day = null;
        String updated = "";
        try {
            if (raw != null && !raw.isEmpty()) {
                JSONObject root = new JSONObject(raw);
                updated = root.optString("updatedAt", "");
                JSONObject days = root.optJSONObject("days");
                if (days != null && System.currentTimeMillis() < root.optLong("validUntil", 0)) day = days.optJSONObject(today("yyyy-MM-dd"));
            }
        } catch (Exception ignored) {
            day = null;
        }
        if (day == null) {
            v.setTextViewText(R.id.w_arrivals, "Откройте HotelManager, чтобы обновить данные");
            v.setTextViewText(R.id.w_departures, "");
            v.setTextViewText(R.id.w_rooms, "");
        } else {
            v.setTextViewText(R.id.w_arrivals, day.optString("arrivals", ""));
            v.setTextViewText(R.id.w_departures, day.optString("departures", ""));
            v.setTextViewText(R.id.w_rooms, day.optString("rooms", ""));
        }
        v.setTextViewText(R.id.w_updated, updated.isEmpty() ? "" : "обновлено " + updated);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_root, pi);
        return v;
    }
}
