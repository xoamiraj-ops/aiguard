package com.arzban.app;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private LinearLayout currencyList;
    private LinearLayout goldList;
    private LinearLayout coinList;
    private TextView lastUpdatedText;
    private TextView marketStatus;
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

        PriceStore.ensureTelegramMigration(this);
        if (PriceStore.isAutoEnabled(this)) {
            MarketSyncScheduler.ensureScheduled(this);
        }

        setContentView(R.layout.activity_main);

        currencyList = findViewById(R.id.currency_list);
        goldList = findViewById(R.id.gold_list);
        coinList = findViewById(R.id.coin_list);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        marketStatus = findViewById(R.id.market_status);
        serviceStatus = findViewById(R.id.service_status);
        errorText = findViewById(R.id.error_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        findViewById(R.id.refresh_button).setOnClickListener(v -> refreshOnce());

        startButton.setOnClickListener(v -> {
            maybeRequestNotificationPermission();
            PriceStore.setAutoEnabled(this, true);
            MarketSyncScheduler.ensureScheduled(this);
            MarketSyncScheduler.enqueueNow(this);
            render();
        });

        stopButton.setOnClickListener(v -> {
            PriceStore.setAutoEnabled(this, false);
            MarketSyncScheduler.cancelPeriodic(this);
            render();
        });

        findViewById(R.id.notification_settings_button).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });

        findViewById(R.id.privacy_button).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class))
        );

        render();

        if (PriceStore.lastFetchEpochMs(this) <= 0L) {
            MarketSyncScheduler.enqueueNow(this);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter("com.arzban.app.PRICE_UPDATED");
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
        MarketSyncScheduler.enqueueNow(this);
    }

    private void render() {
        fillGroup(currencyList, Asset.Group.CURRENCY);
        fillGroup(goldList, Asset.Group.GOLD);
        fillGroup(coinList, Asset.Group.COIN);

        String time = PriceStore.lastMarketTime(this);
        if ((time == null || time.isEmpty()) && PriceStore.lastFetchEpochMs(this) > 0L) {
            time = PriceFormat.localTime(PriceStore.lastFetchEpochMs(this));
        }

        if (PriceStore.lastFetchEpochMs(this) <= 0L) {
            lastUpdatedText.setText("هنوز نرخ معتبری از @DO_L4 دریافت نشده");
            marketStatus.setText("○ در انتظار اولین دریافت از @DO_L4");
            marketStatus.setTextColor(Color.parseColor("#8A907F"));
        } else {
            lastUpdatedText.setText(
                    "آخرین بروزرسانی قیمت" +
                    (time == null || time.isEmpty() ? "" : " • " + time)
            );

            if (PriceStore.isSourceFresh(this)) {
                marketStatus.setText("● منبع @DO_L4 — نرخ جدید دریافت شده");
                marketStatus.setTextColor(Color.parseColor("#5E8C61"));
            } else {
                marketStatus.setText("○ منبع @DO_L4 — آخرین نرخ منتشرشده");
                marketStatus.setTextColor(Color.parseColor("#8A907F"));
            }
        }

        boolean auto = PriceStore.isAutoEnabled(this);
        serviceStatus.setText(auto
                ? "● بررسی خودکار کانال فعال است • حدود هر ۳۰ دقیقه"
                : "○ بررسی خودکار کانال خاموش است");
        serviceStatus.setTextColor(Color.parseColor(auto ? "#5E8C61" : "#777777"));
        startButton.setEnabled(!auto);
        stopButton.setEnabled(auto);

        String err = PriceStore.lastError(this);
        errorText.setVisibility(err.isEmpty() ? View.GONE : View.VISIBLE);
        if (!err.isEmpty()) {
            errorText.setText("بررسی @DO_L4 ناموفق بود؛ آخرین نرخ معتبر ذخیره‌شده نمایش داده می‌شود.");
        }
    }

    private void fillGroup(LinearLayout container, Asset.Group group) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Asset asset : Asset.inGroup(group)) {
            View row = inflater.inflate(R.layout.market_row, container, false);
            TextView title = row.findViewById(R.id.row_title);
            TextView symbol = row.findViewById(R.id.row_symbol);
            TextView price = row.findViewById(R.id.row_price);
            TextView change = row.findViewById(R.id.row_change);

            title.setText(asset.titleFa);
            symbol.setText(asset.symbol);

            AssetQuote q = PriceStore.loadQuote(this, asset);
            if (q == null) {
                price.setText("—");
                change.setText("—");
                change.setTextColor(Color.parseColor("#8A907F"));
            } else {
                price.setText(PriceFormat.value(asset, q.value) + " " + asset.unitLabel());
                change.setText(PriceFormat.change(q.dailyChangePercent));
                String color = "#8A907F";
                if (q.dailyChangePercent != null) {
                    if (q.dailyChangePercent > 0) color = "#5E8C61";
                    else if (q.dailyChangePercent < 0) color = "#C65A52";
                }
                change.setTextColor(Color.parseColor(color));
            }

            container.addView(row);
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }
}
