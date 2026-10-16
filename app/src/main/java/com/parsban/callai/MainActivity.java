package com.parsban.callai;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView screeningStatus;
    private TextView folderStatus;
    private TextView gatewayStatus;
    private LinearLayout recent;
    private TextView empty;
    private final java.util.concurrent.ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        screeningStatus = findViewById(R.id.status_screening);
        folderStatus = findViewById(R.id.status_folder);
        gatewayStatus = findViewById(R.id.status_gateway);
        recent = findViewById(R.id.recent_calls);
        empty = findViewById(R.id.empty_text);

        findViewById(R.id.button_setup).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.button_role).setOnClickListener(v -> requestScreeningRole());

        findViewById(R.id.button_test).setOnClickListener(v -> testGateway());

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
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
            startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 300);
        } else {
            Toast.makeText(this, "تشخیص تماس فعال است", Toast.LENGTH_SHORT).show();
        }
    }

    private void testGateway() {
        Toast.makeText(this, "در حال تست…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                new GatewayClient(this).health();
                runOnUiThread(() ->
                        Toast.makeText(this, "اتصال Gateway موفق است", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "اتصال ناموفق: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void render() {
        RoleManager rm = getSystemService(RoleManager.class);
        boolean screening = rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        screeningStatus.setText(screening
                ? "● تشخیص تماس فعال است"
                : "○ نقش تشخیص تماس فعال نیست");
        screeningStatus.setTextColor(Color.parseColor(screening ? "#285940" : "#B64E47"));

        String tree = AppPrefs.recordingTree(this);
        folderStatus.setText(tree.isEmpty()
                ? "○ پوشه ضبط مکالمات انتخاب نشده"
                : "● دسترسی پوشه ضبط سامسونگ برقرار است");

        String gateway = AppPrefs.gateway(this);
        gatewayStatus.setText(gateway.isEmpty()
                ? "○ Gateway تنظیم نشده"
                : "● Gateway: " + gateway);

        List<CallSession> items = new AppDb(this).recent(20);
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

            name.setText(s.customerName == null || s.customerName.isEmpty()
                    ? "مشتری شناسایی‌نشده"
                    : s.customerName);
            phone.setText(s.phone + "  •  " + time.format(new Date(s.createdAt)));
            status.setText(statusFa(s.status));
            summary.setText(s.summary == null || s.summary.isEmpty()
                    ? (s.error == null ? "" : s.error)
                    : s.summary);
            summary.setVisibility(summary.getText().length() == 0 ? View.GONE : View.VISIBLE);

            recent.addView(row);
        }
    }

    private String statusFa(String status) {
        if (status == null) return "";
        switch (status) {
            case "Ringing": return "در حال زنگ خوردن";
            case "Dialing": return "تماس خروجی";
            case "Connected": return "در حال مکالمه";
            case "Missed": return "پاسخ داده نشد";
            case "WaitingRecording": return "در انتظار فایل ضبط";
            case "NeedsRecordingFolder": return "پوشه ضبط تنظیم نشده";
            case "RecordingNotFound": return "فایل ضبط پیدا نشد";
            case "QueuedForAI": return "در صف پردازش";
            case "ProcessingAI": return "در حال تبدیل صدا و خلاصه‌سازی";
            case "RetryingAI": return "تلاش مجدد برای پردازش";
            case "Completed": return "ثبت‌شده در CRM";
            case "Failed": return "پردازش ناموفق";
            default: return status;
        }
    }
}
