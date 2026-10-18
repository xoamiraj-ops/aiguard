package com.parsban.callai;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    public static final String POLICY_ALWAYS = "always";
    public static final String POLICY_ASK = "ask";
    public static final String POLICY_NEVER = "never";

    private static final String PREFS = "avakav_personal";
    private static final String TREE = "recording_tree";
    private static final String ACTIVE_SESSION = "active_session";
    private static final String CLOUD_CONSENT = "cloud_consent";
    private static final String DEFAULT_POLICY = "default_policy";

    private AppPrefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    public static boolean cloudConsent(Context c) {
        return p(c).getBoolean(CLOUD_CONSENT, false);
    }

    public static void setCloudConsent(Context c, boolean value) {
        p(c).edit().putBoolean(CLOUD_CONSENT, value).apply();
    }

    public static String defaultPolicy(Context c) {
        return p(c).getString(DEFAULT_POLICY, POLICY_ALWAYS);
    }

    public static void setDefaultPolicy(Context c, String value) {
        String v = POLICY_ALWAYS;
        if (POLICY_ASK.equals(value) || POLICY_NEVER.equals(value)) v = value;
        p(c).edit().putString(DEFAULT_POLICY, v).apply();
    }
}
