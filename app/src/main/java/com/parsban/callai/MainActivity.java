package com.parsban.callai;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView screeningStatus;
    private TextView folderStatus;
    private TextView aiStatus;
    private TextView todayCount;
    private EditText searchInput;
    private LinearLayout recent;
    private TextView empty;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed = false;
    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            render();
            handler.postDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        screeningStatus = findViewById(R.id.status_screening);
        folderStatus = findViewById(R.id.status_folder);
        aiStatus = findViewById(R.id.status_ai);
        todayCount = findViewById(R.id.today_count);
        searchInput = findViewById(R.id.search_input);
        recent = findViewById(R.id.recent_calls);
        empty = findViewById(R.id.empty_text);

        findViewById(R.id.button_setup).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.button_role).setOnClickListener(v -> requestScreeningRole());
        findViewById(R.id.button_latest).setOnClickListener(v -> analyzeLatestRecording());
        findViewById(R.id.button_search).setOnClickListener(v -> render());

        long fromNotification = getIntent().getLongExtra("sessionId", -1L);
        if (fromNotification > 0) openDetail(fromNotification);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void requestScreeningRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 300);
        } else {
            Toast.makeText(this, "تشخیص تماس آواکاو فعال است", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeLatestRecording() {
        if (AppPrefs.recordingTree(this).isEmpty()) {
            Toast.makeText(this, "ابتدا پوشه ضبط سامسونگ را در تنظیمات انتخاب کن", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "در حال پیدا کردن آخرین فایل ضبط…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            Uri uri = RecordingScanner.findNewest(this);
            if (uri == null) {
                runOnUiThread(() -> Toast.makeText(this, "فایل صوتی در پوشه انتخاب‌شده پیدا نشد", Toast.LENGTH_LONG).show());
                return;
            }

            CallSession s = new CallSession();
            s.phone = "";
            s.direction = "Manual";
            s.createdAt = System.currentTimeMillis();
            s.connectedAt = s.createdAt;
            s.endedAt = s.createdAt;
            s.status = "QueuedForAI";
            s.contactName = "آخرین ضبط سامسونگ";
            s.recordingUri = uri.toString();
            s.id = new AppDb(this).insert(s);
            ProcessCallWorker.enqueue(this, s.id);

            runOnUiThread(() -> {
                render();
                Toast.makeText(this, "آخرین ضبط برای تحلیل ارسال شد", Toast.LENGTH_LONG).show();
                openDetail(s.id);
            });
        });
    }

    private void openDetail(long id) {
        Intent i = new Intent(this, CallDetailActivity.class);
        i.putExtra("sessionId", id);
        startActivity(i);
    }

    private void render() {
        RoleManager rm = getSystemService(RoleManager.class);
        boolean role = rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        boolean phonePermission = checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        boolean contactsPermission = checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        boolean callReady = role && phonePermission && contactsPermission;

        screeningStatus.setText(callReady
                ? "● تشخیص تماس آماده"
                : "○ راه‌اندازی تماس کامل نشده");
        screeningStatus.setTextColor(Color.parseColor(callReady ? "#3F6B50" : "#A44D47"));

        String tree = AppPrefs.recordingTree(this);
        folderStatus.setText(tree.isEmpty()
                ? "○ پوشه ضبط سامسونگ انتخاب نشده"
                : "● پوشه ضبط سامسونگ متصل است");
        folderStatus.setTextColor(Color.parseColor(tree.isEmpty() ? "#A44D47" : "#3F6B50"));

        boolean localReady = LocalModels.ready(this);
        aiStatus.setText(localReady
                ? "● هوش مصنوعی محلی آماده • بدون اینترنت"
                : "○ مدل‌های محلی هنوز دانلود نشده‌اند");
        aiStatus.setTextColor(Color.parseColor(localReady ? "#3F6B50" : "#A44D47"));

        AppDb db = new AppDb(this);
        todayCount.setText(Integer.toString(db.countToday()));

        String q = searchInput.getText() == null ? "" : searchInput.getText().toString();
        List<CallSession> items = db.search(q, 50);
        recent.removeAllViews();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat time = new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault());

        for (CallSession s : items) {
            View row = inflater.inflate(R.layout.item_call, recent, false);
            TextView name = row.findViewById(R.id.call_name);
            TextView phone = row.findViewById(R.id.call_phone);
            TextView status = row.findViewById(R.id.call_status);
            TextView summary = row.findViewById(R.id.call_summary);

            String title = s.contactName == null || s.contactName.isEmpty()
                    ? (s.phone == null || s.phone.isEmpty() ? "مکالمه" : s.phone)
                    : s.contactName;
            name.setText(title);

            String meta = time.format(new Date(s.createdAt));
            if (s.phone != null && !s.phone.isEmpty()) meta = s.phone + "  •  " + meta;
            phone.setText(meta);
            status.setText(statusFa(s.status));

            String body = s.summary == null ? "" : s.summary;
            if (body.isEmpty() && s.error != null) body = s.error;
            summary.setText(body);
            summary.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);

            row.setOnClickListener(v -> openDetail(s.id));
            recent.addView(row);
        }
    }

    public static String statusFa(String status) {
        if (status == null) return "";
        switch (status) {
            case "Ringing": return "در حال زنگ خوردن";
            case "Dialing": return "تماس خروجی";
            case "Connected": return "در حال مکالمه";
            case "Missed": return "پاسخ داده نشد";
            case "WaitingRecording": return "در انتظار فایل ضبط";
            case "NeedsRecordingFolder": return "پوشه ضبط تنظیم نشده";
            case "RecordingNotFound": return "فایل ضبط پیدا نشد";
            case "AwaitingApproval": return "منتظر تأیید برای تحلیل";
            case "Skipped": return "طبق تنظیم مخاطب تحلیل نشد";
            case "QueuedForAI": return "در صف تحلیل";
            case "DecodingAudio": return "در حال آماده‌سازی فایل صوتی";
            case "LoadingWhisper": return "در حال بارگذاری مدل تبدیل صدا به متن";
            case "TranscribingLocal": return "در حال تبدیل صدا به متن";
            case "SummarizingLocal": return "متن آماده است؛ در حال خلاصه‌سازی";
            case "ProcessingAI":
            case "ProcessingLocal": return "در حال پردازش محلی روی گوشی";
            case "RetryingAI":
            case "RetryingLocal": return "تلاش مجدد برای پردازش محلی";
            case "NeedsConsent": return "اجازه پردازش لازم است";
            case "NeedsApiKey": return "کلید API لازم است";
            case "NeedsLocalModels": return "مدل‌های محلی باید دانلود شوند";
            case "Completed": return "تحلیل آماده است";
            case "Failed": return "تحلیل ناموفق";
            default: return status;
        }
    }
}

