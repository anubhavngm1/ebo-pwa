package com.ebostay.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Native FCM with heads-up (popup) notifications.
 */
public class EboFirebaseMessagingService extends FirebaseMessagingService {

    /** Must match FCM android.notification.channel_id from server */
    public static final String CHANNEL_ID = "ebo_alerts";
    private static final String TAG = "EboFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "FCM token refreshed");
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        try {
            ensureChannel(this);
            Map<String, String> data = message.getData();
            String title = "EBO Stay";
            String body = "You have an update";
            String imageUrl = null;
            String deepLink = null;

            if (message.getNotification() != null) {
                if (message.getNotification().getTitle() != null)
                    title = message.getNotification().getTitle();
                if (message.getNotification().getBody() != null)
                    body = message.getNotification().getBody();
                if (message.getNotification().getImageUrl() != null)
                    imageUrl = message.getNotification().getImageUrl().toString();
            }
            if (data != null) {
                if (data.get("title") != null) title = data.get("title");
                if (data.get("body") != null) body = data.get("body");
                if (data.get("image") != null) imageUrl = data.get("image");
                if (data.get("imageUrl") != null) imageUrl = data.get("imageUrl");
                if (data.get("url") != null) deepLink = data.get("url");
                if (data.get("link") != null) deepLink = data.get("link");
            }

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (deepLink != null && !deepLink.isEmpty()) {
                open.setData(android.net.Uri.parse(deepLink));
            }

            PendingIntent pi = PendingIntent.getActivity(
                    this, (int) (System.currentTimeMillis() % 100000), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            boolean silent = data != null && "1".equals(data.get("silent"));
            String channel = silent ? "ebo_silent" : CHANNEL_ID;
            int priority = silent ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MAX;

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, channel)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setPriority(priority)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(new long[]{0, 250, 150, 250});

            if (imageUrl != null && imageUrl.startsWith("http")) {
                Bitmap bmp = downloadImage(imageUrl);
                if (bmp != null) {
                    nb.setLargeIcon(bmp);
                    nb.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bmp)
                            .bigLargeIcon((Bitmap) null)
                            .setSummaryText(body));
                }
            }

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), nb.build());
            }
        } catch (Throwable t) {
            Log.e(TAG, "onMessageReceived failed", t);
        }
    }

    private Bitmap downloadImage(String src) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setDoInput(true);
            c.connect();
            InputStream is = c.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            return b;
        } catch (Exception e) {
            Log.w(TAG, "image download failed: " + e.getMessage());
            return null;
        }
    }

    /** Create alert (heads-up) + silent (tray only) channels. Safe to call from Activity. */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        // Heads-up / popup
        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ID, "EBO Alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Bookings, offers & reminders — popup alerts");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0, 250, 150, 250});
        alerts.enableLights(true);
        alerts.setShowBadge(true);
        alerts.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        alerts.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, aa);
        nm.createNotificationChannel(alerts);

        // Silent: tray + vibrate, no heads-up popup
        NotificationChannel silent = new NotificationChannel(
                "ebo_silent", "EBO Silent", NotificationManager.IMPORTANCE_DEFAULT);
        silent.setDescription("Quiet alerts — notification panel only, no popup");
        silent.enableVibration(true);
        silent.setVibrationPattern(new long[]{0, 180, 100, 180});
        silent.setShowBadge(true);
        silent.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        silent.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, aa);
        nm.createNotificationChannel(silent);
    }
}
