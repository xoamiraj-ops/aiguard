package com.parsban.callai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretStore {
    private static final String KEY_ALIAS = "avakav_personal_key";
    private static final String PREFS = "avakav_personal_secrets";
    private static final String OPENAI_KEY = "openai_api_key";

    private SecretStore() {}

    public static void setOpenAiKey(Context context, String value) throws Exception {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(OPENAI_KEY, encrypt(value == null ? "" : value.trim())).apply();
    }

    public static String openAiKey(Context context) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(OPENAI_KEY, "");
            if (raw == null || raw.isEmpty()) return "";
            return decrypt(raw);
        } catch (Exception e) {
            return "";
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] out = new byte[1 + iv.length + encrypted.length];
        out[0] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 1, iv.length);
        System.arraycopy(encrypted, 0, out, 1 + iv.length, encrypted.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static String decrypt(String raw) throws Exception {
        byte[] all = Base64.decode(raw, Base64.NO_WRAP);
        int ivLen = all[0] & 0xFF;
        byte[] iv = new byte[ivLen];
        byte[] encrypted = new byte[all.length - 1 - ivLen];
        System.arraycopy(all, 1, iv, 0, ivLen);
        System.arraycopy(all, 1 + ivLen, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
