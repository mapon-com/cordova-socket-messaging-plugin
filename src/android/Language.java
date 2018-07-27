package com.socketservice;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

class Language {
    private static final String PREFS_NAME = "SocketServiceLanguageFile";

    private Context context;

    Language(Context context){
        this.context=context;
    }

    public void setLanguage(JSONObject language) {
        SharedPreferences settings = this.context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        // Default fallback translations
        editor.putString("new_notification", "New notification");
        editor.putString("open", language.optString("open", "Open"));
        editor.putString("close", language.optString("close", "Close"));

        // Translations passed from cordova js
        Iterator<String> iter = language.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                String value = language.getString(key);
                editor.putString(key, value);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        editor.apply();
    }

    String getLanguageEntry(String name, JSONObject... values) {
        SharedPreferences settings = this.context.getSharedPreferences(PREFS_NAME, 0);

        if (values.length > 0) {
            String result = settings.getString(name, "");
            JSONObject placeholders = values[0];
            Iterator<String> iter = placeholders.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                result = result.replaceFirst("%" + key + "%", placeholders.optString(key, ""));
            }

            return result;
        } else {
            return settings.getString(name, null);
        }
    }
}
