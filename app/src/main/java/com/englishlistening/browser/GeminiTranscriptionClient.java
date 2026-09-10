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
    private static final String TRANSCRIBE_MODEL = "gemini-3.5-transcribe";
    private static final String FALLBACK_MODEL = "gemini-2.5-flash";

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

                String text = null;
                Exception primaryError = null;

                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        text = requestSpecializedTranscript(upload.uri, upload.mimeType, learningMaterial, key.trim());
                        if (text != null && !text.trim().isEmpty()) break;
                    } catch (ApiException e) {
                        primaryError = e;
                        if (e.code == 401 || e.code == 403) throw e;
                        if (!e.isTemporary()) break;
                        try { Thread.sleep(attempt == 0 ? 700 : 1500); } catch (InterruptedException ignored) {}
                    } catch (Exception e) {
                        primaryError = e;
                        break;
                    }
                }

                if (text == null || text.trim().isEmpty()) {
                    try {
                        text = requestFallbackTranscript(upload.uri, upload.mimeType, learningMaterial, key.trim());
                    } catch (Exception fallbackError) {
                        String p = primaryError == null ? "专用转写暂时不可用" : safeMessage(primaryError);
                        String f = safeMessage(fallbackError);
                        throw new IllegalStateException("专用转写和备用识别都暂时不可用。\n专用模型：" + p + "\n备用模型：" + f);
                    }
                }

                if (text == null || text.trim().isEmpty()) throw new IllegalStateException("没有识别到清晰语音");
                final String result = cleanupTranscript(text);
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String msg = safeMessage(e);
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
            if (code < 200 || code >= 300) throw new ApiException(code, apiError(readAll(start.getErrorStream()), code));
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
            if (code < 200 || code >= 300) throw new ApiException(code, apiError(raw, code));
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

    private String requestSpecializedTranscript(String uri, String mimeType, String material, String key) throws Exception {
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
            body.put("model", TRANSCRIBE_MODEL);
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
            if (code < 200 || code >= 300) throw new ApiException(code, apiError(raw, code));
            return parseInteractionText(raw);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String requestFallbackTranscript(String uri, String mimeType, String material, String key) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + FALLBACK_MODEL + ":generateContent";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setRequestProperty("x-goog-api-key", key);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            String vocabHint = vocabularyHint(material);
            String prompt = "Transcribe the English speech in this audio exactly. Return only the transcript, no explanation, no markdown. Preserve natural contractions. Do not invent missing words.";
            if (!vocabHint.isEmpty()) prompt += "\nLikely vocabulary from the learner's material: " + vocabHint;

            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            parts.put(new JSONObject().put("fileData", new JSONObject().put("fileUri", uri).put("mimeType", mimeType)));
            JSONObject content = new JSONObject().put("role", "user").put("parts", parts);
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
            body.put("generationConfig", new JSONObject().put("temperature", 0).put("maxOutputTokens", 2048));

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
            int code = conn.getResponseCode();
            String raw = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new ApiException(code, apiError(raw, code));
            return parseGenerateContentText(raw);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String parseInteractionText(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        String text = root.optString("output_text", "");
        if (!text.trim().isEmpty()) return text.trim();

        JSONArray steps = root.optJSONArray("steps");
        if (steps != null) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) continue;
                JSONArray content = step.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject p = content.optJSONObject(j);
                    if (p == null) continue;
                    String t = p.optString("text", "");
                    if (!t.isEmpty()) {
                        if (out.length() > 0) out.append('\n');
                        out.append(t);
                    }
                }
            }
            if (out.length() > 0) return out.toString().trim();
        }

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
            return out.toString().trim();
        }
        return "";
    }

    private String parseGenerateContentText(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return "";
        JSONObject candidate = candidates.optJSONObject(0);
        if (candidate == null) return "";
        JSONObject content = candidate.optJSONObject("content");
        if (content == null) return "";
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p == null) continue;
            String t = p.optString("text", "");
            if (!t.isEmpty()) {
                if (out.length() > 0) out.append('\n');
                out.append(t);
            }
        }
        return out.toString().trim();
    }

    private JSONArray buildVocabulary(String material) {
        JSONArray out = new JSONArray();
        for (String w : vocabularySet(material)) out.put(w);
        return out;
    }

    private String vocabularyHint(String material) {
        StringBuilder sb = new StringBuilder();
        for (String w : vocabularySet(material)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(w);
            if (sb.length() > 500) break;
        }
        return sb.toString();
    }

    private Set<String> vocabularySet(String material) {
        Set<String> words = new LinkedHashSet<>();
        if (material == null || material.trim().isEmpty()) return words;
        Matcher m = Pattern.compile("[A-Za-z][A-Za-z'-]{4,}").matcher(material);
        while (m.find() && words.size() < 80) {
            String w = m.group();
            String lower = w.toLowerCase(Locale.ROOT);
            if (COMMON.contains(lower)) continue;
            words.add(w);
        }
        return words;
    }

    private String cleanupTranscript(String text) {
        if (text == null) return "";
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[A-Za-z]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("transcript:")) s = s.substring(11).trim();
        else if (lower.startsWith("transcription:")) s = s.substring(14).trim();
        return s;
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
            if (code == 401 || code == 403) return "Gemini Key 无效或没有语音识别权限";
            if (code == 429) return "免费额度或请求频率限制，请稍后再试";
            if (code == 503) return message.isEmpty() ? "服务繁忙" : message;
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {}
        return "HTTP " + code;
    }

    private String safeMessage(Exception e) {
        if (e == null) return "未知错误";
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
        return msg;
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

    private static class ApiException extends Exception {
        final int code;
        ApiException(int code, String message) { super(message); this.code = code; }
        boolean isTemporary() { return code == 408 || code == 409 || code == 429 || code >= 500; }
    }

    private static final Set<String> COMMON = new java.util.HashSet<String>() {{
        String[] a = {"about","after","again","because","before","being","between","could","every","first","friend","friends","going","great","hello","little","maybe","other","people","really","right","should","something","their","there","these","thing","things","think","those","through","today","under","where","which","while","would","your","youre"};
        for (String s : a) add(s);
    }};
}
