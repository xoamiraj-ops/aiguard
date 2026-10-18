package com.parsban.callai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private static final int REQ_TREE = 500;
    private static final int REQ_PERMS = 501;

    private EditText apiKey;
    private TextView folder;
    private CheckBox cloudConsent;
    private Spinner defaultPolicy;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);

        apiKey = findViewById(R.id.input_api_key);
        folder = findViewById(R.id.folder_text);
        cloudConsent = findViewById(R.id.cloud_consent);
        defaultPolicy = findViewById(R.id.default_policy);

        apiKey.setText(SecretStore.openAiKey(this));
        cloudConsent.setChecked(AppPrefs.cloudConsent(this));
        defaultPolicy.setSelection(policyIndex(AppPrefs.defaultPolicy(this)));
        renderFolder();

        findViewById(R.id.button_folder).setOnClickListener(v -> chooseFolder());
        findViewById(R.id.button_permissions).setOnClickListener(v -> requestNeededPermissions());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
        findViewById(R.id.button_test_ai).setOnClickListener(v -> testAi());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);

        if (permissions.isEmpty()) {
            Toast.makeText(this, "مجوزهای لازم برقرار است", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(permissions.toArray(new String[0]), REQ_PERMS);
    }

    private void save() {
        try {
            SecretStore.setOpenAiKey(this, apiKey.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "ذخیره امن کلید API ناموفق بود", Toast.LENGTH_LONG).show();
            return;
        }
        AppPrefs.setCloudConsent(this, cloudConsent.isChecked());
        AppPrefs.setDefaultPolicy(this, policyAt(defaultPolicy.getSelectedItemPosition()));
        Toast.makeText(this, "تنظیمات آواکاو ذخیره شد", Toast.LENGTH_SHORT).show();
    }

    private void testAi() {
        try {
            SecretStore.setOpenAiKey(this, apiKey.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "ابتدا کلید را ذخیره کن", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "در حال بررسی اتصال…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                new OpenAiClient(this).testKey();
                runOnUiThread(() -> Toast.makeText(this, "اتصال OpenAI موفق است", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String m = e.getMessage() == null ? "اتصال ناموفق بود" : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderFolder() {
        String raw = AppPrefs.recordingTree(this);
        folder.setText(raw.isEmpty() ? "پوشه‌ای انتخاب نشده" : "دسترسی دائمی پوشه برقرار است");
    }

    private int policyIndex(String p) {
        if (AppPrefs.POLICY_ASK.equals(p)) return 1;
        if (AppPrefs.POLICY_NEVER.equals(p)) return 2;
        return 0;
    }

    private String policyAt(int i) {
        if (i == 1) return AppPrefs.POLICY_ASK;
        if (i == 2) return AppPrefs.POLICY_NEVER;
        return AppPrefs.POLICY_ALWAYS;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Toast.makeText(this, "ثبت دسترسی دائمی پوشه ناموفق بود", Toast.LENGTH_LONG).show();
            return;
        }

        AppPrefs.setRecordingTree(this, uri.toString());
        renderFolder();
    }
}
