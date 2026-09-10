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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdBlocker {
    public interface Callback {
        void onDone(String message);
    }

    private static final String PREFS = "adblock_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String RULE_FILE = "adblock_domains.txt";
    private static final String[] SOURCES = {
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
            "https://filters.adtidy.org/extension/chromium/filters/224.txt"
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
        if (!isEnabled() || request == null) return null;
        String url = request.getUrl() == null ? "" : request.getUrl().toString();
        if (!shouldBlock(url)) return null;
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    public boolean shouldBlock(String url) {
        if (!isEnabled() || url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (domains.contains(host)) return true;
            int dot = host.indexOf('.');
            while (dot > 0 && dot < host.length() - 1) {
                host = host.substring(dot + 1);
                if (domains.contains(host)) return true;
                dot = host.indexOf('.');
            }
        } catch (Exception ignored) {}
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
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "EnglishListening/1.2");
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) parseRule(line, downloaded);
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
                    loadBuiltIns();
                    domains.addAll(downloaded);
                }
                saveRules(downloaded);
            }
            final int ok = sourceOk;
            final int count = domains.size();
            main.post(() -> {
                if (callback != null) callback.onDone(ok > 0 ? "广告规则已更新，共约 " + count + " 个域名规则" : "广告规则更新失败，继续使用本地缓存");
            });
        });
    }

    private void parseRule(String raw, Set<String> out) {
        if (raw == null) return;
        String line = raw.trim();
        if (!line.startsWith("||") || line.startsWith("||/")) return;
        int end = line.indexOf('^', 2);
        if (end < 0) end = line.indexOf('$', 2);
        if (end < 0) end = line.length();
        String domain = line.substring(2, end).trim().toLowerCase(Locale.ROOT);
        int slash = domain.indexOf('/');
        if (slash >= 0) domain = domain.substring(0, slash);
        if (domain.startsWith("*.") ) domain = domain.substring(2);
        if (domain.matches("^[a-z0-9._-]+\\.[a-z]{2,}$")) out.add(domain);
    }

    private void loadBuiltIns() {
        domains.clear();
        String[] built = {
                "doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com",
                "googletagservices.com","googletagmanager.com","amazon-adsystem.com","adsrvr.org",
                "taboola.com","outbrain.com","criteo.com","criteo.net","scorecardresearch.com",
                "adnxs.com","pubmatic.com","rubiconproject.com","openx.net","moatads.com",
                "zedo.com","yieldmo.com","adform.net","smartadserver.com","quantserve.com"
        };
        Collections.addAll(domains, built);
    }

    private void loadCachedRules() {
        File file = new File(context.getFilesDir(), RULE_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty()) domains.add(line);
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
