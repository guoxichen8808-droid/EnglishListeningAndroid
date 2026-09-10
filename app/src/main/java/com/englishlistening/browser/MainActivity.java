package com.englishlistening.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int STORAGE_PERMISSION_CODE = 701;
    private static final int FILE_CHOOSER_CODE = 702;

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private Button backButton;
    private Button forwardButton;
    private PendingDownload pendingDownload;
    private ValueCallback<Uri[]> filePathCallback;

    private AppData data;
    private AudioPlayerDialog audioPlayer;
    private AiPracticeHelper aiPractice;
    private UltimateTools tools;
    private long sessionStarted;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

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
        data = new AppData(this);
        buildUi();
        configureWebView();
        audioPlayer = new AudioPlayerDialog(this);
        aiPractice = new AiPracticeHelper(this, webView, data);
        tools = new UltimateTools(this, webView, data, aiPractice);
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            if (data.autoResume() && AppData.isHttp(data.lastLearningUrl())) webView.loadUrl(data.lastLearningUrl());
            else loadHome();
        }
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

        LinearLayout browserNav = new LinearLayout(this);
        browserNav.setOrientation(LinearLayout.HORIZONTAL);
        browserNav.setGravity(Gravity.CENTER);
        browserNav.setBackgroundColor(Color.rgb(245, 246, 248));
        String[] navNames = {"←", "→", "刷新", "☆ 收藏", "页内查找"};
        Button[] nav = new Button[navNames.length];
        for (int i = 0; i < navNames.length; i++) {
            nav[i] = makeButton(navNames[i], 12);
            browserNav.addView(nav[i], new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        backButton = nav[0];
        forwardButton = nav[1];
        nav[0].setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        nav[1].setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        nav[2].setOnClickListener(v -> webView.reload());
        nav[3].setOnClickListener(v -> addCurrentFavorite());
        nav[4].setOnClickListener(v -> tools.findInPage());
        root.addView(browserNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout mainNav = new LinearLayout(this);
        mainNav.setOrientation(LinearLayout.HORIZONTAL);
        mainNav.setGravity(Gravity.CENTER);
        mainNav.setBackgroundColor(Color.rgb(248, 249, 251));
        String[] mainNames = {"首页", "继续", "收藏", "下载", "训练"};
        Button[] main = new Button[mainNames.length];
        for (int i = 0; i < mainNames.length; i++) {
            main[i] = makeButton(mainNames[i], 12);
            mainNav.addView(main[i], new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        main[0].setOnClickListener(v -> loadHome());
        main[1].setOnClickListener(v -> continueLastLearning());
        main[2].setOnClickListener(v -> showFavorites());
        main[3].setOnClickListener(v -> showDownloads());
        main[4].setOnClickListener(v -> aiPractice.openFromCurrentPage());
        root.addView(mainNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
    }

    private Button makeButton(String text, int size) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(size);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
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
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);

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
                boolean local = url != null && url.startsWith("file:///android_asset/");
                addressBar.setText(local ? "" : url);
                if (AppData.isHttp(url) && !AppData.isAiUrl(url)) {
                    data.addHistory(url, view.getTitle());
                    data.setLastLearning(url, view.getTitle());
                }
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

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent;
                try { intent = params.createIntent(); }
                catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_CODE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "找不到文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() { hideCustomView(); }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (!AppData.isHttp(url)) {
                    Toast.makeText(MainActivity.this, "不是普通 HTTP/HTTPS 文件，无法直接下载", Toast.LENGTH_LONG).show();
                    return;
                }
                startDownloadWithPermission(new PendingDownload(url, userAgent, contentDisposition, mimeType));
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

    private void hideCustomView() {
        if (customView == null) return;
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        decor.removeView(customView);
        customView = null;
        getWindow().getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : View.SYSTEM_UI_FLAG_VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    private boolean handleSpecialUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("app://")) {
            if (url.equals("app://continue")) continueLastLearning();
            else if (url.equals("app://studio") || url.equals("app://ai")) aiPractice.openFromCurrentPage();
            else if (url.equals("app://materials")) tools.showMaterials();
            else if (url.equals("app://favorites")) showFavorites();
            else if (url.equals("app://downloads")) showDownloads();
            else if (url.equals("app://history")) showHistory("");
            else if (url.equals("app://stats")) tools.showStats();
            else if (url.equals("app://shortcuts")) tools.showShortcuts();
            else if (url.equals("app://backup")) tools.showBackupActions();
            else if (url.equals("app://settings")) tools.showSettings();
            return true;
        }
        if (AppData.isHttp(url) || url.startsWith("file:///android_asset/")) return false;
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

    private void loadHome() {
        String html = HomePageBuilder.build(data);
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private void continueLastLearning() {
        String url = data.lastLearningUrl();
        if (AppData.isHttp(url)) webView.loadUrl(url);
        else Toast.makeText(this, "还没有上次学习记录", Toast.LENGTH_SHORT).show();
    }

    private void updateNavButtons() {
        if (backButton != null) backButton.setEnabled(webView.canGoBack());
        if (forwardButton != null) forwardButton.setEnabled(webView.canGoForward());
    }

    private void showHistory(String query) {
        JSONArray arr = data.history();
        ArrayList<JSONObject> matches = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String text = (o.optString("title") + " " + o.optString("url") + " " + o.optString("host")).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || text.contains(q)) matches.add(o);
        }
        if (matches.isEmpty()) {
            Toast.makeText(this, q.isEmpty() ? "还没有浏览记录" : "没有匹配的历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setPadding(dp(18), 0, dp(18), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索历史，例如 ceiling / BBC");
        search.setText(query == null ? "" : query);
        searchBox.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        String[] labels = new String[matches.size()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < matches.size(); i++) {
            JSONObject o = matches.get(i);
            labels[i] = AppData.ellipsize(o.optString("title", o.optString("url")), 40) + "\n" + fmt.format(new Date(o.optLong("time", 0))) + " · " + o.optInt("visits", 1) + "次 · " + AppData.compactHost(o.optString("url"));
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(q.isEmpty() ? "浏览历史" : "历史搜索：" + query)
                .setView(searchBox)
                .setItems(labels, (d, which) -> webView.loadUrl(matches.get(which).optString("url")))
                .setPositiveButton("搜索", null)
                .setNeutralButton("清空", (d,w) -> confirmClearHistory())
                .setNegativeButton("关闭", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String next = search.getText().toString().trim();
            dialog.dismiss();
            showHistory(next);
        }));
        dialog.show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("清空浏览历史？")
                .setMessage("收藏、素材库和已下载文件不会删除。")
                .setPositiveButton("清空", (d,w) -> {
                    data.clearHistory(); webView.clearHistory(); Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("取消", null).show();
    }

    private void addCurrentFavorite() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url) || AppData.isAiUrl(url)) {
            Toast.makeText(this, "当前页面不能加入学习收藏", Toast.LENGTH_SHORT).show(); return;
        }
        if (data.isFavorite(url)) { Toast.makeText(this, "这个页面已经收藏过了", Toast.LENGTH_SHORT).show(); return; }
        EditText note = new EditText(this); note.setHint("可选备注：例如 重点练连读");
        new AlertDialog.Builder(this).setTitle("收藏当前内容")
                .setMessage(AppData.safeTitle(webView.getTitle(), url)).setView(note)
                .setPositiveButton("收藏", (d,w) -> { data.addFavorite(url, webView.getTitle(), note.getText().toString()); Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show(); })
                .setNeutralButton("收藏并训练", (d,w) -> { data.addFavorite(url, webView.getTitle(), note.getText().toString()); aiPractice.openFromCurrentPage(); })
                .setNegativeButton("取消", null).show();
    }

    private void showFavorites() {
        JSONArray arr = data.favorites();
        if (arr.length() == 0) { Toast.makeText(this, "还没有收藏。浏览网页后点“☆ 收藏”。", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            String title = o == null ? "" : AppData.ellipsize(o.optString("title", o.optString("url")), 40);
            String note = o == null ? "" : o.optString("note", "");
            labels[i] = title + (note.isEmpty() ? "" : "\n备注：" + AppData.ellipsize(note, 34));
        }
        new AlertDialog.Builder(this).setTitle("我的收藏 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showFavoriteActions(arr.optJSONObject(which), which))
                .setNegativeButton("关闭", null).show();
    }

    private void showFavoriteActions(JSONObject item, int index) {
        if (item == null) return;
        String[] actions = {"打开网页", "直接训练", "加入首页快捷入口", "删除收藏"};
        new AlertDialog.Builder(this).setTitle(AppData.ellipsize(item.optString("title"), 50))
                .setItems(actions, (d, which) -> {
                    String url = item.optString("url");
                    if (which == 0) webView.loadUrl(url);
                    else if (which == 1) { webView.loadUrl(url); Toast.makeText(this, "网页加载后点底部“训练”", Toast.LENGTH_LONG).show(); }
                    else if (which == 2) { if (data.addShortcut(item.optString("title"), url)) Toast.makeText(this, "已加入首页", Toast.LENGTH_SHORT).show(); else Toast.makeText(this, "已存在或首页入口已满", Toast.LENGTH_SHORT).show(); }
                    else { data.removeFavorite(index); Toast.makeText(this, "收藏已删除", Toast.LENGTH_SHORT).show(); }
                }).setNegativeButton("返回", null).show();
    }

    private void showLinkMenu(String url) {
        boolean audio = AppData.isAudio(url);
        String[] options = AppData.isHttp(url)
                ? (audio ? new String[]{"打开链接", "直接播放", "下载链接", "复制链接", "系统浏览器打开"}
                         : new String[]{"打开链接", "下载链接", "复制链接", "系统浏览器打开"})
                : new String[]{"打开链接", "复制链接", "系统浏览器打开"};
        new AlertDialog.Builder(this).setTitle("链接操作").setItems(options, (d, which) -> {
            String action = options[which];
            if (action.equals("打开链接")) webView.loadUrl(url);
            else if (action.equals("直接播放")) audioPlayer.show(url, "网页音频");
            else if (action.equals("下载链接")) startDownloadWithPermission(new PendingDownload(url, webView.getSettings().getUserAgentString(), null, AppData.guessMime(url)));
            else if (action.equals("复制链接")) copy(url, "链接");
            else openExternal(url);
        }).show();
    }

    private void startDownloadWithPermission(PendingDownload download) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = download;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }
        enqueueDownload(download);
    }

    private void enqueueDownload(PendingDownload d) {
        try {
            String fileName = sanitizeFileName(URLUtil.guessFileName(d.url, d.contentDisposition, d.mimeType));
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(d.url));
            request.setTitle(fileName);
            request.setDescription("EnglishListening Ultimate 下载");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            String mime = d.mimeType == null || d.mimeType.trim().isEmpty() ? AppData.guessMime(d.url) : d.mimeType;
            request.setMimeType(mime);
            if (d.userAgent != null && !d.userAgent.isEmpty()) request.addRequestHeader("User-Agent", d.userAgent);
            String cookies = CookieManager.getInstance().getCookie(d.url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EnglishListening/" + fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            long id = dm.enqueue(request);
            data.addDownload(id, fileName, d.url, mime);
            Toast.makeText(this, "开始下载：" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDownloads() {
        JSONArray arr = data.downloads();
        if (arr.length() == 0) {
            new AlertDialog.Builder(this).setTitle("下载")
                    .setMessage("还没有 APP 下载记录。\n文件默认保存在 Downloads/EnglishListening/")
                    .setPositiveButton("系统下载", (d,w) -> openSystemDownloads()).setNegativeButton("关闭", null).show();
            return;
        }
        String[] labels = new String[arr.length()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = (o == null ? "" : AppData.ellipsize(o.optString("name"), 40)) + "\n" + (o == null ? "" : fmt.format(new Date(o.optLong("time", 0))));
        }
        new AlertDialog.Builder(this).setTitle("下载记录 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showDownloadActions(arr.optJSONObject(which)))
                .setPositiveButton("系统下载", (d,w) -> openSystemDownloads())
                .setNeutralButton("清记录", (d,w) -> { data.clearDownloadsList(); Toast.makeText(this, "只清除记录，文件没有删除", Toast.LENGTH_LONG).show(); })
                .setNegativeButton("关闭", null).show();
    }

    private void showDownloadActions(JSONObject item) {
        if (item == null) return;
        long id = item.optLong("id", -1);
        String name = item.optString("name", "下载文件");
        String mime = item.optString("mime", "");
        boolean isAudio = AppData.isAudio(name) || mime.startsWith("audio/");
        String[] actions = isAudio ? new String[]{"播放", "打开文件", "重新下载", "复制来源网址"}
                                   : new String[]{"打开文件", "重新下载", "复制来源网址"};
        new AlertDialog.Builder(this).setTitle(name).setItems(actions, (d, which) -> {
            String action = actions[which];
            if (action.equals("播放")) openDownloaded(id, mime, true);
            else if (action.equals("打开文件")) openDownloaded(id, mime, false);
            else if (action.equals("重新下载")) startDownloadWithPermission(new PendingDownload(item.optString("url"), webView.getSettings().getUserAgentString(), null, mime));
            else copy(item.optString("url"), "来源网址");
        }).show();
    }

    private void openDownloaded(long id, String mime, boolean playAudio) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri == null) { Toast.makeText(this, "文件可能未完成或已被删除", Toast.LENGTH_LONG).show(); return; }
        if (playAudio) { audioPlayer.show(uri.toString(), "已下载音频"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime == null || mime.isEmpty() ? "*/*" : mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) { Toast.makeText(this, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show(); }
    }

    private void openSystemDownloads() {
        try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
            catch (Exception ignored) { Toast.makeText(this, "请在文件管理器打开 Downloads/EnglishListening", Toast.LENGTH_LONG).show(); }
        }
    }

    private void showMoreMenu() {
        String[] items = {"训练当前网页", "保存网页文字到素材库", "纯文本阅读", "收藏当前页", "加入首页快捷入口", "网页内查找", "切换电脑/手机网页", "分享当前网页", "系统浏览器打开", "复制当前网址", "夜间阅读切换", "素材库", "学习统计", "备份与恢复", "设置", "停止音频播放", "清除网页缓存"};
        new AlertDialog.Builder(this).setTitle("终极工具箱").setItems(items, (d, which) -> {
            String current = webView.getUrl();
            if (which == 0) aiPractice.openFromCurrentPage();
            else if (which == 1) aiPractice.saveCurrentPageToLibrary();
            else if (which == 2) tools.showReaderMode();
            else if (which == 3) addCurrentFavorite();
            else if (which == 4) tools.addCurrentShortcut();
            else if (which == 5) tools.findInPage();
            else if (which == 6) tools.toggleDesktopMode();
            else if (which == 7) tools.shareCurrentPage();
            else if (which == 8 && AppData.isHttp(current)) openExternal(current);
            else if (which == 9 && current != null) copy(current, "网址");
            else if (which == 10) toggleNightReading();
            else if (which == 11) tools.showMaterials();
            else if (which == 12) tools.showStats();
            else if (which == 13) tools.showBackupActions();
            else if (which == 14) tools.showSettings();
            else if (which == 15) { audioPlayer.stop(); Toast.makeText(this, "音频播放已停止", Toast.LENGTH_SHORT).show(); }
            else if (which == 16) { webView.clearCache(true); Toast.makeText(this, "网页缓存已清除", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private void toggleNightReading() {
        String js = "(function(){var id='englishListeningNight';var x=document.getElementById(id);if(x){x.remove();return 'off';}var s=document.createElement('style');s.id=id;s.innerHTML='html{background:#111!important;filter:invert(1) hue-rotate(180deg)!important}img,video,picture,iframe{filter:invert(1) hue-rotate(180deg)!important}';document.documentElement.appendChild(s);return 'on';})()";
        webView.evaluateJavascript(js, value -> Toast.makeText(this, value != null && value.contains("on") ? "夜间阅读已开启" : "夜间阅读已关闭", Toast.LENGTH_SHORT).show());
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "没有可用的浏览器或对应应用", Toast.LENGTH_SHORT).show(); }
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        Toast.makeText(this, "已复制：" + label, Toast.LENGTH_SHORT).show();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) name = "download_" + System.currentTimeMillis();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && pendingDownload != null) {
            PendingDownload d = pendingDownload; pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enqueueDownload(d);
            else Toast.makeText(this, "Android 8/9 需要存储权限才能保存文件", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (tools != null && tools.handleActivityResult(requestCode, resultCode, intent)) { loadHome(); return; }
        if (requestCode == FILE_CHOOSER_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && intent != null) {
                    if (intent.getClipData() != null) {
                        int count = intent.getClipData().getItemCount(); results = new Uri[count];
                        for (int i = 0; i < count; i++) results[i] = intent.getClipData().getItemAt(i).getUri();
                    } else if (intent.getData() != null) results = new Uri[]{intent.getData()};
                }
                filePathCallback.onReceiveValue(results); filePathCallback = null;
            }
        } else if (requestCode == AiPracticeHelper.SPEECH_REQUEST && resultCode == RESULT_OK) {
            aiPractice.handleSpeechResult(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume(); sessionStarted = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        if (sessionStarted > 0) {
            long seconds = (System.currentTimeMillis() - sessionStarted) / 1000L;
            if (seconds >= 5) data.addStudySeconds(Math.min(seconds, 4 * 60 * 60));
            sessionStarted = 0;
        }
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState); super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && customView != null) { hideCustomView(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (aiPractice != null) aiPractice.destroy();
        if (audioPlayer != null) audioPlayer.stop();
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
