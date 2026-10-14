package com.arzban.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum Asset {
    DOLLAR("dollar", "دلار آمریکا", "USD", Group.CURRENCY, Unit.TOMAN, 0),
    EURO("euro", "یورو", "EUR", Group.CURRENCY, Unit.TOMAN, 0),
    AED("aed", "درهم امارات", "AED", Group.CURRENCY, Unit.TOMAN, 0),
    POUND("pound", "پوند انگلیس", "GBP", Group.CURRENCY, Unit.TOMAN, 0),
    LIRA("lira", "لیر ترکیه", "TRY", Group.CURRENCY, Unit.TOMAN, 0),
    YUAN("yuan", "یوان چین", "CNY", Group.CURRENCY, Unit.TOMAN, 0),
    CAD("cad", "دلار کانادا", "CAD", Group.CURRENCY, Unit.TOMAN, 0),
    AUD("aud", "دلار استرالیا", "AUD", Group.CURRENCY, Unit.TOMAN, 0),
    CHF("chf", "فرانک سوئیس", "CHF", Group.CURRENCY, Unit.TOMAN, 0),
    SAR("sar", "ریال عربستان", "SAR", Group.CURRENCY, Unit.TOMAN, 0),
    QAR("qar", "ریال قطر", "QAR", Group.CURRENCY, Unit.TOMAN, 0),
    KWD("kwd", "دینار کویت", "KWD", Group.CURRENCY, Unit.TOMAN, 0),
    OMR("omr", "ریال عمان", "OMR", Group.CURRENCY, Unit.TOMAN, 0),
    IQD("iqd", "دینار عراق", "IQD", Group.CURRENCY, Unit.TOMAN, 0),
    AFN("afn", "افغانی", "AFN", Group.CURRENCY, Unit.TOMAN, 0),
    RUB("rub", "روبل روسیه", "RUB", Group.CURRENCY, Unit.TOMAN, 0),
    INR("inr", "روپیه هند", "INR", Group.CURRENCY, Unit.TOMAN, 0),
    TETHER("tether", "تتر", "USDT", Group.CURRENCY, Unit.TOMAN, 0),

    GOLD18("gold", "طلای ۱۸ عیار", "18K", Group.GOLD, Unit.TOMAN, 0),
    GOLD24("gold24", "طلای ۲۴ عیار", "24K", Group.GOLD, Unit.TOMAN, 0),
    MESGHAL("mesghal", "مثقال طلا", "مثقال", Group.GOLD, Unit.TOMAN, 0),
    SILVER("silver", "نقره ۹۹۹", "Ag", Group.GOLD, Unit.TOMAN, 0),
    OUNCE("ounce", "اونس جهانی طلا", "XAU", Group.GOLD, Unit.USD, 2),

    COIN_EMAMI("coin", "سکه امامی", "امامی", Group.COIN, Unit.TOMAN, 0),
    COIN_BAHAR("bahar", "سکه بهار آزادی", "بهار", Group.COIN, Unit.TOMAN, 0),
    COIN_HALF("half", "نیم‌سکه", "نیم", Group.COIN, Unit.TOMAN, 0),
    COIN_QUARTER("quarter", "ربع‌سکه", "ربع", Group.COIN, Unit.TOMAN, 0),
    COIN_GRAM("gram", "سکه گرمی", "گرمی", Group.COIN, Unit.TOMAN, 0);

    public enum Group { CURRENCY, GOLD, COIN }
    public enum Unit { TOMAN, USD }

    public final String key;
    public final String titleFa;
    public final String symbol;
    public final Group group;
    public final Unit unit;
    public final int decimals;

    Asset(String key, String titleFa, String symbol, Group group, Unit unit, int decimals) {
        this.key = key;
        this.titleFa = titleFa;
        this.symbol = symbol;
        this.group = group;
        this.unit = unit;
        this.decimals = decimals;
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
        for (Asset asset : values()) if (asset.group == group) result.add(asset);
        return result;
    }

    public static List<Asset> widgetChoices() {
        return Arrays.asList(values());
    }
}
