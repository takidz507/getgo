package com.taki.getgo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * v2.3 foreground download service. This is what makes a
 * download survive leaving the app: MainActivity (or the
 * share receiver) hands the link to this service, the service
 * runs DownloadEngine on its own thread, and Android keeps
 * the process alive because a foreground notification is
 * showing. The notification carries the live percentage, a
 * Cancel action, and (when done) a tap target into Library.
 *
 * No silent install, no new account, no server: the service
 * only downloads the one link it was given, into Download/GetGo.
 */
public class DownloadService extends Service {
    public static final String ACTION_START = "com.taki.getgo.START_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.taki.getgo.CANCEL_DOWNLOAD";
    public static final String EXTRA_LINK = "link";
    public static final String EXTRA_AUDIO = "audio_only";
    public static final String EXTRA_QUALITY = "quality";

    /** Broadcast MainActivity listens for while it is open. */
    public static final String ACTION_PROGRESS =
            "com.taki.getgo.DOWNLOAD_PROGRESS";
    public static final String EXTRA_PERCENT = "percent";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_SAVED_FILE = "saved_file";
    public static final String EXTRA_FINISHED = "finished";

    private static final String CHANNEL_ID = "getgo_downloads";
    private static final int NOTIF_ID = 1;
    private static final int DONE_NOTIF_ID = 2;

    private static volatile boolean running = false;

    private DownloadEngine engine;
    private NotificationManager notifManager;
    private String currentLink = "";
    private String savedFile = null;
    private String lastStatus = "";
    private boolean cancelledByUser = false;
    private long lastNotifUpdate = 0;

    public static boolean isRunning() {
        return running;
    }

    public static void startDownload(Context ctx, String link,
                                     boolean audioOnly, String quality) {
        Intent intent = new Intent(ctx, DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_LINK, link);
        intent.putExtra(EXTRA_AUDIO, audioOnly);
        intent.putExtra(EXTRA_QUALITY, quality);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void cancelDownload(Context ctx) {
        Intent intent = new Intent(ctx, DownloadService.class);
        intent.setAction(ACTION_CANCEL);
        ctx.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && notifManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_channel_downloads));
            notifManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelledByUser = true;
            if (engine != null) {
                engine.cancel();
            } else {
                stopForeground(true);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }
        if (running) {
            // One download at a time, same as the old UI rule.
            broadcast(-1, "", getString(R.string.status_busy), null, false);
            return START_NOT_STICKY;
        }
        final String link = intent.getStringExtra(EXTRA_LINK);
        final boolean audioOnly = intent.getBooleanExtra(EXTRA_AUDIO, false);
        final String quality = intent.getStringExtra(EXTRA_QUALITY);
        if (link == null || link.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        cancelledByUser = false;
        savedFile = null;
        currentLink = link;
        lastStatus = getString(R.string.status_connecting);
        startForeground(NOTIF_ID, buildProgressNotification(0, lastStatus));
        engine = new DownloadEngine(this, new DownloadEngine.Listener() {
            @Override
            public void onStatus(String text) {
                lastStatus = text;
                updateProgressNotification(-1, text);
                broadcast(-1, "", text, null, false);
            }

            @Override
            public void onProgress(int percent, String text) {
                updateProgressNotification(percent, text);
                broadcast(percent, text, null, null, false);
            }

            @Override
            public void onSaved(String fileName, String quality) {
                savedFile = fileName;
            }

            @Override
            public void onFinished() {
                running = false;
                if (cancelledByUser) {
                    stopForeground(true);
                    broadcast(-1, "", getString(R.string.status_cancelled),
                            null, true);
                    stopSelf();
                    return;
                }
                // Detach from foreground but keep a finished
                // notification the user can tap into Library.
                stopForeground(false);
                if (savedFile != null) {
                    Notification done = buildDoneNotification(
                            getString(R.string.status_done) + " " + savedFile);
                    if (notifManager != null) {
                        notifManager.notify(DONE_NOTIF_ID, done);
                    }
                    broadcast(100, "", getString(R.string.status_done)
                            + " " + savedFile, savedFile, true);
                } else {
                    Notification failed = buildDoneNotification(lastStatus);
                    if (notifManager != null) {
                        notifManager.notify(DONE_NOTIF_ID, failed);
                    }
                    broadcast(-1, "", lastStatus, null, true);
                }
                // Remove the progress notification; the done one stays.
                if (notifManager != null) {
                    notifManager.cancel(NOTIF_ID);
                }
                stopSelf();
            }
        });
        engine.start(link, audioOnly, quality != null ? quality : "Best");
        return START_NOT_STICKY;
    }

    private void broadcast(int percent, String text, String status,
                           String savedFile, boolean finished) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PERCENT, percent);
        intent.putExtra(EXTRA_TEXT, text == null ? "" : text);
        intent.putExtra(EXTRA_STATUS, status == null ? "" : status);
        intent.putExtra(EXTRA_SAVED_FILE, savedFile == null ? "" : savedFile);
        intent.putExtra(EXTRA_FINISHED, finished);
        sendBroadcast(intent);
    }

    private void updateProgressNotification(int percent, String text) {
        long now = System.currentTimeMillis();
        // Throttle notification updates: the extractor can report
        // many times a second, and each notify() costs work.
        if (percent >= 0 && percent < 100 && now - lastNotifUpdate < 400) {
            return;
        }
        lastNotifUpdate = now;
        if (notifManager == null) {
            return;
        }
        int pct = percent < 0 ? -1 : percent;
        String content = text != null && !text.isEmpty() ? text : lastStatus;
        Notification.Builder builder = baseBuilder(true)
                .setContentTitle(getString(R.string.notif_downloading))
                .setContentText(content)
                .setOngoing(true);
        if (pct >= 0) {
            builder.setProgress(100, pct, false);
        }
        notifManager.notify(NOTIF_ID, builder.build());
    }

    private Notification buildProgressNotification(int percent, String text) {
        return baseBuilder(true)
                .setContentTitle(getString(R.string.notif_downloading))
                .setContentText(text)
                .setProgress(100, percent, false)
                .setOngoing(true)
                .build();
    }

    private Notification buildDoneNotification(String text) {
        Intent open = new Intent(this, LibraryActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        return baseBuilder(false)
                .setContentTitle("GetGo")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
    }

    private Notification.Builder baseBuilder(boolean withCancel) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(tap);
        if (withCancel) {
            Intent cancel = new Intent(this, DownloadService.class);
            cancel.setAction(ACTION_CANCEL);
            PendingIntent cancelPi = PendingIntent.getService(this, 0, cancel,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_delete,
                    getString(R.string.cancel), cancelPi);
        }
        return builder;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
