package com.reptrack.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StepCounterService extends Service implements SensorEventListener {
    public static final String CHANNEL = "reptrack_steps";
    private SensorManager manager;
    private Sensor sensor;
    private SharedPreferences prefs;
    private boolean firstReading = true;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("reptrack", MODE_PRIVATE);
        createChannel();
        long initialSteps = prefs.getLong("steps_today", 0);
        String stepStr = String.format(Locale.US, "%,d", initialSteps);

        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_steps);
        customView.setTextViewText(R.id.notif_steps, stepStr);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_steps)
                .setColor(0xFFC6FF63)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(true).setOnlyAlertOnce(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        else startForeground(41, notification);
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor != null) manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Step tracking", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Ongoing notification while Routine Track counts steps");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void updateNotification(long steps) {
        String stepStr = String.format(Locale.US, "%,d", steps);
        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_steps);
        customView.setTextViewText(R.id.notif_steps, stepStr);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_steps)
                .setColor(0xFFC6FF63)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(41, notification);
        }
    }

    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.values == null || event.values.length == 0) return;
        long cumulative = (long) event.values[0];
        String day = today();
        String savedDay = prefs.getString("step_day", "");
        long total = prefs.getLong("steps_today", 0);
        long previous = prefs.getLong("sensor_last", -1);
        if (!day.equals(savedDay)) {
            if (!savedDay.isEmpty()) prefs.edit().putLong("steps_" + savedDay, total).apply();
            total = 0;
            prefs.edit().putString("step_day", day).putLong("steps_today", 0).apply();
            previous = -1;
        }
        if (previous >= 0 && cumulative >= previous) total += cumulative - previous;
        // A sensor reset (usually reboot) establishes a new baseline instead of subtracting a negative delta.
        prefs.edit().putLong("sensor_last", cumulative).putLong("steps_today", total).putString("step_day", day).apply();
        firstReading = false;
        updateNotification(total);
        Intent update = new Intent("com.reptrack.app.STEPS_UPDATED");
        update.setPackage(getPackageName());
        sendBroadcast(update);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "UPDATE_NOTIFICATION".equals(intent.getAction())) {
            long currentSteps = prefs.getLong("steps_today", 0);
            updateNotification(currentSteps);
        }
        return START_STICKY;
    }
    @Override public void onDestroy() { if (manager != null) manager.unregisterListener(this); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
