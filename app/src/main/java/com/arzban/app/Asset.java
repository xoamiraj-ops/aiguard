package com.arzban.app;

import java.util.ArrayList;
import java.util.List;

public enum Asset {
    DOLLAR("dollar", "دلار آمریکا", "USD", Group.CURRENCY, Unit.TOMAN, 0, true),
    EURO("euro", "یورو", "EUR", Group.CURRENCY, Unit.TOMAN, 0, true),
    AED("aed", "درهم امارات", "AED", Group.CURRENCY, Unit.TOMAN, 0, true),
    POUND("pound", "پوند انگلیس", "GBP", Group.CURRENCY, Unit.TOMAN, 0, true),
    LIRA("lira", "لیر ترکیه", "TRY", Group.CURRENCY, Unit.TOMAN, 0, true),
    YUAN("yuan", "یوان چین", "CNY", Group.CURRENCY, Unit.TOMAN, 0, true),
    CAD("cad", "دلار کانادا", "CAD", Group.CURRENCY, Unit.TOMAN, 0, true),
    AUD("aud", "دلار استرالیا", "AUD", Group.CURRENCY, Unit.TOMAN, 0, true),
    MANAT("azn", "منات آذربایجان", "AZN", Group.CURRENCY, Unit.TOMAN, 0, true),
    OMR("omr", "ریال عمان", "OMR", Group.CURRENCY, Unit.TOMAN, 0, true),
    IQD("iqd", "دینار عراق", "IQD", Group.CURRENCY, Unit.TOMAN, 1, true),
    AFN("afn", "افغانی", "AFN", Group.CURRENCY, Unit.TOMAN, 0, true),
    TETHER("tether", "تتر", "USDT", Group.CURRENCY, Unit.TOMAN, 0, true),

    CHF("chf", "فرانک سوئیس", "CHF", Group.CURRENCY, Unit.TOMAN, 0, false),
    SAR("sar", "ریال عربستان", "SAR", Group.CURRENCY, Unit.TOMAN, 0, false),
    QAR("qar", "ریال قطر", "QAR", Group.CURRENCY, Unit.TOMAN, 0, false),
    KWD("kwd", "دینار کویت", "KWD", Group.CURRENCY, Unit.TOMAN, 0, false),
    RUB("rub", "روبل روسیه", "RUB", Group.CURRENCY, Unit.TOMAN, 0, false),
    INR("inr", "روپیه هند", "INR", Group.CURRENCY, Unit.TOMAN, 0, false),

    GOLD18("gold", "طلای ۱۸ عیار", "18K", Group.GOLD, Unit.TOMAN, 0, true),
    MESGHAL("mesghal", "مثقال طلا", "مثقال", Group.GOLD, Unit.TOMAN, 0, true),
    OUNCE("ounce", "اونس جهانی طلا", "XAU", Group.GOLD, Unit.USD, 0, true),
    GOLD24("gold24", "طلای ۲۴ عیار", "24K", Group.GOLD, Unit.TOMAN, 0, false),
    SILVER("silver", "نقره ۹۹۹", "Ag", Group.GOLD, Unit.TOMAN, 0, false),

    COIN_EMAMI("coin", "سکه امامی", "امامی", Group.COIN, Unit.TOMAN, 0, true),
    COIN_BAHAR("bahar", "سکه بهار آزادی", "بهار", Group.COIN, Unit.TOMAN, 0, true),
    COIN_HALF("half", "نیم‌سکه", "نیم", Group.COIN, Unit.TOMAN, 0, true),
    COIN_QUARTER("quarter", "ربع‌سکه", "ربع", Group.COIN, Unit.TOMAN, 0, true),
    COIN_GRAM("gram", "سکه گرمی", "گرمی", Group.COIN, Unit.TOMAN, 0, true);

    public enum Group { CURRENCY, GOLD, COIN }
    public enum Unit { TOMAN, USD }

    public final String key;
    public final String titleFa;
    public final String symbol;
    public final Group group;
    public final Unit unit;
    public final int decimals;
    public final boolean sourceSupported;

    Asset(
            String key,
            String titleFa,
            String symbol,
            Group group,
            Unit unit,
            int decimals,
            boolean sourceSupported
    ) {
        this.key = key;
        this.titleFa = titleFa;
        this.symbol = symbol;
        this.group = group;
        this.unit = unit;
        this.decimals = decimals;
        this.sourceSupported = sourceSupported;
    }

    public String displayName() {
        return titleFa + "  •  " + symbol;
    }

    public String unitLabel() {
        return unit == Unit.USD ? "دلار" : "تومان";
    }

    public static Asset fromKey(String key) {
        if (key != null) {
            for (Asset asset : values()) {
                if (asset.key.equals(key)) return asset;
            }
        }
        return DOLLAR;
    }

    public static List<Asset> inGroup(Group group) {
        List<Asset> result = new ArrayList<>();
        for (Asset asset : values()) {
            if (asset.group == group && asset.sourceSupported) result.add(asset);
        }
        return result;
    }

    public static List<Asset> widgetChoices() {
        List<Asset> result = new ArrayList<>();
        for (Asset asset : values()) {
            if (asset.sourceSupported) result.add(asset);
        }
        return result;
    }
}
