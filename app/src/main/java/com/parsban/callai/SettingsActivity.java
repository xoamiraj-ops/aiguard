package com.parsban.callai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_TREE = 500;
    private static final int REQ_PERMS = 501;

    private EditText gateway;
    private EditText token;
    private TextView folder;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);

        gateway = findViewById(R.id.input_gateway);
        token = findViewById(R.id.input_token);
        folder = findViewById(R.id.folder_text);

        gateway.setText(AppPrefs.gateway(this));
        token.setText(SecretStore.deviceToken(this));
        renderFolder();

        findViewById(R.id.button_folder).setOnClickListener(v -> chooseFolder());
        findViewById(R.id.button_permissions).setOnClickListener(v -> requestNeededPermissions());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (permissions.isEmpty()) {
            Toast.makeText(this, "مجوزهای لازم برقرار است", Toast.LENGTH_SHORT).show();
            return;
        }

        requestPermissions(permissions.toArray(new String[0]), REQ_PERMS);
    }

    private void save() {
        AppPrefs.setGateway(this, gateway.getText().toString());
        try {
            SecretStore.setDeviceToken(this, token.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "ذخیره امن توکن ناموفق بود", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void renderFolder() {
        String raw = AppPrefs.recordingTree(this);
        folder.setText(raw.isEmpty() ? "پوشه‌ای انتخاب نشده" : raw);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception e) {
            Toast.makeText(this, "ثبت دسترسی دائمی پوشه ناموفق بود", Toast.LENGTH_LONG).show();
            return;
        }

        AppPrefs.setRecordingTree(this, uri.toString());
        renderFolder();
    }
}
