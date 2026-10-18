package com.parsban.callai;

import android.content.Context;
import android.content.SharedPreferences;

public final class PolicyStore {
    private static final String PREFS = "avakav_contact_policies";

    private PolicyStore() {}

    private static String key(String phone) {
        String n = PhoneUtil.normalizeIran(phone);
        if (n.isEmpty()) n = PhoneUtil.digits(phone);
        return "p_" + n;
    }

    public static String get(Context c, String phone) {
        if (phone == null || phone.trim().isEmpty()) return AppPrefs.POLICY_ALWAYS;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(key(phone), AppPrefs.defaultPolicy(c));
    }

    public static void set(Context c, String phone, String policy) {
        if (phone == null || phone.trim().isEmpty()) return;
        String v = AppPrefs.POLICY_ALWAYS;
        if (AppPrefs.POLICY_ASK.equals(policy) || AppPrefs.POLICY_NEVER.equals(policy)) v = policy;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(phone), v).apply();
    }
}
