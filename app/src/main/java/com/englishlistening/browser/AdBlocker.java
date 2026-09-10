package com.englishlistening.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdBlocker {
    public interface Callback { void onDone(String message); }

    private static final String PREFS = "adblock_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String RULE_FILE = "adblock_domains_safe_v2.txt";
    private static final String[] SOURCES = {
            "https://easylist.to/easylist/easylist.txt"
    };

    private final Context context;
    private final SharedPreferences prefs;
    private final Set<String> domains = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AdBlocker(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadBuiltIns();
        loadCachedRules();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public int ruleCount() {
        return domains.size();
    }

    public WebResourceResponse intercept(WebResourceRequest request) {
        if (!isEnabled() || request == null || request.getUrl() == null) return null;
        if (request.isForMainFrame()) return null;

        String url = request.getUrl().toString();
        if (mustAllow(request, url)) return null;
        if (!shouldBlock(url)) return null;

        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private boolean mustAllow(WebResourceRequest request, String url) {
        String lower = url.toLowerCase(Locale.ROOT);

        if (looksLikeMedia(lower)) return true;

        Map<String, String> headers = request.getRequestHeaders();
        if (headers != null) {
            String accept = headers.get("Accept");
            if (accept != null) {
                String a = accept.toLowerCase(Locale.ROOT);
                if (a.contains("video/") || a.contains("audio/") ||
                        a.contains("application/vnd.apple.mpegurl") ||
                        a.contains("application/x-mpegurl") ||
                        a.contains("application/dash+xml")) return true;
            }
            String ref = headers.get("Referer");
            if (ref == null) ref = headers.get("referer");
            if (isLearningSite(ref)) return true;
        }

        return isLearningSite(url) || isProtectedMediaHost(url);
    }

    private boolean looksLikeMedia(String u) {
        return u.matches(".*\\.(mp4|webm|m4v|mov|mp3|m4a|aac|wav|ogg|flac|m3u8|mpd|ts|m4s)(\\?|#|$).*")
                || u.contains("/videoplayback")
                || u.contains("/manifest")
                || u.contains("/hls/")
                || u.contains("/dash/")
                || u.contains("/stream/")
                || u.contains("/media/");
    }

    private boolean isLearningSite(String url) {
        String host = host(url);
        if (host.isEmpty()) return false;
        return ends(host, "elllo.org")
                || ends(host, "bbc.co.uk")
                || ends(host, "bbc.com")
                || ends(host, "bbci.co.uk")
                || ends(host, "voanews.com")
                || ends(host, "youglish.com");
    }

    private boolean isProtectedMediaHost(String url) {
        String host = host(url);
        if (host.isEmpty()) return false;
        return ends(host, "googlevideo.com")
                || ends(host, "youtube.com")
                || ends(host, "youtu.be")
                || ends(host, "ytimg.com")
                || ends(host, "vimeo.com")
                || ends(host, "vimeocdn.com")
                || ends(host, "akamaized.net")
                || ends(host, "cloudfront.net")
                || ends(host, "jwpcdn.com");
    }

    private String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean ends(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public boolean shouldBlock(String url) {
        if (!isEnabled() || url == null || url.isEmpty()) return false;
        String host = host(url);
        if (host.isEmpty()) return false;

        if (domains.contains(host)) return true;
        int dot = host.indexOf('.');
        while (dot > 0 && dot < host.length() - 1) {
            host = host.substring(dot + 1);
            if (domains.contains(host)) return true;
            dot = host.indexOf('.');
        }
        return false;
    }

    public void injectCosmeticRules(WebView webView) {
        if (!isEnabled() || webView == null) return;
        String js = "(function(){try{" +
                "var s=document.getElementById('__el_adblock_css');" +
                "if(!s){s=document.createElement('style');s.id='__el_adblock_css';" +
                "s.textContent='.adsbygoogle,[id^=google_ads],[id*=\\\"google_ads\\\"],iframe[src*=\\\"doubleclick\\\"],iframe[src*=\\\"googlesyndication\\\"],.ad-container,.advertisement,.ad-banner,.ad-slot,[data-ad-slot],[data-ad-client]{display:none!important;visibility:hidden!important;max-height:0!important;overflow:hidden!important}';document.documentElement.appendChild(s);}" +
                "}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    public void updateRules(Callback callback) {
        executor.execute(() -> {
            HashSet<String> downloaded = new HashSet<>();
            int sourceOk = 0;

            for (String source : SOURCES) {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(source).openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(25000);
                    conn.setRequestProperty("User-Agent", "EnglishListening/1.3");
                    int code = conn.getResponseCode();
                    if (code >= 200 && code < 300) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) parseSafeRule(line, downloaded);
                        }
                        sourceOk++;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            if (!downloaded.isEmpty()) {
                synchronized (domains) {
                    domains.clear();
                    loadBuiltIns();
                    domains.addAll(downloaded);
                }
                saveRules(downloaded);
            }

            final int ok = sourceOk;
            final int count = domains.size();
            main.post(() -> {
                if (callback != null) {
                    callback.onDone(ok > 0
                            ? "兼容模式广告规则已更新，共约 " + count + " 条"
                            : "广告规则更新失败，继续使用本地兼容规则");
                }
            });
        });
    }

    private void parseSafeRule(String raw, Set<String> out) {
        if (raw == null) return;
        String line = raw.trim().toLowerCase(Locale.ROOT);

        if (!line.startsWith("||")) return;
        if (line.startsWith("@@") || line.contains("$") || line.contains("*") || line.contains("/")) return;

        int end = line.indexOf('^', 2);
        if (end < 0) return;

        String domain = line.substring(2, end).trim();
        if (!domain.matches("^[a-z0-9._-]+\\.[a-z]{2,}$")) return;

        if (isInfrastructureDomain(domain)) return;
        out.add(domain);
    }

    private boolean isInfrastructureDomain(String d) {
        return ends(d, "googlevideo.com")
                || ends(d, "youtube.com")
                || ends(d, "ytimg.com")
                || ends(d, "vimeo.com")
                || ends(d, "vimeocdn.com")
                || ends(d, "cloudfront.net")
                || ends(d, "akamaized.net")
                || ends(d, "bbc.co.uk")
                || ends(d, "bbci.co.uk")
                || ends(d, "elllo.org")
                || ends(d, "voanews.com")
                || ends(d, "youglish.com");
    }

    private void loadBuiltIns() {
        String[] built = {
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "adservice.google.com",
                "amazon-adsystem.com",
                "adsrvr.org",
                "taboola.com",
                "outbrain.com",
                "criteo.com",
                "criteo.net",
                "adnxs.com",
                "pubmatic.com",
                "rubiconproject.com",
                "openx.net",
                "yieldmo.com",
                "adform.net",
                "smartadserver.com"
        };
        Collections.addAll(domains, built);
    }

    private void loadCachedRules() {
        File file = new File(context.getFilesDir(), RULE_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String d = line.trim().toLowerCase(Locale.ROOT);
                if (!d.isEmpty() && !isInfrastructureDomain(d)) domains.add(d);
            }
        } catch (Exception ignored) {}
    }

    private void saveRules(Set<String> set) {
        File file = new File(context.getFilesDir(), RULE_FILE);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            for (String d : set) out.write((d + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public void destroy() {
        executor.shutdownNow();
    }
}
