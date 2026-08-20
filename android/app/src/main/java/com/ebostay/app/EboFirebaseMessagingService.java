package com.ebostay.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EboFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "ebo_default";
    private static final String TAG = "EboFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Token refreshed");
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        try {
            ensureChannel();
            String title = "EBO Stay";
            String body = "You have an update";
            if (message.getNotification() != null) {
                if (message.getNotification().getTitle() != null)
                    title = message.getNotification().getTitle();
                if (message.getNotification().getBody() != null)
                    body = message.getNotification().getBody();
            } else if (message.getData() != null) {
                if (message.getData().get("title") != null) title = message.getData().get("title");
                if (message.getData().get("body") != null) body = message.getData().get("body");
            }

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            String deep = message.getData() != null ? message.getData().get("url") : null;
            if (deep != null && !deep.isEmpty()) {
                open.setData(android.net.Uri.parse(deep));
            }

            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "onMessageReceived failed", t);
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "EBO Stay", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Booking updates and offers");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
