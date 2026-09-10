package com.englishlistening.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class AppData {
    private static final String PREFS = "english_listening_prefs";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_DOWNLOADS = "downloads_json";
    private static final String KEY_LAST_URL = "last_learning_url";
    private static final String KEY_LAST_TITLE = "last_learning_title";
    private static final int MAX_HISTORY = 300;
    private static final int MAX_DOWNLOADS = 150;

    private final SharedPreferences prefs;

    public AppData(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONArray history() { return readArray(KEY_HISTORY); }
    public JSONArray favorites() { return readArray(KEY_FAVORITES); }
    public JSONArray downloads() { return readArray(KEY_DOWNLOADS); }

    public void saveHistory(JSONArray value) { saveArray(KEY_HISTORY, value); }
    public void saveFavorites(JSONArray value) { saveArray(KEY_FAVORITES, value); }
    public void saveDownloads(JSONArray value) { saveArray(KEY_DOWNLOADS, value); }

    public void clearHistory() { prefs.edit().remove(KEY_HISTORY).apply(); }
    public void clearDownloadsList() { prefs.edit().remove(KEY_DOWNLOADS).apply(); }

    public void setLastLearning(String url, String title) {
        prefs.edit().putString(KEY_LAST_URL, url == null ? "" : url)
                .putString(KEY_LAST_TITLE, title == null ? "" : title).apply();
    }

    public String lastLearningUrl() { return prefs.getString(KEY_LAST_URL, ""); }
    public String lastLearningTitle() { return prefs.getString(KEY_LAST_TITLE, ""); }

    public void addHistory(String url, String title) {
        try {
            JSONArray old = history();
            JSONArray next = new JSONArray();
            JSONObject current = new JSONObject();
            current.put("url", url);
            current.put("title", safeTitle(title, url));
            current.put("time", System.currentTimeMillis());
            current.put("host", compactHost(url));
            next.put(current);
            for (int i = 0; i < old.length() && next.length() < MAX_HISTORY; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !url.equals(item.optString("url"))) next.put(item);
            }
            saveHistory(next);
        } catch (Exception ignored) {}
    }

    public boolean isFavorite(String url) {
        JSONArray arr = favorites();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && url != null && url.equals(o.optString("url"))) return true;
        }
        return false;
    }

    public void addFavorite(String url, String title, String note) {
        if (url == null || isFavorite(url)) return;
        try {
            JSONArray old = favorites();
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("url", url);
            item.put("title", safeTitle(title, url));
            item.put("note", note == null ? "" : note.trim());
            item.put("time", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            saveFavorites(next);
        } catch (Exception ignored) {}
    }

    public void removeFavorite(int index) {
        JSONArray old = favorites();
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            if (i != index) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
        }
        saveFavorites(next);
    }

    public void addDownload(long id, String name, String url, String mime) {
        try {
            JSONArray old = downloads();
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("name", name == null ? "download" : name);
            item.put("url", url == null ? "" : url);
            item.put("mime", mime == null ? "" : mime);
            item.put("time", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < MAX_DOWNLOADS; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            saveDownloads(next);
        } catch (Exception ignored) {}
    }

    public static boolean isHttp(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static boolean isAiUrl(String url) {
        String host = compactHost(url).toLowerCase(Locale.ROOT);
        return host.endsWith("chatgpt.com") || host.endsWith("openai.com") || host.endsWith("doubao.com");
    }

    public static boolean isAudio(String value) {
        if (value == null) return false;
        String u = value.toLowerCase(Locale.ROOT).split("\\?")[0];
        return u.endsWith(".mp3") || u.endsWith(".m4a") || u.endsWith(".aac") || u.endsWith(".wav") || u.endsWith(".ogg") || u.endsWith(".flac") || u.startsWith("audio/");
    }

    public static String guessMime(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains(".mp3")) return "audio/mpeg";
        if (u.contains(".m4a")) return "audio/mp4";
        if (u.contains(".aac")) return "audio/aac";
        if (u.contains(".wav")) return "audio/wav";
        if (u.contains(".ogg")) return "audio/ogg";
        if (u.contains(".mp4")) return "video/mp4";
        if (u.contains(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    public static String compactHost(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.replaceFirst("^www\\.", "");
        } catch (Exception e) { return ""; }
    }

    public static String safeTitle(String title, String url) {
        return title == null || title.trim().isEmpty() ? (url == null ? "" : url) : title.trim();
    }

    public static String ellipsize(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private JSONArray readArray(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveArray(String key, JSONArray arr) {
        prefs.edit().putString(key, arr == null ? "[]" : arr.toString()).apply();
    }
}
