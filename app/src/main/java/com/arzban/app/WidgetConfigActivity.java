package com.arzban.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int count = 1;
    private Spinner spinner1;
    private Spinner spinner2;
    private Spinner spinner3;
    private LinearLayout row2;
    private LinearLayout row3;
    private List<Asset> choices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(appWidgetId);
        if (info != null && info.provider != null) {
            String cls = info.provider.getClassName();
            if (cls.endsWith("TripleWidgetProvider")) count = 3;
            else if (cls.endsWith("DoubleWidgetProvider")) count = 2;
            else count = 1;
        }

        TextView title = findViewById(R.id.config_title);
        title.setText(count == 1 ? "ویجت تک‌قیمت" : count == 2 ? "ویجت دو‌قیمت" : "ویجت سه‌قیمت");

        spinner1 = findViewById(R.id.config_asset_1);
        spinner2 = findViewById(R.id.config_asset_2);
        spinner3 = findViewById(R.id.config_asset_3);
        row2 = findViewById(R.id.config_row_2);
        row3 = findViewById(R.id.config_row_3);

        row2.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        row3.setVisibility(count >= 3 ? View.VISIBLE : View.GONE);

        choices = new ArrayList<>(Asset.widgetChoices());
        List<String> labels = new ArrayList<>();
        for (Asset asset : choices) labels.add(asset.displayName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(adapter);
        spinner2.setAdapter(adapter);
        spinner3.setAdapter(adapter);

        Asset[] current = WidgetPrefs.assets(this, appWidgetId, count);
        setSelection(spinner1, current[0]);
        if (count >= 2) setSelection(spinner2, current[1]);
        if (count >= 3) setSelection(spinner3, current[2]);

        Button save = findViewById(R.id.config_save_button);
        save.setOnClickListener(v -> saveAndFinish());
    }

    private void setSelection(Spinner spinner, Asset asset) {
        int index = choices.indexOf(asset);
        spinner.setSelection(Math.max(0, index));
    }

    private void saveAndFinish() {
        Asset a1 = choices.get(spinner1.getSelectedItemPosition());
        Asset a2 = count >= 2 ? choices.get(spinner2.getSelectedItemPosition()) : null;
        Asset a3 = count >= 3 ? choices.get(spinner3.getSelectedItemPosition()) : null;

        if ((count >= 2 && a1 == a2) ||
                (count >= 3 && (a1 == a3 || a2 == a3))) {
            Toast.makeText(this, "موارد انتخابی باید متفاوت باشند.", Toast.LENGTH_SHORT).show();
            return;
        }

        Asset[] selected = count == 1
                ? new Asset[]{a1}
                : count == 2 ? new Asset[]{a1, a2} : new Asset[]{a1, a2, a3};

        WidgetPrefs.save(this, appWidgetId, count, selected);
        ArzbanWidgetProvider.updateOne(
                this,
                AppWidgetManager.getInstance(this),
                appWidgetId,
                count
        );

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
