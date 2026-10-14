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
