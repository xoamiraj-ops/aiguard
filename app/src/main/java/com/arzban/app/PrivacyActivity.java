package com.arzban.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);
        Button close = findViewById(R.id.privacy_close_button);
        close.setOnClickListener(v -> finish());
    }
}
