package com.englishlistening.browser;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final String MODEL = "gemini-3.1-flash-lite";

    private final SecureApiKeyStore keyStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public GeminiClient(SecureApiKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public void ask(String prompt, Callback callback) {
        final String key = keyStore.load();
        if (key == null || key.trim().isEmpty()) {
            main.post(() -> callback.onError("还没有配置免费 Gemini API Key"));
            return;
        }
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("x-goog-api-key", key.trim());

                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("input", prompt == null ? "" : prompt);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

                int code = conn.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String raw = readAll(stream);
                if (code < 200 || code >= 300) {
                    String msg = parseError(raw, code);
                    main.post(() -> callback.onError(msg));
                    return;
                }
                String text = parseText(raw);
                if (text.trim().isEmpty()) text = "AI 已返回，但没有可显示的文字结果。";
                final String result = text;
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String msg = "AI 请求失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                main.post(() -> callback.onError(msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private String parseText(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        String convenience = root.optString("output_text", "");
        if (!convenience.trim().isEmpty()) return convenience.trim();
        StringBuilder out = new StringBuilder();
        JSONArray steps = root.optJSONArray("steps");
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null || !"model_output".equals(step.optString("type"))) continue;
                JSONArray content = step.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part != null && "text".equals(part.optString("type"))) {
                        String text = part.optString("text", "");
                        if (!text.isEmpty()) {
                            if (out.length() > 0) out.append("\n");
                            out.append(text);
                        }
                    }
                }
            }
        }
        return out.toString().trim();
    }

    private String parseError(String raw, int code) {
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (code == 401 || code == 403) return "Gemini Key 无效或没有权限，请在 AI 设置里重新配置。";
            if (code == 429) return "Gemini 免费额度或速率限制已达到，请稍后再试。";
            if (!message.isEmpty()) return "Gemini 返回错误：" + message;
        } catch (Exception ignored) {}
        return "Gemini 请求失败（HTTP " + code + "）";
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
