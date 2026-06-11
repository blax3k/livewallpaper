package com.hashilab.dev.editor.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPreferences {

    private static final String PREFS_NAME = "ProjectBrowser";
    public static final String PREF_SERVER_URL = "server_url";
    public static final String PREF_SESSION_COOKIE = "session_cookie";
    public static final String DEFAULT_SERVER_URL = "https://livewallpaper-backend-production.up.railway.app/";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the stored server URL, always without a trailing slash. */
    public static String getServerUrl(Context context) {
        String url = getPrefs(context).getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Returns the stored session cookie value, or null if not signed in. */
    public static String getSessionCookie(Context context) {
        return getPrefs(context).getString(PREF_SESSION_COOKIE, null);
    }

    public static void setSessionCookie(Context context, String cookie) {
        getPrefs(context).edit().putString(PREF_SESSION_COOKIE, cookie).apply();
    }

    public static void clearSessionCookie(Context context) {
        getPrefs(context).edit().remove(PREF_SESSION_COOKIE).apply();
    }
}

