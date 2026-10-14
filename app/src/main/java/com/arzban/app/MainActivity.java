package com.arzban.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView priceText;
    private TextView changeText;
    private TextView updatedText;
    private TextView serviceStatus;
    private TextView errorText;
    private Button startButton;
    private Button stopButton;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        priceText = findViewById(R.id.price_text);
        changeText = findViewById(R.id.change_text);
        updatedText = findViewById(R.id.updated_text);
        serviceStatus = findViewById(R.id.service_status);
        errorText = findViewById(R.id.error_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        findViewById(R.id.refresh_button).setOnClickListener(v -> refreshOnce());

        startButton.setOnClickListener(v -> {
            maybeRequestNotificationPermission();
            PriceStore.setAutoEnabled(this, true);
            Intent i = new Intent(this, PriceUpdateService.class);
            i.setAction(PriceUpdateService.ACTION_START);
            startForegroundService(i);
            render();
        });

        stopButton.setOnClickListener(v -> {
            PriceStore.setAutoEnabled(this, false);
            Intent i = new Intent(this, PriceUpdateService.class);
            i.setAction(PriceUpdateService.ACTION_STOP);
            startService(i);
            render();
        });

        findViewById(R.id.notification_settings_button).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });

        render();
        if (PriceStore.loadQuote(this) == null) refreshOnce();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(PriceUpdateService.ACTION_PRICE_UPDATED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(updateReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(updateReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void refreshOnce() {
        Intent i = new Intent(this, PriceUpdateService.class);
        i.setAction(PriceUpdateService.ACTION_REFRESH_ONCE);
        startForegroundService(i);
    }

    private void render() {
        Quote q = PriceStore.loadQuote(this);
        boolean auto = PriceStore.isAutoEnabled(this);

        if (q == null) {
            priceText.setText("—");
            changeText.setText("—");
            updatedText.setText("هنوز قیمتی دریافت نشده");
        } else {
            priceText.setText(PriceFormat.toman(q.priceToman));
            changeText.setText(PriceFormat.change(q.dailyChangePercent));
            String when = "آخرین بروزرسانی";
            if (!q.serverDate.isEmpty()) when += " • " + q.serverDate;
            if (!q.serverTime.isEmpty()) when += " • " + q.serverTime;
            updatedText.setText(when);

            String color = "#8A907F";
            if (q.dailyChangePercent != null) {
                if (q.dailyChangePercent > 0) color = "#5E8C61";
                else if (q.dailyChangePercent < 0) color = "#C65A52";
            }
            changeText.setTextColor(Color.parseColor(color));
        }

        serviceStatus.setText(auto
                ? "● بروزرسانی خودکار هر ۶۰ ثانیه فعال است"
                : "○ بروزرسانی ۶۰ ثانیه‌ای خاموش است");
        serviceStatus.setTextColor(Color.parseColor(auto ? "#5E8C61" : "#777777"));
        startButton.setEnabled(!auto);
        stopButton.setEnabled(auto);

        String err = PriceStore.lastError(this);
        errorText.setVisibility(err.isEmpty() ? View.GONE : View.VISIBLE);
        if (!err.isEmpty()) {
            errorText.setText("اتصال ناموفق؛ آخرین قیمت ذخیره‌شده نمایش داده می‌شود.");
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }
}
