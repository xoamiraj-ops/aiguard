package com.parsban.callai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public final class GatewayClient {
    private final Context context;
    private final OkHttpClient client;

    public GatewayClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.MINUTES)
                .writeTimeout(6, TimeUnit.MINUTES)
                .build();
    }

    public JSONObject health() throws Exception {
        Request request = base("health").get().build();
        return executeJson(request);
    }

    public JSONObject lookup(String phone) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("phone", phone);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json; charset=utf-8"));

        Request request = base("lookup").post(body).build();
        return executeJson(request);
    }

    public JSONObject processCall(CallSession session) throws Exception {
        Uri uri = Uri.parse(session.recordingUri);
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";

        MultipartBody.Builder form = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("phone", session.phone)
                .addFormDataPart("direction", session.direction)
                .addFormDataPart("startedAt", Long.toString(session.createdAt))
                .addFormDataPart("connectedAt", Long.toString(session.connectedAt))
                .addFormDataPart("endedAt", Long.toString(session.endedAt))
                .addFormDataPart("crmType", session.crmType == null ? "" : session.crmType)
                .addFormDataPart("crmId", session.crmId == null ? "" : session.crmId)
                .addFormDataPart("customerName", session.customerName == null ? "" : session.customerName)
                .addFormDataPart(
                        "audio",
                        displayName(uri),
                        new UriBody(context, uri, MediaType.parse(mime)));

        Request request = base("process_call").post(form.build()).build();
        return executeJson(request);
    }

    private Request.Builder base(String action) {
        String raw = AppPrefs.gateway(context);
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalStateException("Gateway تنظیم نشده");
        }

        HttpUrl url = HttpUrl.get(raw).newBuilder()
                .setQueryParameter("action", action)
                .build();

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ParsbanCallAI/0.1");

        String token = SecretStore.deviceToken(context);
        if (token != null && !token.isEmpty()) {
            builder.header("X-Parsban-Token", token);
        }
        return builder;
    }

    private JSONObject executeJson(Request request) throws Exception {
        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Gateway HTTP " + response.code() + ": " + raw);
            }
            JSONObject json = new JSONObject(raw.isEmpty() ? "{}" : raw);
            if (!json.optBoolean("ok", false)) {
                throw new IOException(json.optString("error", "Gateway error"));
            }
            return json;
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        return "call-recording.m4a";
    }

    private static final class UriBody extends RequestBody {
        private final Context context;
        private final Uri uri;
        private final MediaType mediaType;

        UriBody(Context context, Uri uri, MediaType mediaType) {
            this.context = context.getApplicationContext();
            this.uri = uri;
            this.mediaType = mediaType;
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open recording URI");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) sink.write(buffer, 0, read);
                }
            }
        }
    }
}
