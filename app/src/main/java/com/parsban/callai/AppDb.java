package com.parsban.callai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class AppDb extends SQLiteOpenHelper {
    private static final String DB = "parsban_call_ai.db";
    private static final int VERSION = 1;

    public AppDb(Context context) {
        super(context, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE calls (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "phone TEXT,direction TEXT,created_at INTEGER,connected_at INTEGER,ended_at INTEGER," +
                "status TEXT,customer_name TEXT,crm_type TEXT,crm_id TEXT,recording_uri TEXT," +
                "summary TEXT,transcript TEXT,crm_call_id TEXT,error TEXT)");
        db.execSQL("CREATE INDEX idx_calls_status ON calls(status)");
        db.execSQL("CREATE INDEX idx_calls_created ON calls(created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public synchronized long insert(CallSession s) {
        ContentValues v = values(s);
        return getWritableDatabase().insertOrThrow("calls", null, v);
    }

    public synchronized void update(CallSession s) {
        getWritableDatabase().update("calls", values(s), "id=?", new String[]{Long.toString(s.id)});
    }

    public synchronized CallSession get(long id) {
        try (Cursor c = getReadableDatabase().query(
                "calls", null, "id=?", new String[]{Long.toString(id)},
                null, null, null, "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized CallSession latestOpen() {
        try (Cursor c = getReadableDatabase().query(
                "calls", null,
                "status IN ('Ringing','Connected')",
                null, null, null, "created_at DESC", "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized List<CallSession> recent(int limit) {
        List<CallSession> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "calls", null, null, null, null, null,
                "created_at DESC", Integer.toString(limit))) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static ContentValues values(CallSession s) {
        ContentValues v = new ContentValues();
        v.put("phone", s.phone);
        v.put("direction", s.direction);
        v.put("created_at", s.createdAt);
        v.put("connected_at", s.connectedAt);
        v.put("ended_at", s.endedAt);
        v.put("status", s.status);
        v.put("customer_name", s.customerName);
        v.put("crm_type", s.crmType);
        v.put("crm_id", s.crmId);
        v.put("recording_uri", s.recordingUri);
        v.put("summary", s.summary);
        v.put("transcript", s.transcript);
        v.put("crm_call_id", s.crmCallId);
        v.put("error", s.error);
        return v;
    }

    private static CallSession read(Cursor c) {
        CallSession s = new CallSession();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        s.direction = c.getString(c.getColumnIndexOrThrow("direction"));
        s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        s.connectedAt = c.getLong(c.getColumnIndexOrThrow("connected_at"));
        s.endedAt = c.getLong(c.getColumnIndexOrThrow("ended_at"));
        s.status = c.getString(c.getColumnIndexOrThrow("status"));
        s.customerName = c.getString(c.getColumnIndexOrThrow("customer_name"));
        s.crmType = c.getString(c.getColumnIndexOrThrow("crm_type"));
        s.crmId = c.getString(c.getColumnIndexOrThrow("crm_id"));
        s.recordingUri = c.getString(c.getColumnIndexOrThrow("recording_uri"));
        s.summary = c.getString(c.getColumnIndexOrThrow("summary"));
        s.transcript = c.getString(c.getColumnIndexOrThrow("transcript"));
        s.crmCallId = c.getString(c.getColumnIndexOrThrow("crm_call_id"));
        s.error = c.getString(c.getColumnIndexOrThrow("error"));
        return s;
    }
}
