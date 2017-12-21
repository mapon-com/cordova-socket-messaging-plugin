package com.socketservice;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class AlertActivity extends Activity {
    private static final String BROADCAST_INCOMING_EVENT = "incoming.event";
    public static int isAlertShown = 0;

    public void onCreate(Bundle state){
        super.onCreate(state);

        isAlertShown++;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        setContentView(getApplicationContext().getResources().getIdentifier("activity_alert", "layout", getApplicationContext().getPackageName()));

        Intent i = getIntent();

        final String event = i.getStringExtra("event");
        final String data = i.getStringExtra("data");
        final String alertMessage = i.getStringExtra("alertMessage");

        final Intent intent = new Intent(BROADCAST_INCOMING_EVENT);
        Bundle b = new Bundle();
        b.putString("userdata",
                "{" +
                        "event: '" + event + "'," +
                        "data: " + data + "," +
                        "alertMessage: '" + alertMessage + "'" +
                        "}");
        intent.putExtras(b);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcastSync(intent);

        Language language = new Language(getApplicationContext());

        String alertTitle = language.getLanguageEntry("new_notification");
        String customAlertTitle = language.getLanguageEntry("new_" + event);
        if ((customAlertTitle != null) && (customAlertTitle.length() > 0)) {
            alertTitle = customAlertTitle;
        }
        String open = language.getLanguageEntry("open");
        String close = language.getLanguageEntry("close");

        int titleViewId = getApplicationContext().getResources().getIdentifier("title", "id", getApplicationContext().getPackageName());
        int openViewId = getApplicationContext().getResources().getIdentifier("open", "id", getApplicationContext().getPackageName());
        int closeViewId = getApplicationContext().getResources().getIdentifier("close", "id", getApplicationContext().getPackageName());
        int textViewId = getApplicationContext().getResources().getIdentifier("text", "id", getApplicationContext().getPackageName());

        ((TextView) findViewById(titleViewId)).setText(alertTitle);
        ((TextView) findViewById(openViewId)).setText(open);
        ((TextView) findViewById(closeViewId)).setText(close);

        ((TextView) findViewById(textViewId)).setText(alertMessage);

        findViewById(openViewId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                nMgr.cancelAll();

                PackageManager pm = getPackageManager();
                Intent notificationIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());

                notificationIntent.putExtra("event", event);
                notificationIntent.putExtra("data", data);
                notificationIntent.putExtra("alertMessage", alertMessage);

                startActivity(notificationIntent);
                finish();
            }
        });

        findViewById(closeViewId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    public void onDestroy() {
        isAlertShown--;
        super.onDestroy();
    }
}
