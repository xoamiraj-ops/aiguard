package com.arzban.app;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PriceFormat {
    private PriceFormat() {}

    public static String toman(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    public static String value(Asset asset, double value) {
        if (asset.unit == Asset.Unit.USD) {
            NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
            f.setMinimumFractionDigits(asset.decimals);
            f.setMaximumFractionDigits(asset.decimals);
            return f.format(value);
        }
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value));
    }

    public static String valueWithUnit(AssetQuote quote) {
        if (quote == null) return "—";
        return value(quote.asset, quote.value) + " " + quote.asset.unitLabel();
    }

    public static String change(Double value) {
        if (value == null) return "—";
        if (value > 0) return String.format(Locale.US, "+%.2f%%", value);
        return String.format(Locale.US, "%.2f%%", value);
    }

    public static String localTime(long epochMs) {
        if (epochMs <= 0) return "—";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(epochMs));
    }
}
