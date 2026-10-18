package com.parsban.callai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public final class OpenAiClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String BASE = "https://api.openai.com/v1";

    private final Context context;
    private final OkHttpClient http;

    public OpenAiClient(Context context) {
        this.context = context.getApplicationContext();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .callTimeout(12, TimeUnit.MINUTES)
                .build();
    }

    public void testKey() throws Exception {
        String key = requireKey();
        Request request = new Request.Builder()
                .url(BASE + "/models/gpt-transcribe")
                .header("Authorization", "Bearer " + key)
                .get()
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw apiError(response, "کلید API معتبر نیست");
        }
    }

    public Transcription transcribe(Uri uri) throws Exception {
        String key = requireKey();
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) mime = "audio/m4a";
        String fileName = fileName(uri);

        RequestBody file = new UriBody(context, uri, MediaType.parse(mime));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, file)
                .addFormDataPart("model", "gpt-transcribe")
                .addFormDataPart("language", "fa")
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(BASE + "/audio/transcriptions")
                .header("Authorization", "Bearer " + key)
                .post(body)
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw apiError(response, "تبدیل صدا به متن ناموفق بود");
            String raw = response.body() == null ? "" : response.body().string();
            JSONObject json = new JSONObject(raw);
            String text = json.optString("text", "").trim();
            if (text.isEmpty()) throw new IOException("متنی از فایل صوتی استخراج نشد");
            return new Transcription(text, "gpt-transcribe");
        }
    }

    public Analysis analyze(CallSession session, String transcript) throws Exception {
        String key = requireKey();

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        JSONObject props = new JSONObject();
        props.put("title", new JSONObject().put("type", "string"));
        props.put("summary", new JSONObject().put("type", "string"));
        props.put("highlights", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string")));
        props.put("action_items", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string")));
        schema.put("properties", props);
        schema.put("required", new JSONArray()
                .put("title").put("summary").put("highlights").put("action_items"));

        JSONObject format = new JSONObject()
                .put("type", "json_schema")
                .put("name", "avakav_call_analysis")
                .put("strict", true)
                .put("schema", schema);

        String who = session.contactName == null || session.contactName.isEmpty()
                ? (session.phone == null ? "" : session.phone)
                : session.contactName;

        String input = "نام/طرف مکالمه: " + who + "\n" +
                "نوع تماس: " + ("Outgoing".equals(session.direction) ? "خروجی" : "ورودی") + "\n\n" +
                "متن مکالمه:\n" + transcript;

        String instructions =
                "تو موتور تحلیل مکالمه اپ آواکاو هستی. متن مکالمه تلفنی فارسی را دقیق تحلیل کن. " +
                "خلاصه کوتاه و کاربردی بنویس، نکات مهم را بدون تکرار استخراج کن، و فقط کارهایی را در action_items قرار بده " +
                "که در مکالمه صریحاً گفته یا به شکل روشن توافق شده‌اند. هیچ تاریخ، مبلغ، اسم یا تعهدی را حدس نزن. " +
                "اگر نکته یا کاری وجود ندارد آرایه خالی برگردان. خروجی باید فارسی روان باشد.";

        JSONObject body = new JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("store", false)
                .put("instructions", instructions)
                .put("input", input)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format));

        Request request = new Request.Builder()
                .url(BASE + "/responses")
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw apiError(response, "تحلیل مکالمه ناموفق بود");
            String raw = response.body() == null ? "" : response.body().string();
            JSONObject root = new JSONObject(raw);
            String output = collectOutputText(root);
            if (output.isEmpty()) throw new IOException("خروجی تحلیل خالی بود");
            JSONObject data = new JSONObject(output);
            return new Analysis(
                    data.optString("title", ""),
                    data.optString("summary", ""),
                    data.optJSONArray("highlights") == null ? "[]" : data.optJSONArray("highlights").toString(),
                    data.optJSONArray("action_items") == null ? "[]" : data.optJSONArray("action_items").toString(),
                    "gpt-5.6-luna"
            );
        }
    }

    private String requireKey() throws IOException {
        String key = SecretStore.openAiKey(context);
        if (key == null || key.trim().isEmpty()) {
            throw new IOException("کلید OpenAI در تنظیمات وارد نشده است");
        }
        return key.trim();
    }

    private static String collectOutputText(JSONObject root) {
        StringBuilder out = new StringBuilder();
        JSONArray items = root.optJSONArray("output");
        if (items == null) return "";
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !"message".equals(item.optString("type"))) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    out.append(part.optString("text", ""));
                }
            }
        }
        return out.toString().trim();
    }

    private static Exception apiError(Response response, String fallback) {
        try {
            String body = response.body() == null ? "" : response.body().string();
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (!message.isEmpty()) return new IOException(message);
        } catch (Exception ignored) {}
        return new IOException(fallback + " (" + response.code() + ")");
    }

    private String fileName(Uri uri) {
        String s = uri.getLastPathSegment();
        if (s == null || s.trim().isEmpty()) return "call.m4a";
        String clean = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!clean.contains(".")) clean += ".m4a";
        return clean;
    }

    public static final class Transcription {
        public final String text;
        public final String model;
        public Transcription(String text, String model) {
            this.text = text;
            this.model = model;
        }
    }

    public static final class Analysis {
        public final String title;
        public final String summary;
        public final String highlightsJson;
        public final String actionItemsJson;
        public final String model;
        public Analysis(String title, String summary, String highlightsJson, String actionItemsJson, String model) {
            this.title = title;
            this.summary = summary;
            this.highlightsJson = highlightsJson;
            this.actionItemsJson = actionItemsJson;
            this.model = model;
        }
    }

    private static final class UriBody extends RequestBody {
        private final Context context;
        private final Uri uri;
        private final MediaType type;

        UriBody(Context context, Uri uri, MediaType type) {
            this.context = context;
            this.uri = uri;
            this.type = type;
        }

        @Override
        public MediaType contentType() {
            return type;
        }

        @Override
        public long contentLength() {
            try (AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                return afd == null ? -1L : afd.getLength();
            } catch (Exception e) {
                return -1L;
            }
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 Source source = in == null ? null : Okio.source(in)) {
                if (source == null) throw new IOException("فایل ضبط قابل خواندن نیست");
                sink.writeAll(source);
            }
        }
    }
}
