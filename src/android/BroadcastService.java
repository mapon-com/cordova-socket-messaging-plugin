package com.socketservice;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class BroadcastService extends CordovaPlugin {

    private static String TAG =  BroadcastService.class.getSimpleName();

    private static final String EVENTNAME_ERROR = "event name null or empty.";

    private static final String USERDATA = "userdata";

    private java.util.Map<String,BroadcastReceiver> receiverMap =
            new java.util.HashMap<String,BroadcastReceiver>(10);

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if( action.equals("fireNativeEvent")) {

            final String eventName = args.getString(0);
            if( eventName==null || eventName.isEmpty() ) {
                callbackContext.error(EVENTNAME_ERROR);

            }
            final JSONObject userData = args.getJSONObject(1);


            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    fireNativeEvent(eventName, userData);
                }
            });

            callbackContext.success();
            return true;
        } else if (action.equals("addEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }
            if (!receiverMap.containsKey(eventName)) {
                Log.d(TAG, "adding event listener " + eventName);

                final BroadcastReceiver r = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, final Intent intent) {

                        final Bundle b = intent.getExtras();

                        // parse the JSON passed as a string.
                        try {

                            String userData = "{}";
                            if (b != null) {//  in some broadcast there might be no extra info
                                userData = b.getString(USERDATA, "{}");
                            } else {
                                Log.v(TAG, "No extra information in intent bundle");
                            }
                            fireEvent(eventName, userData);

                        } catch (JSONException e) {
                            Log.e(TAG, "'userdata' is not a valid json object!");
                        }

                    }
                };

                registerReceiver(r, new IntentFilter(eventName));

                receiverMap.put(eventName, r);
            }
            callbackContext.success();

            return true;
        } else if (action.equals("removeEventListener")) {

            final String eventName = args.getString(0);
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            BroadcastReceiver r = receiverMap.remove(eventName);

            if (r != null) {
                Log.d(TAG, "removing event listener " + eventName);
                unregisterReceiver(r);
            }
            callbackContext.success();
            return true;
        }

        return false;
    }

    private void fireNativeEvent( final String eventName, JSONObject userData ) {
        if( eventName == null ) {
            throw new IllegalArgumentException("eventName parameter is null!");
        }

        final Intent intent = new Intent(eventName);

        if( userData != null ) {
            Bundle b = new Bundle();
            b.putString(USERDATA, userData.toString());
            intent.putExtras(b);
        }

        sendBroadcast( intent );
    }

    private void fireEvent( final String eventName, final Object jsonUserData) throws JSONException {
        Log.d(TAG, eventName);

        String method = null;
        if( jsonUserData != null ) {
            final String data = String.valueOf(jsonUserData);
            if (!(jsonUserData instanceof JSONObject)) {
                final JSONObject json = new JSONObject(data); // CHECK IF VALID
            }
            method = String.format("window.socketservice.fireEvent( '%s', %s );", eventName, data);
        }
        else {
            method = String.format("window.socketservice.fireEvent( '%s', {} );", eventName);
        }
        sendJavascript(method);
    }


    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        LocalBroadcastManager.getInstance(super.webView.getContext()).registerReceiver(receiver,filter);
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(super.webView.getContext()).unregisterReceiver(receiver);
    }

    private boolean sendBroadcast(Intent intent) {
        return LocalBroadcastManager.getInstance(super.webView.getContext()).sendBroadcast(intent);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void sendJavascript(final String javascript) {
        webView.getView().post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.sendJavascript(javascript);
                } else {
                    webView.loadUrl("javascript:".concat(javascript));
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        // deregister receiver
        for( BroadcastReceiver r : receiverMap.values() ) {
            unregisterReceiver(r);
        }

        receiverMap.clear();

        super.onDestroy();

    }
}
