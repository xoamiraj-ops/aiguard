package com.parsban.callai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public final class ContactResolver {
    private ContactResolver() {}

    public static String nameFor(Context c, String phone) {
        if (phone == null || phone.isEmpty()) return "";
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "";

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
        );
        String[] projection = { ContactsContract.PhoneLookup.DISPLAY_NAME };
        try (Cursor cur = c.getContentResolver().query(uri, projection, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                return n == null ? "" : n;
            }
        } catch (Exception ignored) {}
        return "";
    }
}
