package com.socketservice;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.List;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class NotificationSocketService extends Service {

    private static final String ACTION_START = "drivinglog.service.start";
    private static final String ACTION_STOP = "drivinglog.service.stop";

    private static final String INTENT_EXTRA_CONNECT_URL = "connect_url";

    private static final String PREF_ACTION = "pref_action";
    private static final String PREF_CONNECT_URL = "pref_connect_url";

    private static final String PREFS_NAME = "NotificationSocketServiceFile";

    private static final String PREF_ACTION_DEFAULT = ACTION_START;
    private static final String PREF_CONNECT_URL_DEFAULT = "";

    private static final String BROADCAST_INCOMING_EVENT = "incoming.event";
    private static final String BROADCAST_OUTGOING_EVENT = "outgoing.event";

    public static final String MAIN_APP_STATE_CHANGE_EVENT = "state.event";

    private static Socket socket;

    private static String host;
    private static Integer port;

    private String connectUrl;

    private static PowerManager.WakeLock wakeLock;
    private static final Object LOCK = NotificationSocketService.class;

    private boolean mRunning = false;

    protected boolean mMainAppRunningForeground = true;

    private BroadcastReceiver notificationReceiver = null;

    public static void start(Context context, String connectUrl) {
        Log.d("NotificationService", "NotificationSocketService STARTED");

        Intent intent = new Intent(context, NotificationSocketService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(INTENT_EXTRA_CONNECT_URL, connectUrl);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Log.d("NotificationService", "NotificationSocketService STOPPED");

        Intent intent = new Intent(context, NotificationSocketService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("NotificationService", "onStartCommand METHOD");

        String action;
        if (intent != null && intent.getAction() != null) {
            String intentConnectUrl = intent.getStringExtra(INTENT_EXTRA_CONNECT_URL);

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PREF_ACTION, intent.getAction());
            editor.putString(PREF_CONNECT_URL,intentConnectUrl );
            editor.apply();

            action = intent.getAction();
            this.connectUrl = intentConnectUrl;
        } else {
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            action = settings.getString(PREF_ACTION, PREF_ACTION_DEFAULT);
            this.connectUrl = settings.getString(PREF_CONNECT_URL, PREF_CONNECT_URL_DEFAULT);
        }

        if (action.equals(ACTION_START)) {
            mRunning = true;
            connect();
            listenBroadcasts();
        } else if (action.equals(ACTION_STOP)) {
            if (mRunning) {
                disconnect();
                stopListeningBroadcasts();
                stopSelf();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void connect() {
        Log.d("NotificationService", "connect METHOD");
        try {
            if (socket == null) {
                IO.Options options = new IO.Options();
                options.reconnection = true;
                options.forceNew = true;
                socket = IO.socket(this.connectUrl, options);
                Log.d("NotificationService", "SOCKET OPENED WITH URL: " + this.connectUrl);
                socket.on(Socket.EVENT_RECONNECT, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        Log.d("NotificationService", "EVENT_RECONNECT");
                    }
                });
                socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        connectionEstablishedEvent();
                    }
                });
                socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        Log.d("NotificationService", "EVENT_DISCONNECT");
                    }
                });
                socket.on("data", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        try {
                            Log.d("NotificationService", "EVENT INCOMING DATA");

                            if (args.length > 0) {
                                Ack ack = (Ack) args[args.length - 1];
                                if (ack != null) {
                                    ack.call();
                                }
                            }

                            JSONObject eventData = new JSONObject(args[0].toString());
                            String event = eventData.getString("event");
                            JSONObject payload = eventData.getJSONObject("data");
                            boolean alert = payload.optBoolean("alert", false);
                            String alertMessage = null;
                            if (alert) {
                                alertMessage = payload.optString("alertMessage", "empty message");
                            }
                            Log.d("NotificationService", "EVENT " + event);
                            Log.d("NotificationService", "PAYLOAD " + payload.toString());

                            if (isMainAppForeground()) {
                                Log.d("NotificationService", "APP RUNNING FOREGROUND, BROADCASTING EVENT");
                                broadcastEvent(event, payload);
                            } else {
                                if (alert) {
                                    Log.d("NotificationService", "APP RUNNING BACKGROUND, SHOWING NOTIFICATION AND ALERT AND BROADCASTING");
                                    showNotification(event, payload, alertMessage);
                                    showAlert(event, payload, alertMessage);
                                }
                            }

                            wakeUp();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                socket.connect();
            } else {
                connectionEstablishedEvent();
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void connectionEstablishedEvent() {
        try {
            Log.d("NotificationService", "EVENT_CONNECT");

            JSONObject payload = new JSONObject();
            payload.put("type", Socket.EVENT_CONNECT);

            if (isMainAppForeground()) {
                Log.d("NotificationService", "APP RUNNING FOREGROUND, BROADCASTING EVENT");
                broadcastEvent("socket_event", payload);
            }

            wakeUp();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private boolean isMainAppForeground() {
        boolean isMainAppForeground = false;
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcessInfo = activityManager.getRunningAppProcesses();
        if (runningProcessInfo != null) {
            for (ActivityManager.RunningAppProcessInfo appProcess : runningProcessInfo) {
                Log.d("NotificationService", String.valueOf(appProcess.importance));
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && appProcess.processName.equals(getApplication().getPackageName())) {
                    isMainAppForeground = true;
                }
            }
        }

        return isMainAppForeground;
    }

    private void listenBroadcasts() {
        stopListeningBroadcasts();
        notificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    JSONObject eventData = new JSONObject(intent.getExtras().getString("userdata"));

                    Log.d("NotificationService", "EMIT EVENT: " + eventData.toString());

                    if (socket != null) {
                        socket.emit("data", eventData);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, new IntentFilter(BROADCAST_OUTGOING_EVENT));

        final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isBackground = intent.getBooleanExtra("isBackground", false);

                mMainAppRunningForeground = !isBackground;

                Log.d("NotificationService", "isBackground EVENT: " + String.valueOf(isBackground));
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(stateReceiver, new IntentFilter(MAIN_APP_STATE_CHANGE_EVENT));
    }

    private void stopListeningBroadcasts() {
        if (notificationReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver);
        }
    }

    private void disconnect() {
        Log.d("NotificationService", "disconnect METHOD");

        if (socket != null) {
            socket.disconnect();
        }
        socket = null;
    }

    private void broadcastEvent(String event, JSONObject payload) {
        final Intent intent = new Intent(BROADCAST_INCOMING_EVENT);
        Bundle b = new Bundle();
        b.putString("userdata",
                "{" +
                    "event: '" + event + "'," +
                    "data: " + payload.toString() +
                "}");
        intent.putExtras(b);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcastSync(intent);
    }

    private void showAlert(String event, JSONObject payload, String alertMessage) {
        Intent alertIntent;
        alertIntent = new Intent(getApplicationContext(), AlertActivity.class);

        alertIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        alertIntent.putExtra("event", event);
        alertIntent.putExtra("data", payload.toString());
        alertIntent.putExtra("alertMessage", alertMessage);
        startActivity(alertIntent);
    }

    private void showNotification(String event, JSONObject payload, String alertMessage) {
        Language language = new Language(getApplicationContext());
        String messageTitle = language.getLanguageEntry("new_notification");
        String customMessageTitle = language.getLanguageEntry("new_" + event);
        if ((customMessageTitle != null) && (customMessageTitle.length() > 0)) {
            messageTitle = customMessageTitle;
        }

        String packageName = getApplicationContext().getPackageName();
        Resources resources = getApplicationContext().getResources();

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
        builder
                .setSmallIcon(resources.getIdentifier("icon", "mipmap", packageName))
                .setWhen(System.currentTimeMillis()).setAutoCancel(true)
                .setContentTitle(messageTitle)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentText(alertMessage);

        PackageManager pm = getPackageManager();
        Intent notificationIntent = pm.getLaunchIntentForPackage(packageName);

        notificationIntent.putExtra("event", event);
        notificationIntent.putExtra("data", payload.toString());

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        builder.setContentIntent(contentIntent);
        Notification n = builder.build();
        nm.notify(1, n);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        restartService.setPackage(getPackageName());
        PendingIntent restartServicePI = PendingIntent.getService(
                getApplicationContext(), 1, restartService,
                PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() +1000, restartServicePI);
    }

    private void wakeUp(){
        synchronized(LOCK) {
            if (wakeLock == null) {
                Log.d("NotificationService", "wakeUp wakeLock");
                PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "my_wakelock");
            }
        }
    }
}
