package com.arzban.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PriceRepository {
    public static final String CHANNEL_URL = "https://t.me/s/DO_L4";

    private static final Pattern FIRST_NUMBER =
            Pattern.compile("([0-9][0-9,٬]*(?:\\.[0-9]+)?)");
    private static final Pattern PERCENT =
            Pattern.compile("%\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([+-])");
    private static final Pattern BUY =
            Pattern.compile("([0-9][0-9,٬]*)\\s*خ(?:ـ)?رید");
    private static final Pattern SELL =
            Pattern.compile("([0-9][0-9,٬]*)\\s*فروش");
    private static final Pattern TRADE =
            Pattern.compile("([0-9][0-9,٬]*)\\s*معامله");

    private PriceRepository() {}

    public static TelegramMarketResult fetchTelegramMarket() throws Exception {
        Document doc = Jsoup.connect(CHANNEL_URL)
                .userAgent("Mozilla/5.0 (Linux; Android 16) Arzban/1.4")
                .referrer("https://t.me/")
                .timeout(15_000)
                .maxBodySize(2_500_000)
                .get();

        Elements messages = doc.select("div.tgme_widget_message");
        if (messages.isEmpty()) {
            throw new IllegalStateException("No Telegram channel messages found");
        }

        Post full = null;
        Post dollar = null;

        for (Element message : messages) {
            Element textNode = message.selectFirst(".tgme_widget_message_text");
            if (textNode == null) continue;

            String text = normalize(textNode.wholeText());
            if (text.isEmpty()) continue;

            long id = parseMessageId(message.attr("data-post"));
            long epochMs = parseMessageEpoch(message);

            if (isFullTable(text) && (full == null || id >= full.id)) {
                full = new Post(id, text, epochMs);
            }

            if (isDollarFuture(text) && (dollar == null || id >= dollar.id)) {
                dollar = new Post(id, text, epochMs);
            }
        }

        if (full == null && dollar == null) {
            throw new IllegalStateException("No supported price post found in @DO_L4");
        }

        Map<Asset, AssetQuote> quotes = new EnumMap<>(Asset.class);
        long newestEpoch = 0L;
        String displayTime = "";

        if (full != null) {
            parseFullTable(full, quotes);
            newestEpoch = Math.max(newestEpoch, full.epochMs);
            displayTime = formatTime(full.epochMs);
        }

        if (dollar != null) {
            AssetQuote oldDollar = quotes.get(Asset.DOLLAR);
            Double dailyChange = oldDollar == null ? null : oldDollar.dailyChangePercent;

            Double buy = parseAmount(BUY, dollar.text);
            Double sell = parseAmount(SELL, dollar.text);
            Double trade = parseAmount(TRADE, dollar.text);

            double current = trade != null
                    ? trade
                    : (buy != null && sell != null ? (buy + sell) / 2.0
                    : buy != null ? buy
                    : sell != null ? sell : 0.0);

            if (current > 0) {
                quotes.put(
                        Asset.DOLLAR,
                        new AssetQuote(
                                Asset.DOLLAR,
                                current,
                                dailyChange,
                                "",
                                formatTime(dollar.epochMs),
                                dollar.epochMs
                        )
                );
                if (dollar.epochMs >= newestEpoch) {
                    newestEpoch = dollar.epochMs;
                    displayTime = formatTime(dollar.epochMs);
                }
            }
        }

        if (quotes.isEmpty()) {
            throw new IllegalStateException("DO_L4 messages were found but no prices could be parsed");
        }

        if (newestEpoch <= 0L) newestEpoch = System.currentTimeMillis();

        MarketSnapshot snapshot = new MarketSnapshot(
                quotes,
                "",
                displayTime,
                newestEpoch
        );

        return new TelegramMarketResult(
                snapshot,
                full == null ? 0L : full.id,
                full == null ? "" : sha256(full.text),
                dollar == null ? 0L : dollar.id,
                dollar == null ? "" : sha256(dollar.text),
                newestEpoch
        );
    }

    // Kept for compatibility with older internal code paths.
    public static MarketSnapshot fetchMarket() throws Exception {
        return fetchTelegramMarket().snapshot;
    }

    private static boolean isFullTable(String text) {
        return text.contains("نرخ فروش")
                && text.contains("دلار:")
                && text.contains("سکه")
                && text.contains("طلا");
    }

    private static boolean isDollarFuture(String text) {
        return text.contains("دلار فردایی تهران")
                && text.contains("خرید")
                && text.contains("فروش");
    }

    private static void parseFullTable(Post post, Map<Asset, AssetQuote> quotes) {
        for (String raw : post.text.split("\\n")) {
            String line = normalize(raw);
            if (line.isEmpty()) continue;

            parseLine(post, quotes, line, Asset.DOLLAR, false, "دلار:");
            parseLine(post, quotes, line, Asset.EURO, false, "یورو:");
            parseLine(post, quotes, line, Asset.POUND, false, "پوند:");
            parseLine(post, quotes, line, Asset.CAD, false, "دلار کانادا:");
            parseLine(post, quotes, line, Asset.AUD, false, "دلار استرالیا:");
            parseLine(post, quotes, line, Asset.YUAN, false, "یوان:");
            parseLine(post, quotes, line, Asset.AED, false, "درهم:");
            parseLine(post, quotes, line, Asset.LIRA, false, "لیر:");
            parseLine(post, quotes, line, Asset.IQD, false, "دینار عراق:");
            parseLine(post, quotes, line, Asset.MANAT, false, "منات:");
            parseLine(post, quotes, line, Asset.AFN, false, "افغانی:");
            parseLine(post, quotes, line, Asset.OMR, false, "ریال عمان:");
            parseLine(post, quotes, line, Asset.TETHER, false, "تتر:");

            parseLine(post, quotes, line, Asset.COIN_EMAMI, true, "سکه جدید:");
            parseLine(post, quotes, line, Asset.COIN_BAHAR, true, "سکه قدیم:");
            parseLine(post, quotes, line, Asset.COIN_HALF, true, "سکه نیم:");
            parseLine(post, quotes, line, Asset.COIN_QUARTER, true, "سکه ربع:");
            parseLine(post, quotes, line, Asset.COIN_GRAM, true, "سکه گرمی:");
            parseLine(post, quotes, line, Asset.MESGHAL, true, "مثقال طلا");
            parseLine(post, quotes, line, Asset.GOLD18, true, "گرم 18 عیار", "گرم ۱۸ عیار");
            parseLine(post, quotes, line, Asset.OUNCE, false, "اونس:");
        }
    }

    private static void parseLine(
            Post post,
            Map<Asset, AssetQuote> quotes,
            String line,
            Asset asset,
            boolean thousands,
            String... labels
    ) {
        boolean matched = false;
        for (String label : labels) {
            if (line.contains(label)) {
                matched = true;
                break;
            }
        }
        if (!matched) return;

        int colon = line.indexOf(':');
        String valuePart = colon >= 0 ? line.substring(colon + 1) : line;

        Matcher number = FIRST_NUMBER.matcher(valuePart);
        if (!number.find()) return;

        double value;
        try {
            value = Double.parseDouble(number.group(1).replace(",", "").replace("٬", ""));
        } catch (NumberFormatException e) {
            return;
        }

        if (thousands && value < 1_000_000d) value *= 1000d;

        Double change = parsePercent(line);

        quotes.put(
                asset,
                new AssetQuote(
                        asset,
                        value,
                        change,
                        "",
                        formatTime(post.epochMs),
                        post.epochMs
                )
        );
    }

    private static Double parsePercent(String line) {
        if (line.contains("بدون تغییر")) return 0.0;

        Matcher m = PERCENT.matcher(line);
        if (!m.find()) return null;

        try {
            double value = Double.parseDouble(m.group(1));
            if ("-".equals(m.group(2))) value = -value;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseAmount(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group(1).replace(",", "").replace("٬", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseMessageId(String dataPost) {
        if (dataPost == null) return 0L;
        int slash = dataPost.lastIndexOf('/');
        if (slash < 0 || slash == dataPost.length() - 1) return 0L;
        try {
            return Long.parseLong(dataPost.substring(slash + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long parseMessageEpoch(Element message) {
        Element time = message.selectFirst("time[datetime]");
        if (time == null) return System.currentTimeMillis();

        String datetime = time.attr("datetime");
        if (datetime == null || datetime.isEmpty()) return System.currentTimeMillis();

        try {
            return OffsetDateTime.parse(datetime).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String formatTime(long epochMs) {
        if (epochMs <= 0L) return "";
        return java.time.Instant.ofEpochMilli(epochMs)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
    }

    private static String normalize(String input) {
        if (input == null) return "";

        String s = input
                .replace('\u200c', ' ')
                .replace('\u200e', ' ')
                .replace('\u200f', ' ')
                .replace('\ufeff', ' ')
                .replace("ي", "ی")
                .replace("ك", "ک")
                .replace("ـ", "");

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            if (c >= '\u2066' && c <= '\u2069') continue;
            out.append(c);
        }

        return out.toString()
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static final class Post {
        final long id;
        final String text;
        final long epochMs;

        Post(long id, String text, long epochMs) {
            this.id = id;
            this.text = text;
            this.epochMs = epochMs;
        }
    }
}
