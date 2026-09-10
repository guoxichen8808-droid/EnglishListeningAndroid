package com.englishlistening.browser;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiTranscriptionClient {
    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private static final String FILES_START = "https://generativelanguage.googleapis.com/upload/v1beta/files";
    private static final String INTERACTIONS = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final String MODEL = "gemini-3.5-transcribe";

    private final SecureApiKeyStore keyStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public GeminiTranscriptionClient(SecureApiKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public void transcribe(File audioFile, String learningMaterial, Callback callback) {
        final String key = keyStore.load();
        if (key == null || key.trim().isEmpty()) {
            main.post(() -> callback.onError("还没有配置 Gemini API Key"));
            return;
        }
        if (audioFile == null || !audioFile.exists() || audioFile.length() < 1000) {
            main.post(() -> callback.onError("录音文件无效，请重新录音"));
            return;
        }

        executor.execute(() -> {
            String uploadedName = null;
            try {
                UploadResult upload = upload(audioFile, key.trim());
                uploadedName = upload.name;
                String text = requestTranscript(upload.uri, upload.mimeType, learningMaterial, key.trim());
                if (text == null || text.trim().isEmpty()) throw new IllegalStateException("没有识别到清晰语音");
                final String result = text.trim();
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
                final String out = "Gemini 语音识别失败：" + msg;
                main.post(() -> callback.onError(out));
            } finally {
                if (uploadedName != null) deleteRemoteQuietly(uploadedName, key.trim());
                try { audioFile.delete(); } catch (Exception ignored) {}
            }
        });
    }

    private UploadResult upload(File file, String key) throws Exception {
        HttpURLConnection start = null;
        String uploadUrl;
        try {
            start = (HttpURLConnection) new URL(FILES_START).openConnection();
            start.setRequestMethod("POST");
            start.setConnectTimeout(15000);
            start.setReadTimeout(30000);
            start.setDoOutput(true);
            start.setRequestProperty("x-goog-api-key", key);
            start.setRequestProperty("X-Goog-Upload-Protocol", "resumable");
            start.setRequestProperty("X-Goog-Upload-Command", "start");
            start.setRequestProperty("X-Goog-Upload-Header-Content-Length", String.valueOf(file.length()));
            start.setRequestProperty("X-Goog-Upload-Header-Content-Type", "audio/m4a");
            start.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] meta = new JSONObject().put("file", new JSONObject().put("display_name", "EnglishListening voice")).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = start.getOutputStream()) { os.write(meta); }
            int code = start.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException(apiError(readAll(start.getErrorStream()), code));
            uploadUrl = headerIgnoreCase(start, "X-Goog-Upload-URL");
            if (uploadUrl == null || uploadUrl.isEmpty()) throw new IllegalStateException("Google 没有返回上传地址");
        } finally {
            if (start != null) start.disconnect();
        }

        HttpURLConnection up = null;
        try {
            up = (HttpURLConnection) new URL(uploadUrl).openConnection();
            up.setRequestMethod("POST");
            up.setConnectTimeout(15000);
            up.setReadTimeout(60000);
            up.setDoOutput(true);
            up.setFixedLengthStreamingMode(file.length());
            up.setRequestProperty("Content-Length", String.valueOf(file.length()));
            up.setRequestProperty("Content-Type", "audio/m4a");
            up.setRequestProperty("X-Goog-Upload-Offset", "0");
            up.setRequestProperty("X-Goog-Upload-Command", "upload, finalize");
            try (FileInputStream in = new FileInputStream(file); OutputStream os = up.getOutputStream()) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) >= 0) if (n > 0) os.write(buf, 0, n);
            }
            int code = up.getResponseCode();
            String raw = readAll(code >= 200 && code < 300 ? up.getInputStream() : up.getErrorStream());
            if (code < 200 || code >= 300) throw new IllegalStateException(apiError(raw, code));
            JSONObject f = new JSONObject(raw).optJSONObject("file");
            if (f == null) throw new IllegalStateException("上传完成但没有获得文件信息");
            String uri = f.optString("uri", "");
            String name = f.optString("name", "");
            String mime = f.optString("mimeType", "audio/m4a");
            if (uri.isEmpty()) throw new IllegalStateException("上传完成但没有获得音频 URI");
            return new UploadResult(name, uri, mime.isEmpty() ? "audio/m4a" : mime);
        } finally {
            if (up != null) up.disconnect();
        }
    }

    private String requestTranscript(String uri, String mimeType, String material, String key) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(INTERACTIONS).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setRequestProperty("x-goog-api-key", key);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            JSONArray input = new JSONArray();
            input.put(new JSONObject().put("type", "audio").put("uri", uri).put("mime_type", mimeType));
            body.put("input", input);

            JSONObject transcription = new JSONObject();
            transcription.put("language_codes", new JSONArray().put("en-US"));
            JSONArray vocab = buildVocabulary(material);
            if (vocab.length() > 0) transcription.put("custom_vocabulary", vocab);
            transcription.put("mode", new JSONObject().put("type", "verbatim"));
            body.put("generation_config", new JSONObject().put("transcription_config", transcription));

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
            int code = conn.getResponseCode();
            String raw = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new IllegalStateException(apiError(raw, code));
            JSONObject root = new JSONObject(raw);
            String text = root.optString("output_text", "");
            if (!text.trim().isEmpty()) return text.trim();
            JSONArray outputs = root.optJSONArray("outputs");
            if (outputs != null) {
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < outputs.length(); i++) {
                    JSONObject o = outputs.optJSONObject(i);
                    if (o == null) continue;
                    String t = o.optString("text", "");
                    if (!t.isEmpty()) {
                        if (out.length() > 0) out.append('\n');
                        out.append(t);
                    }
                }
                if (out.length() > 0) return out.toString();
            }
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JSONArray buildVocabulary(String material) {
        JSONArray out = new JSONArray();
        if (material == null || material.trim().isEmpty()) return out;
        Set<String> words = new LinkedHashSet<>();
        Matcher m = Pattern.compile("[A-Za-z][A-Za-z'-]{4,}").matcher(material);
        while (m.find() && words.size() < 80) {
            String w = m.group();
            String lower = w.toLowerCase(Locale.ROOT);
            if (COMMON.contains(lower)) continue;
            words.add(w);
        }
        for (String w : words) out.put(w);
        return out;
    }

    private void deleteRemoteQuietly(String name, String key) {
        if (name == null || name.isEmpty()) return;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/" + name).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("x-goog-api-key", key);
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String headerIgnoreCase(HttpURLConnection conn, String target) {
        String direct = conn.getHeaderField(target);
        if (direct != null) return direct.trim();
        for (Map.Entry<String, java.util.List<String>> e : conn.getHeaderFields().entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(target) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0).trim();
            }
        }
        return null;
    }

    private String apiError(String raw, int code) {
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (code == 401 || code == 403) return "Gemini Key 无效或没有语音转写权限";
            if (code == 429) return "Gemini 免费额度或速率限制已达到，请稍后再试";
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {}
        return "HTTP " + code;
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

    public void shutdown() {
        executor.shutdownNow();
    }

    private static class UploadResult {
        final String name, uri, mimeType;
        UploadResult(String name, String uri, String mimeType) {
            this.name = name;
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }

    private static final Set<String> COMMON = new java.util.HashSet<String>() {{
        String[] a = {"about","after","again","because","before","being","between","could","every","first","friend","friends","going","great","hello","little","maybe","other","people","really","right","should","something","their","there","these","thing","things","think","those","through","today","under","where","which","while","would","your","youre"};
        for (String s : a) add(s);
    }};
}
