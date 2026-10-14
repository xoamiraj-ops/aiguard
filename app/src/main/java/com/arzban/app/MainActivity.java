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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout currencyList;
    private LinearLayout goldList;
    private LinearLayout coinList;
    private TextView lastUpdatedText;
    private TextView serviceStatus;
    private TextView errorText;
    private Button startButton;
    private Button stopButton;
    private Spinner widgetAsset1;
    private Spinner widgetAsset2;
    private Spinner widgetAsset3;
    private List<Asset> widgetChoices;
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

        currencyList = findViewById(R.id.currency_list);
        goldList = findViewById(R.id.gold_list);
        coinList = findViewById(R.id.coin_list);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        serviceStatus = findViewById(R.id.service_status);
        errorText = findViewById(R.id.error_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        widgetAsset1 = findViewById(R.id.widget_asset_1);
        widgetAsset2 = findViewById(R.id.widget_asset_2);
        widgetAsset3 = findViewById(R.id.widget_asset_3);

        setupWidgetSelectors();

        findViewById(R.id.save_widget_button).setOnClickListener(v -> saveWidgetSelection());
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
        if (PriceStore.lastFetchEpochMs(this) <= 0L) refreshOnce();
    }

    private void setupWidgetSelectors() {
        widgetChoices = new ArrayList<>(Asset.widgetChoices());
        List<String> labels = new ArrayList<>();
        for (Asset asset : widgetChoices) labels.add(asset.displayName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        widgetAsset1.setAdapter(adapter);
        widgetAsset2.setAdapter(adapter);
        widgetAsset3.setAdapter(adapter);

        Asset[] selected = PriceStore.getSelectedAssets(this);
        widgetAsset1.setSelection(widgetChoices.indexOf(selected[0]));
        widgetAsset2.setSelection(widgetChoices.indexOf(selected[1]));
        widgetAsset3.setSelection(widgetChoices.indexOf(selected[2]));
    }

    private void saveWidgetSelection() {
        Asset a1 = widgetChoices.get(widgetAsset1.getSelectedItemPosition());
        Asset a2 = widgetChoices.get(widgetAsset2.getSelectedItemPosition());
        Asset a3 = widgetChoices.get(widgetAsset3.getSelectedItemPosition());

        if (!PriceStore.setSelectedAssets(this, a1, a2, a3)) {
            Toast.makeText(this, "سه مورد متفاوت انتخاب کنید.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArzbanWidgetProvider.updateAll(this);
        Toast.makeText(this, "انتخاب‌های ویجت ذخیره شد.", Toast.LENGTH_SHORT).show();
        refreshOnce();
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
        fillGroup(currencyList, Asset.Group.CURRENCY);
        fillGroup(goldList, Asset.Group.GOLD);
        fillGroup(coinList, Asset.Group.COIN);

        String date = PriceStore.lastMarketDate(this);
        String time = PriceStore.lastMarketTime(this);
        if ((time == null || time.isEmpty()) && PriceStore.lastFetchEpochMs(this) > 0L) {
            time = PriceFormat.localTime(PriceStore.lastFetchEpochMs(this));
        }

        if ((date == null || date.isEmpty()) && (time == null || time.isEmpty())) {
            lastUpdatedText.setText("هنوز نرخ بازار دریافت نشده");
        } else {
            StringBuilder sb = new StringBuilder("آخرین بروزرسانی");
            if (date != null && !date.isEmpty()) sb.append(" • ").append(date);
            if (time != null && !time.isEmpty()) sb.append(" • ").append(time);
            lastUpdatedText.setText(sb.toString());
        }

        boolean auto = PriceStore.isAutoEnabled(this);
        serviceStatus.setText(auto
                ? "● بروزرسانی خودکار هر ۶۰ ثانیه فعال است"
                : "○ بروزرسانی ۶۰ ثانیه‌ای خاموش است");
        serviceStatus.setTextColor(Color.parseColor(auto ? "#5E8C61" : "#777777"));
        startButton.setEnabled(!auto);
        stopButton.setEnabled(auto);

        String err = PriceStore.lastError(this);
        errorText.setVisibility(err.isEmpty() ? View.GONE : View.VISIBLE);
        if (!err.isEmpty()) {
            errorText.setText("اتصال ناموفق؛ آخرین نرخ‌های ذخیره‌شده نمایش داده می‌شوند.");
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
