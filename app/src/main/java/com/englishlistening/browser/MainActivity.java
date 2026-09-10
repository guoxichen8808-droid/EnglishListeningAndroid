package com.englishlistening.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "english_listening_prefs";
    private static final String KEY_HISTORY = "history_json";
    private static final int MAX_HISTORY = 200;
    private static final int STORAGE_PERMISSION_CODE = 701;

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private Button backButton;
    private Button forwardButton;
    private PendingDownload pendingDownload;

    private static class PendingDownload {
        String url, userAgent, contentDisposition, mimeType;
        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247, 248, 250));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        buildUi();
        configureWebView();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) loadHome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(247, 248, 250));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("输入网址或搜索内容");
        addressBar.setTextSize(14);
        addressBar.setPadding(dp(10), 0, dp(8), 0);
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button go = makeButton("前往", 13);
        go.setOnClickListener(v -> navigateFromAddressBar());
        top.addView(go, new LinearLayout.LayoutParams(dp(58), dp(42)));

        Button more = makeButton("⋮", 24);
        more.setOnClickListener(v -> showMoreMenu());
        top.addView(more, new LinearLayout.LayoutParams(dp(46), dp(42)));

        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            navigateFromAddressBar();
            return true;
        });

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        String[] names = {"主页", "后退", "前进", "历史", "下载"};
        Button[] buttons = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            buttons[i] = makeButton(names[i], 13);
            bottom.addView(buttons[i], new LinearLayout.LayoutParams(0, dp(54), 1f));
        }
        backButton = buttons[1];
        forwardButton = buttons[2];
        buttons[0].setOnClickListener(v -> loadHome());
        buttons[1].setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        buttons[2].setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        buttons[3].setOnClickListener(v -> showHistory());
        buttons[4].setOnClickListener(v -> openSystemDownloads());
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
    }

    private Button makeButton(String text, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleSpecialUrl(request.getUrl().toString());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleSpecialUrl(url);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addressBar.setText(HOME_URL.equals(url) ? "" : url);
                if (isHttpUrl(url)) addHistory(url, view.getTitle());
                updateNavButtons();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
                updateNavButtons();
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (!isHttpUrl(url)) {
                    Toast.makeText(MainActivity.this, "该链接不是普通 HTTP/HTTPS 文件，无法直接下载。", Toast.LENGTH_LONG).show();
                    return;
                }
                startDownloadWithPermission(new PendingDownload(url, userAgent, contentDisposition, mimetype));
            }
        });

        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = webView.getHitTestResult();
            if (hit == null || hit.getExtra() == null) return false;
            int type = hit.getType();
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE || type == WebView.HitTestResult.IMAGE_TYPE) {
                showLinkMenu(hit.getExtra());
                return true;
            }
            return false;
        });
    }

    private boolean handleSpecialUrl(String url) {
        if (url == null || isHttpUrl(url) || url.startsWith("file:///android_asset/")) return false;
        try {
            Intent intent = url.startsWith("intent://") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开此链接", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void navigateFromAddressBar() {
        String input = addressBar.getText().toString().trim();
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) url = input;
        else if (input.matches("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*$")) url = "https://" + input;
        else url = "https://www.google.com/search?q=" + Uri.encode(input);
        webView.loadUrl(url);
        webView.requestFocus();
    }

    private void loadHome() { webView.loadUrl(HOME_URL); }

    private void updateNavButtons() {
        if (backButton != null) backButton.setEnabled(webView.canGoBack());
        if (forwardButton != null) forwardButton.setEnabled(webView.canGoForward());
    }

    private void addHistory(String url, String title) {
        try {
            JSONArray old = readHistory();
            JSONArray next = new JSONArray();
            JSONObject current = new JSONObject();
            current.put("url", url);
            current.put("title", title == null || title.trim().isEmpty() ? url : title.trim());
            current.put("time", System.currentTimeMillis());
            next.put(current);
            for (int i = 0; i < old.length() && next.length() < MAX_HISTORY; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !url.equals(item.optString("url"))) next.put(item);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HISTORY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private JSONArray readHistory() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        try { return new JSONArray(p.getString(KEY_HISTORY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showHistory() {
        JSONArray arr = readHistory();
        if (arr.length() == 0) {
            Toast.makeText(this, "还没有浏览记录", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[arr.length()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) { labels[i] = ""; continue; }
            String title = o.optString("title", o.optString("url"));
            if (title.length() > 38) title = title.substring(0, 38) + "…";
            labels[i] = title + "\n" + fmt.format(new Date(o.optLong("time", 0L))) + "  " + compactHost(o.optString("url"));
        }
        new AlertDialog.Builder(this)
                .setTitle("浏览历史")
                .setItems(labels, (dialog, which) -> {
                    JSONObject o = arr.optJSONObject(which);
                    if (o != null) webView.loadUrl(o.optString("url"));
                })
                .setNeutralButton("清空历史", (dialog, which) -> confirmClearHistory())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清空浏览历史？")
                .setMessage("只清除本 APP 保存的历史记录，不会删除已下载文件。")
                .setPositiveButton("清空", (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_HISTORY).apply();
                    webView.clearHistory();
                    Toast.makeText(this, "浏览历史已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLinkMenu(String url) {
        String[] options = isHttpUrl(url)
                ? new String[]{"打开链接", "下载链接", "复制链接", "用系统浏览器打开"}
                : new String[]{"打开链接", "复制链接", "用系统浏览器打开"};
        new AlertDialog.Builder(this).setTitle("链接操作").setItems(options, (dialog, which) -> {
            String choice = options[which];
            if ("打开链接".equals(choice)) webView.loadUrl(url);
            else if ("下载链接".equals(choice)) startDownloadWithPermission(new PendingDownload(url, webView.getSettings().getUserAgentString(), null, guessMime(url)));
            else if ("复制链接".equals(choice)) copyToClipboard(url);
            else openExternal(url);
        }).show();
    }

    private void showMoreMenu() {
        String[] items = {"刷新当前页", "用系统浏览器打开", "复制当前网址", "清除网页缓存"};
        new AlertDialog.Builder(this).setTitle("更多").setItems(items, (dialog, which) -> {
            String current = webView.getUrl();
            if (which == 0) webView.reload();
            else if (which == 1 && isHttpUrl(current)) openExternal(current);
            else if (which == 2 && current != null) copyToClipboard(current);
            else if (which == 3) {
                webView.clearCache(true);
                Toast.makeText(this, "网页缓存已清除", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    private void startDownloadWithPermission(PendingDownload d) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = d;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }
        enqueueDownload(d);
    }

    private void enqueueDownload(PendingDownload d) {
        try {
            String fileName = sanitizeFileName(URLUtil.guessFileName(d.url, d.contentDisposition, d.mimeType));
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(d.url));
            request.setTitle(fileName);
            request.setDescription("英语听力 APP 下载");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            if (d.mimeType != null && !d.mimeType.trim().isEmpty()) request.setMimeType(d.mimeType);
            if (d.userAgent != null && !d.userAgent.isEmpty()) request.addRequestHeader("User-Agent", d.userAgent);
            String cookies = CookieManager.getInstance().getCookie(d.url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EnglishListening/" + fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "开始下载：" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && pendingDownload != null) {
            PendingDownload d = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enqueueDownload(d);
            else Toast.makeText(this, "Android 8/9 需要存储权限才能保存文件", Toast.LENGTH_LONG).show();
        }
    }

    private void openSystemDownloads() {
        try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
            catch (Exception ignored) { Toast.makeText(this, "请在文件管理器打开 Downloads/EnglishListening", Toast.LENGTH_LONG).show(); }
        }
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show(); }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("网址", text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private String compactHost(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.replaceFirst("^www\\.", "");
        } catch (Exception e) { return ""; }
    }

    private String guessMime(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains(".mp3")) return "audio/mpeg";
        if (u.contains(".m4a")) return "audio/mp4";
        if (u.contains(".wav")) return "audio/wav";
        if (u.contains(".mp4")) return "video/mp4";
        if (u.contains(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) name = "download_" + System.currentTimeMillis();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
