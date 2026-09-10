package com.englishlistening.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class AppData {
    private static final String PREFS = "english_listening_prefs";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_DOWNLOADS = "downloads_json";
    private static final String KEY_MATERIALS = "materials_json";
    private static final String KEY_SHORTCUTS = "shortcuts_json";
    private static final String KEY_STATS = "study_stats_json";
    private static final String KEY_LAST_URL = "last_learning_url";
    private static final String KEY_LAST_TITLE = "last_learning_title";
    private static final String KEY_GOAL = "daily_goal_minutes";
    private static final String KEY_AUTO_RESUME = "auto_resume";
    private static final int MAX_HISTORY = 500;
    private static final int MAX_DOWNLOADS = 200;
    private static final int MAX_MATERIALS = 150;
    private static final int MAX_SHORTCUTS = 12;

    private final SharedPreferences prefs;

    public AppData(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONArray history() { return readArray(KEY_HISTORY); }
    public JSONArray favorites() { return readArray(KEY_FAVORITES); }
    public JSONArray downloads() { return readArray(KEY_DOWNLOADS); }
    public JSONArray materials() { return readArray(KEY_MATERIALS); }
    public JSONArray shortcuts() { return readArray(KEY_SHORTCUTS); }
    public JSONObject stats() { return normalizedStats(); }

    public void saveHistory(JSONArray value) { saveArray(KEY_HISTORY, value); }
    public void saveFavorites(JSONArray value) { saveArray(KEY_FAVORITES, value); }
    public void saveDownloads(JSONArray value) { saveArray(KEY_DOWNLOADS, value); }
    public void saveMaterials(JSONArray value) { saveArray(KEY_MATERIALS, value); }
    public void saveShortcuts(JSONArray value) { saveArray(KEY_SHORTCUTS, value); }

    public void clearHistory() { prefs.edit().remove(KEY_HISTORY).apply(); }
    public void clearDownloadsList() { prefs.edit().remove(KEY_DOWNLOADS).apply(); }
    public void clearMaterials() { prefs.edit().remove(KEY_MATERIALS).apply(); }

    public void setLastLearning(String url, String title) {
        prefs.edit().putString(KEY_LAST_URL, url == null ? "" : url)
                .putString(KEY_LAST_TITLE, title == null ? "" : title).apply();
    }

    public String lastLearningUrl() { return prefs.getString(KEY_LAST_URL, ""); }
    public String lastLearningTitle() { return prefs.getString(KEY_LAST_TITLE, ""); }

    public int dailyGoalMinutes() { return Math.max(5, Math.min(180, prefs.getInt(KEY_GOAL, 20))); }
    public void setDailyGoalMinutes(int minutes) { prefs.edit().putInt(KEY_GOAL, Math.max(5, Math.min(180, minutes))).apply(); }
    public boolean autoResume() { return prefs.getBoolean(KEY_AUTO_RESUME, false); }
    public void setAutoResume(boolean enabled) { prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply(); }

    public void addHistory(String url, String title) {
        if (!isHttp(url)) return;
        try {
            JSONArray old = history();
            JSONArray next = new JSONArray();
            JSONObject current = new JSONObject();
            current.put("url", url);
            current.put("title", safeTitle(title, url));
            current.put("time", System.currentTimeMillis());
            current.put("host", compactHost(url));
            current.put("visits", 1);
            next.put(current);
            for (int i = 0; i < old.length() && next.length() < MAX_HISTORY; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                if (url.equals(item.optString("url"))) current.put("visits", item.optInt("visits", 1) + 1);
                else next.put(item);
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

    public void removeFavorite(int index) { saveFavorites(without(favorites(), index)); }

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

    public void addMaterial(String title, String text, String sourceUrl, String note) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONArray old = materials();
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("title", safeTitle(title, "学习素材"));
            item.put("text", text.trim().length() > 12000 ? text.trim().substring(0, 12000) : text.trim());
            item.put("url", sourceUrl == null ? "" : sourceUrl);
            item.put("note", note == null ? "" : note.trim());
            item.put("time", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < MAX_MATERIALS; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            saveMaterials(next);
        } catch (Exception ignored) {}
    }

    public void removeMaterial(int index) { saveMaterials(without(materials(), index)); }

    public boolean hasShortcut(String url) {
        JSONArray arr = shortcuts();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && url != null && url.equals(o.optString("url"))) return true;
        }
        return false;
    }

    public boolean addShortcut(String name, String url) {
        if (!isHttp(url) || hasShortcut(url)) return false;
        JSONArray old = shortcuts();
        if (old.length() >= MAX_SHORTCUTS) return false;
        try {
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("name", safeTitle(name, compactHost(url)));
            item.put("url", url);
            item.put("time", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            saveShortcuts(next);
            return true;
        } catch (Exception e) { return false; }
    }

    public void removeShortcut(int index) { saveShortcuts(without(shortcuts(), index)); }

    public void addStudySeconds(long seconds) {
        if (seconds < 5) return;
        updateStats(seconds, 0);
    }

    public void markPractice() { updateStats(0, 1); }

    private void updateStats(long seconds, int practices) {
        try {
            JSONObject s = normalizedStats();
            String today = LocalDate.now().toString();
            String day = s.optString("day", today);
            if (!today.equals(day)) {
                s.put("day", today);
                s.put("todaySeconds", 0L);
                s.put("todayPractices", 0);
            }
            if (seconds > 0 || practices > 0) {
                String last = s.optString("lastStudyDay", "");
                if (!today.equals(last)) {
                    int streak;
                    if (last.isEmpty()) streak = 1;
                    else {
                        try {
                            long gap = ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today));
                            streak = gap == 1 ? Math.max(1, s.optInt("streak", 0) + 1) : 1;
                        } catch (Exception e) { streak = 1; }
                    }
                    s.put("streak", streak);
                    s.put("lastStudyDay", today);
                }
            }
            s.put("todaySeconds", s.optLong("todaySeconds", 0L) + seconds);
            s.put("todayPractices", s.optInt("todayPractices", 0) + practices);
            s.put("totalSeconds", s.optLong("totalSeconds", 0L) + seconds);
            s.put("totalPractices", s.optInt("totalPractices", 0) + practices);
            prefs.edit().putString(KEY_STATS, s.toString()).apply();
        } catch (Exception ignored) {}
    }

    private JSONObject normalizedStats() {
        JSONObject s;
        try { s = new JSONObject(prefs.getString(KEY_STATS, "{}")); }
        catch (Exception e) { s = new JSONObject(); }
        String today = LocalDate.now().toString();
        if (!today.equals(s.optString("day", today))) {
            try {
                s.put("day", today);
                s.put("todaySeconds", 0L);
                s.put("todayPractices", 0);
            } catch (Exception ignored) {}
        }
        return s;
    }

    public String exportAll() {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "EnglishListeningUltimateBackup");
            root.put("version", 1);
            root.put("history", history());
            root.put("favorites", favorites());
            root.put("downloads", downloads());
            root.put("materials", materials());
            root.put("shortcuts", shortcuts());
            root.put("stats", stats());
            root.put("lastUrl", lastLearningUrl());
            root.put("lastTitle", lastLearningTitle());
            root.put("dailyGoal", dailyGoalMinutes());
            root.put("autoResume", autoResume());
            root.put("exportedAt", System.currentTimeMillis());
            return root.toString(2);
        } catch (Exception e) { return "{}"; }
    }

    public boolean importAll(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"EnglishListeningUltimateBackup".equals(root.optString("format"))) return false;
            SharedPreferences.Editor e = prefs.edit();
            e.putString(KEY_HISTORY, root.optJSONArray("history") == null ? "[]" : root.optJSONArray("history").toString());
            e.putString(KEY_FAVORITES, root.optJSONArray("favorites") == null ? "[]" : root.optJSONArray("favorites").toString());
            e.putString(KEY_DOWNLOADS, root.optJSONArray("downloads") == null ? "[]" : root.optJSONArray("downloads").toString());
            e.putString(KEY_MATERIALS, root.optJSONArray("materials") == null ? "[]" : root.optJSONArray("materials").toString());
            e.putString(KEY_SHORTCUTS, root.optJSONArray("shortcuts") == null ? "[]" : root.optJSONArray("shortcuts").toString());
            e.putString(KEY_STATS, root.optJSONObject("stats") == null ? "{}" : root.optJSONObject("stats").toString());
            e.putString(KEY_LAST_URL, root.optString("lastUrl", ""));
            e.putString(KEY_LAST_TITLE, root.optString("lastTitle", ""));
            e.putInt(KEY_GOAL, Math.max(5, Math.min(180, root.optInt("dailyGoal", 20))));
            e.putBoolean(KEY_AUTO_RESUME, root.optBoolean("autoResume", false));
            e.apply();
            return true;
        } catch (Exception e) { return false; }
    }

    private JSONArray without(JSONArray old, int index) {
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            if (i != index) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
        }
        return next;
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
        if (u.contains(".flac")) return "audio/flac";
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

    public static String safeTitle(String title, String fallback) {
        return title == null || title.trim().isEmpty() ? (fallback == null ? "" : fallback) : title.trim();
    }

    public static String ellipsize(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    public static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private JSONArray readArray(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveArray(String key, JSONArray arr) {
        prefs.edit().putString(key, arr == null ? "[]" : arr.toString()).apply();
    }
}
