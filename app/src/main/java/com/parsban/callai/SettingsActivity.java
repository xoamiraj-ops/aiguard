package com.parsban.callai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_TREE = 500, REQ_PERMS = 501;
    private TextView folder, modelStatus, whisperStatus, qwenStatus;
    private Spinner defaultPolicy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private final Runnable refreshModels = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            renderModels();
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_settings);
        folder=findViewById(R.id.folder_text); modelStatus=findViewById(R.id.model_status);
        whisperStatus=findViewById(R.id.whisper_status); qwenStatus=findViewById(R.id.qwen_status);
        defaultPolicy=findViewById(R.id.default_policy);
        defaultPolicy.setSelection(policyIndex(AppPrefs.defaultPolicy(this)));
        renderFolder(); renderModels();

        findViewById(R.id.button_folder).setOnClickListener(v->chooseFolder());
        findViewById(R.id.button_permissions).setOnClickListener(v->requestNeededPermissions());
        findViewById(R.id.button_download_models).setOnClickListener(v->{
            LocalModels.startDownloads(this);
            Toast.makeText(this,"دانلود مدل‌ها شروع شد. وضعیت در اعلان دانلود نمایش داده می‌شود.",Toast.LENGTH_LONG).show();
            renderModels();
        });
        findViewById(R.id.button_delete_models).setOnClickListener(v->{
            LocalModels.deleteModels(this); Toast.makeText(this,"مدل‌های محلی حذف شدند",Toast.LENGTH_SHORT).show(); renderModels();
        });
        findViewById(R.id.button_save).setOnClickListener(v->save());
    }

    @Override protected void onResume(){ super.onResume(); resumed=true; handler.removeCallbacks(refreshModels); handler.post(refreshModels); }
    @Override protected void onPause(){ resumed=false; handler.removeCallbacks(refreshModels); super.onPause(); }

    private void chooseFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQ_TREE);
    }

    private void requestNeededPermissions(){
        List<String> p=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.READ_PHONE_STATE);
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.READ_CONTACTS);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);
        if(p.isEmpty()){Toast.makeText(this,"مجوزهای لازم برقرار است",Toast.LENGTH_SHORT).show();return;}
        requestPermissions(p.toArray(new String[0]),REQ_PERMS);
    }

    private void save(){ AppPrefs.setDefaultPolicy(this,policyAt(defaultPolicy.getSelectedItemPosition())); Toast.makeText(this,"تنظیمات آواکاو ذخیره شد",Toast.LENGTH_SHORT).show(); }
    private void renderFolder(){ String raw=AppPrefs.recordingTree(this); folder.setText(raw.isEmpty()?"پوشه‌ای انتخاب نشده":"پوشه انتخاب شده است"); }
    private void renderModels(){
        LocalModels.ModelStatus s=LocalModels.status(this);
        modelStatus.setText(s.getText());
        whisperStatus.setText(s.getWhisperReady()?"● Whisper Small آماده • متن‌نگاری محلی":"○ Whisper Small هنوز آماده نیست");
        qwenStatus.setText(s.getQwenReady()?"● Qwen2.5 آماده • خلاصه‌سازی محلی":"○ Qwen2.5 هنوز آماده نیست");
    }
    private int policyIndex(String p){if(AppPrefs.POLICY_ASK.equals(p))return 1;if(AppPrefs.POLICY_NEVER.equals(p))return 2;return 0;}
    private String policyAt(int i){if(i==1)return AppPrefs.POLICY_ASK;if(i==2)return AppPrefs.POLICY_NEVER;return AppPrefs.POLICY_ALWAYS;}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_TREE||resultCode!=RESULT_OK||data==null)return;
        Uri uri=data.getData(); if(uri==null)return;
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception e){Toast.makeText(this,"ثبت دسترسی دائمی پوشه ناموفق بود",Toast.LENGTH_LONG).show();return;}
        AppPrefs.setRecordingTree(this,uri.toString()); renderFolder();
    }
}

