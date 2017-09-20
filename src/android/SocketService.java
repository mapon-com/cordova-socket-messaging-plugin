package com.socketservice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class SocketService extends CordovaPlugin {
    @Override
    public void onResume(boolean multitasking) {
        broadcastBackgroundState(false);
        Log.d("SocketService", "RESUME EVENT");
    }

    @Override
    public void onPause(boolean multitasking) {
        broadcastBackgroundState(true);
        Log.d("SocketService", "PAUSE EVENT");
    }

    private void broadcastBackgroundState(boolean isBackground) {
        final Intent intent = new Intent(NotificationSocketService.MAIN_APP_STATE_CHANGE_EVENT);
        Bundle b = new Bundle();
        b.putBoolean("isBackground", isBackground);
        intent.putExtras(b);
        LocalBroadcastManager.getInstance(super.webView.getContext()).sendBroadcastSync(intent);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        webView.getView().setFilterTouchesWhenObscured(true);
        if (action.equals("startService")) {
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            } else {
                String connectUrl = args.get(0).toString();
                callbackContext.sendPluginResult(this.startService(connectUrl));
                return true;
            }
        } else if (action.equals("stopService")) {
            callbackContext.sendPluginResult(this.stopService());
            return true;
        } else if (action.equals("hasParam")) {
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }
            String paramName = args.getString(0);
            PluginResult result = this.hasExtra(paramName);
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("getParam")) {
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }
            String paramName = args.getString(0);
            PluginResult result = this.getExtra(paramName);
            callbackContext.sendPluginResult(result);
            return PluginResult.Status.OK.ordinal() == result.getStatus();
        } else if (action.equals("injectLangArray")) {
            Log.d("SocketService", "injectLangArray");
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }
            JSONObject langArray = new JSONObject(args.get(0).toString());
            Log.d("SocketService", langArray.toString());
            callbackContext.sendPluginResult(this.injectLangArray(langArray));
            return true;
        }

        return false;
    }

    private PluginResult startService(String connectUrl) {
        Context context = this.cordova.getActivity().getApplicationContext();
        NotificationSocketService.start(context, connectUrl);
        return new PluginResult(PluginResult.Status.OK, "SERVICE STARTED");
    }

    private PluginResult stopService() {
        Context context = this.cordova.getActivity().getApplicationContext();
        NotificationSocketService.stop(context);
        return new PluginResult(PluginResult.Status.OK, "SERVICE STOPPED");
    }

    private PluginResult hasExtra(String extraName) {
        Intent i = this.cordova.getActivity().getIntent();
        return new PluginResult(PluginResult.Status.OK, i.hasExtra(extraName));
    }

    @SuppressWarnings("ConstantConditions")
    private PluginResult getExtra(String extraName) {
        Intent i = this.cordova.getActivity().getIntent();
        if (i.hasExtra(extraName)) {
            Bundle bundle = i.getExtras();
            Object paramValue = bundle.get(extraName);
            String paramStringValue = "";

            if (paramValue.getClass() == Integer.class) {
                paramStringValue = String.valueOf(i.getIntExtra(extraName, 0));
            } else if (paramValue.getClass() == String.class) {
                paramStringValue = i.getStringExtra(extraName);
            }
            return new PluginResult(PluginResult.Status.OK, paramStringValue);
        } else {
            return new PluginResult(PluginResult.Status.ERROR);
        }
    }

    private PluginResult injectLangArray(JSONObject languageArray) {
        Language language = new Language(super.webView.getContext());
        language.setLanguage(languageArray);
        return new PluginResult(PluginResult.Status.OK, "LANGUAGE ARRAY INJECTED");
    }
}
