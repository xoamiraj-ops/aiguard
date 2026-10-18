package com.parsban.callai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallDetailActivity extends Activity {
    private long sessionId;
    private CallSession session;

    private TextView title;
    private TextView meta;
    private TextView status;
    private TextView policy;
    private TextView summary;
    private TextView highlights;
    private TextView actions;
    private TextView transcript;
    private TextView error;
    private View analyze;
    private View audio;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_call_detail);
        sessionId = getIntent().getLongExtra("sessionId", -1L);
        if (sessionId <= 0) {
            finish();
            return;
        }

        title = findViewById(R.id.detail_title);
        meta = findViewById(R.id.detail_meta);
        status = findViewById(R.id.detail_status);
        policy = findViewById(R.id.detail_policy);
        summary = findViewById(R.id.detail_summary);
        highlights = findViewById(R.id.detail_highlights);
        actions = findViewById(R.id.detail_actions);
        transcript = findViewById(R.id.detail_transcript);
        error = findViewById(R.id.detail_error);
        analyze = findViewById(R.id.button_analyze);
        audio = findViewById(R.id.button_audio);

        findViewById(R.id.button_back).setOnClickListener(v -> finish());
        analyze.setOnClickListener(v -> analyzeNow());
        audio.setOnClickListener(v -> openAudio());
        findViewById(R.id.button_copy).setOnClickListener(v -> copySummary());
        findViewById(R.id.policy_always).setOnClickListener(v -> setPolicy(AppPrefs.POLICY_ALWAYS));
        findViewById(R.id.policy_ask).setOnClickListener(v -> setPolicy(AppPrefs.POLICY_ASK));
        findViewById(R.id.policy_never).setOnClickListener(v -> setPolicy(AppPrefs.POLICY_NEVER));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        session = new AppDb(this).get(sessionId);
        if (session == null) {
            finish();
            return;
        }

        String who = session.contactName == null || session.contactName.isEmpty()
                ? (session.phone == null || session.phone.isEmpty() ? "مکالمه" : session.phone)
                : session.contactName;
        title.setText(who);

        SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault());
        String m = f.format(new Date(session.createdAt));
        if (session.phone != null && !session.phone.isEmpty()) m += "\n" + session.phone;
        meta.setText(m);
        status.setText(MainActivity.statusFa(session.status));
        policy.setText("قانون این مخاطب: " + policyFa(PolicyStore.get(this, session.phone)));

        setSection(summary, session.summary, "هنوز خلاصه‌ای آماده نشده");
        setSection(highlights, formatArray(session.highlights), "نکته مهمی ثبت نشده");
        setSection(actions, formatArray(session.actionItems), "کار پیگیری‌شده‌ای وجود ندارد");
        setSection(transcript, session.transcript, "متن کامل هنوز آماده نشده");

        String err = session.error == null ? "" : session.error;
        error.setText(err);
        error.setVisibility(err.isEmpty() ? View.GONE : View.VISIBLE);

        boolean hasAudio = session.recordingUri != null && !session.recordingUri.isEmpty();
        analyze.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
        audio.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
    }

    private void setSection(TextView v, String value, String empty) {
        v.setText(value == null || value.trim().isEmpty() ? empty : value.trim());
    }

    private void analyzeNow() {
        if (session == null || session.recordingUri == null || session.recordingUri.isEmpty()) {
            Toast.makeText(this, "فایل صوتی برای این تماس موجود نیست", Toast.LENGTH_LONG).show();
            return;
        }
        session.status = "QueuedForAI";
        session.error = "";
        new AppDb(this).update(session);
        ProcessCallWorker.enqueue(this, session.id);
        Toast.makeText(this, "پردازش محلی شروع شد", Toast.LENGTH_SHORT).show();
        render();
    }

    private void openAudio() {
        try {
            Uri uri = Uri.parse(session.recordingUri);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "پخش‌کننده‌ای برای این فایل پیدا نشد", Toast.LENGTH_LONG).show();
        }
    }

    private void copySummary() {
        if (session == null || session.summary == null || session.summary.isEmpty()) return;
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("AvaKav summary", session.summary));
        Toast.makeText(this, "خلاصه کپی شد", Toast.LENGTH_SHORT).show();
    }

    private void setPolicy(String value) {
        if (session == null || session.phone == null || session.phone.isEmpty()) {
            Toast.makeText(this, "برای ضبط دستی قانون مخاطب قابل تنظیم نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        PolicyStore.set(this, session.phone, value);
        render();
    }

    private String policyFa(String p) {
        if (AppPrefs.POLICY_ASK.equals(p)) return "هر بار بپرس";
        if (AppPrefs.POLICY_NEVER.equals(p)) return "هرگز تحلیل نکن";
        return "همیشه تحلیل کن";
    }

    private String formatArray(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            JSONArray a = new JSONArray(raw);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "").trim();
                if (!s.isEmpty()) out.append("• ").append(s).append("\n");
            }
            return out.toString().trim();
        } catch (Exception e) {
            return raw;
        }
    }
}
