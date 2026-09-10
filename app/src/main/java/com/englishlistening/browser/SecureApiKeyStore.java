package com.englishlistening.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureApiKeyStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String ALIAS = "EnglishListeningGeminiKey";
    private static final String PREFS = "english_listening_secure";
    private static final String KEY_CIPHER = "gemini_cipher";
    private static final String KEY_IV = "gemini_iv";

    private final Context context;

    public SecureApiKeyStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasKey() {
        String key = load();
        return key != null && !key.trim().isEmpty();
    }

    public void save(String plain) throws Exception {
        if (plain == null || plain.trim().isEmpty()) throw new IllegalArgumentException("empty key");
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP));
        e.putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        e.apply();
    }

    public String load() {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String c = p.getString(KEY_CIPHER, "");
            String iv = p.getString(KEY_IV, "");
            if (c.isEmpty() || iv.isEmpty()) return "";
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(ALIAS, null);
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(c, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public void clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CIPHER).remove(KEY_IV).apply();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {}
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
        ks.load(null);
        SecretKey existing = (SecretKey) ks.getKey(ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
