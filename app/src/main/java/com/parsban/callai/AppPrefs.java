package com.parsban.callai;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String PREFS = "parsban_call_ai";
    private static final String GATEWAY = "gateway";
    private static final String TREE = "recording_tree";
    private static final String ACTIVE_SESSION = "active_session";

    private AppPrefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String gateway(Context c) {
        return p(c).getString(GATEWAY, "https://crm.amniathoshmand.ir/call-ai/api.php");
    }

    public static void setGateway(Context c, String value) {
        p(c).edit().putString(GATEWAY, value == null ? "" : value.trim()).apply();
    }

    public static String recordingTree(Context c) {
        return p(c).getString(TREE, "");
    }

    public static void setRecordingTree(Context c, String value) {
        p(c).edit().putString(TREE, value == null ? "" : value).apply();
    }

    public static long activeSession(Context c) {
        return p(c).getLong(ACTIVE_SESSION, -1L);
    }

    public static void setActiveSession(Context c, long id) {
        p(c).edit().putLong(ACTIVE_SESSION, id).apply();
    }

    public static void clearActiveSession(Context c) {
        p(c).edit().remove(ACTIVE_SESSION).apply();
    }
}
