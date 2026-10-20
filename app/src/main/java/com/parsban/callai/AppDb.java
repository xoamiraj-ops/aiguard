package com.parsban.callai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class AppDb extends SQLiteOpenHelper {
    private static final String DB = "avakav.db";
    private static final int VERSION = 1;

    public AppDb(Context context) {
        super(context, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE calls (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "phone TEXT,direction TEXT,created_at INTEGER,connected_at INTEGER,ended_at INTEGER," +
                "status TEXT,contact_name TEXT,recording_uri TEXT,summary TEXT,highlights TEXT," +
                "action_items TEXT,transcript TEXT,ai_model TEXT,error TEXT)");
        db.execSQL("CREATE INDEX idx_calls_status ON calls(status)");
        db.execSQL("CREATE INDEX idx_calls_created ON calls(created_at)");
        db.execSQL("CREATE INDEX idx_calls_phone ON calls(phone)");
        db.execSQL("CREATE UNIQUE INDEX idx_calls_recording_uri ON calls(recording_uri) WHERE recording_uri IS NOT NULL AND recording_uri <> ''");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public synchronized long insert(CallSession s) {
        return getWritableDatabase().insertOrThrow("calls", null, values(s));
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

    public synchronized CallSession byRecordingUri(String uri) {
        try (Cursor c = getReadableDatabase().query("calls", null, "recording_uri=?",
                new String[]{uri}, null, null, null, "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized boolean hasRecordingUri(String uri) {
        if (uri == null || uri.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM calls WHERE recording_uri=? LIMIT 1",
                new String[]{uri})) {
            return c.moveToFirst();
        }
    }

    public synchronized CallSession findWaitingNear(long whenMs) {
        long window = 15L * 60L * 1000L;
        long from = whenMs - window;
        long to = whenMs + window;
        try (Cursor c = getReadableDatabase().query(
                "calls", null,
                "(recording_uri IS NULL OR recording_uri='') AND status IN ('WaitingRecording','RecordingNotFound','Connected') " +
                        "AND COALESCE(NULLIF(ended_at,0),created_at) BETWEEN ? AND ?",
                new String[]{Long.toString(from), Long.toString(to)},
                null, null,
                "ABS(COALESCE(NULLIF(ended_at,0),created_at)-" + whenMs + ") ASC",
                "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized CallSession latestOpen() {
        try (Cursor c = getReadableDatabase().query(
                "calls", null,
                "status IN ('Ringing','Dialing','Connected')",
                null, null, null, "created_at DESC", "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized List<CallSession> recent(int limit) {
        return query(null, null, limit);
    }

    public synchronized List<CallSession> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return recent(limit);
        String like = "%" + q + "%";
        return query(
                "contact_name LIKE ? OR phone LIKE ? OR summary LIKE ? OR transcript LIKE ? OR highlights LIKE ? OR action_items LIKE ?",
                new String[]{like, like, like, like, like, like},
                limit
        );
    }

    public synchronized int countToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM calls WHERE created_at>=?",
                new String[]{Long.toString(cal.getTimeInMillis())})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private List<CallSession> query(String selection, String[] args, int limit) {
        List<CallSession> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "calls", null, selection, args, null, null,
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
        v.put("contact_name", s.contactName);
        v.put("recording_uri", s.recordingUri);
        v.put("summary", s.summary);
        v.put("highlights", s.highlights);
        v.put("action_items", s.actionItems);
        v.put("transcript", s.transcript);
        v.put("ai_model", s.aiModel);
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
        s.contactName = c.getString(c.getColumnIndexOrThrow("contact_name"));
        s.recordingUri = c.getString(c.getColumnIndexOrThrow("recording_uri"));
        s.summary = c.getString(c.getColumnIndexOrThrow("summary"));
        s.highlights = c.getString(c.getColumnIndexOrThrow("highlights"));
        s.actionItems = c.getString(c.getColumnIndexOrThrow("action_items"));
        s.transcript = c.getString(c.getColumnIndexOrThrow("transcript"));
        s.aiModel = c.getString(c.getColumnIndexOrThrow("ai_model"));
        s.error = c.getString(c.getColumnIndexOrThrow("error"));
        return s;
    }
}

