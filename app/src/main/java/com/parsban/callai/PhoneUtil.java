package com.parsban.callai;

public final class PhoneUtil {
    private PhoneUtil() {}

    public static String digits(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) out.append(c);
        }
        return out.toString();
    }

    public static String normalizeIran(String value) {
        String d = digits(value);
        if (d.startsWith("0098")) d = d.substring(4);
        else if (d.startsWith("98")) d = d.substring(2);
        if (d.startsWith("9") && d.length() == 10) d = "0" + d;
        return d;
    }

    public static String lastDigits(String value, int count) {
        String d = digits(value);
        if (d.length() <= count) return d;
        return d.substring(d.length() - count);
    }
}
