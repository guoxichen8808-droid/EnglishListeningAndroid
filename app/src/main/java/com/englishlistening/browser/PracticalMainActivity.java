package com.englishlistening.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PracticalMainActivity extends Activity {
    private static final int STORAGE_PERMISSION_CODE = 901;

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private AppData data;
    private AudioPlayerDialog audioPlayer;
    private RealAiTutor aiTutor;
    private OriginalMediaController originalMedia;
    private PendingDownload pendingDownload;
    private long sessionStarted;

    private static class PendingDownload {
        String url, userAgent, contentDisposition, mimeType;
        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url; this.userAgent = userAgent; this.contentDisposition = contentDisposition; this.mimeType = mimeType;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247,248,250));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        data = new AppData(this);
        buildUi();
        configureWebView();
        audioPlayer = new AudioPlayerDialog(this);
        aiTutor = new RealAiTutor(this, webView, data);
        originalMedia = new OriginalMediaController(this, webView);
        sessionStarted = System.currentTimeMillis();
        if (savedInstanceState == null) loadHome();
        else if (webView.restoreState(savedInstanceState) == null) loadHome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(5), dp(8), dp(5));
        top.setBackgroundColor(Color.rgb(247,248,250));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("输入网址或搜索内容");
        addressBar.setTextSize(14);
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button go = button("前往", 12);
        go.setOnClickListener(v -> navigate());
        top.addView(go, new LinearLayout.LayoutParams(dp(58), dp(42)));
        Button more = button("⋮", 22);
        more.setOnClickListener(v -> showMore());
        top.addView(more, new LinearLayout.LayoutParams(dp(46), dp(42)));
        addressBar.setOnEditorActionListener((v, actionId, event) -> { navigate(); return true; });
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout webTools = new LinearLayout(this);
        webTools.setOrientation(LinearLayout.HORIZONTAL);
        webTools.setBackgroundColor(Color.rgb(245,246,248));
        String[] tools = {"←", "→", "原声精听", "☆收藏", "存素材"};
        for (int i = 0; i < tools.length; i++) {
            Button b = button(tools[i], i == 2 ? 11 : 12);
            final int index = i;
            b.setOnClickListener(v -> {
                if (index == 0 && webView.canGoBack()) webView.goBack();
                else if (index == 1 && webView.canGoForward()) webView.goForward();
                else if (index == 2) originalMedia.show();
                else if (index == 3) addFavorite();
                else if (index == 4) saveCurrentMaterial();
            });
            webTools.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(webTools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setBackgroundColor(Color.rgb(248,249,251));
        String[] names = {"首页", "素材库", "下载", "历史", "AI老师"};
        for (int i = 0; i < names.length; i++) {
            Button b = button(names[i], 12);
            final int index = i;
            b.setOnClickListener(v -> {
                if (index == 0) loadHome();
                else if (index == 1) showMaterials();
                else if (index == 2) showDownloads();
                else if (index == 3) showHistory();
                else if (index == 4) aiTutor.openFromCurrentPage();
            });
            main.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(main, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        setContentView(root);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handleUrl(request.getUrl().toString()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleUrl(url); }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addressBar.setText(url != null && url.startsWith("data:") ? "" : (url == null ? "" : url));
                if (AppData.isHttp(url) && !AppData.isAiUrl(url)) {
                    data.addHistory(url, view.getTitle());
                    data.setLastLearning(url, view.getTitle());
                }
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (!AppData.isHttp(url)) { toast("这个链接不是普通 HTTP/HTTPS 文件，不能直接下载"); return; }
                startDownload(new PendingDownload(url, userAgent, contentDisposition, mimeType));
            }
        });
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = webView.getHitTestResult();
            if (hit == null || hit.getExtra() == null) return false;
            int type = hit.getType();
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                showLinkActions(hit.getExtra());
                return true;
            }
            return false;
        });
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("app://")) {
            if ("app://continue".equals(url)) continueLast();
            else if ("app://ai".equals(url)) aiTutor.openFromCurrentPage();
            else if ("app://materials".equals(url)) showMaterials();
            else if ("app://downloads".equals(url)) showDownloads();
            else if ("app://history".equals(url)) showHistory();
            return true;
        }
        if (AppData.isHttp(url)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("无法打开这个链接"); }
        return true;
    }

    private void loadHome() {
        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
                "body{font-family:sans-serif;background:#f5f7fb;color:#111827;margin:0}.w{padding:18px}.h{background:#1d4ed8;color:white;padding:20px;border-radius:18px}.h h1{margin:0 0 6px;font-size:24px}.h p{margin:0;font-size:13px;line-height:1.6}.g{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:16px}.c{background:white;border:1px solid #e5e7eb;border-radius:15px;padding:16px;text-decoration:none;color:#111827}.c b{display:block;margin-bottom:5px}.c span{font-size:12px;color:#667085;line-height:1.5}.q{display:flex;gap:8px;margin-top:13px}.q a{flex:1;background:#eff6ff;color:#1d4ed8;text-decoration:none;text-align:center;padding:11px;border-radius:12px;font-weight:bold;font-size:13px}.t{margin-top:16px;background:white;border:1px solid #e5e7eb;border-radius:15px;padding:14px;font-size:13px;line-height:1.7}@media(max-width:390px){.g{grid-template-columns:1fr}}</style></head><body><div class='w'>" +
                "<div class='h'><h1>英语听力 · 实用版</h1><p>只保留真正能用的：网页真人原声、素材保存、下载、历史、AI直接分析。</p><div class='q'><a href='app://continue'>继续上次</a><a href='app://ai'>AI老师</a></div></div>" +
                "<div class='g'><a class='c' href='https://elllo.org/'><b>ELLLO</b><span>多口音、原文、听力练习</span></a><a class='c' href='https://www.bbc.co.uk/learningenglish/'><b>BBC Learning English</b><span>6 Minute English 与真实英式英语</span></a><a class='c' href='https://learningenglish.voanews.com/'><b>VOA Learning English</b><span>语速更清楚，适合打基础</span></a><a class='c' href='https://youglish.com/'><b>YouGlish</b><span>查一个词在真实视频里怎么说</span></a></div>" +
                "<div class='q'><a href='app://materials'>素材库</a><a href='app://downloads'>下载</a><a href='app://history'>历史</a></div>" +
                "<div class='t'><b>原声精听：</b>进入有音频/视频的网页后，点下方“原声精听”，控制的是网页真人音频，不是机器人 TTS。<br><b>AI老师：</b>第一次配置免费 Gemini Key，以后点“听力分析 / 高频表达 / 工程迁移 / 出题”会直接在 APP 里返回结果，不再复制提示词。</div></div></body></html>";
        webView.loadDataWithBaseURL("https://local.english-listening.app/", html, "text/html", "UTF-8", null);
    }

    private void continueLast() {
        String url = data.lastLearningUrl();
        if (AppData.isHttp(url)) webView.loadUrl(url); else toast("还没有上次学习记录");
    }

    private void navigate() {
        String in = addressBar.getText().toString().trim();
        if (in.isEmpty()) return;
        String url;
        if (in.startsWith("http://") || in.startsWith("https://")) url = in;
        else if (in.matches("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*$")) url = "https://" + in;
        else url = "https://www.google.com/search?q=" + Uri.encode(in);
        webView.loadUrl(url);
    }

    private void addFavorite() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url)) { toast("当前不是可收藏网页"); return; }
        if (data.isFavorite(url)) { toast("已经收藏过了"); return; }
        EditText note = new EditText(this); note.setHint("可选备注");
        new AlertDialog.Builder(this).setTitle("收藏当前网页").setView(note)
                .setPositiveButton("收藏", (d,w) -> { data.addFavorite(url, webView.getTitle(), note.getText().toString()); toast("已收藏"); })
                .setNegativeButton("取消", null).show();
    }

    private void saveCurrentMaterial() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url)) { toast("请先打开学习网页"); return; }
        String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,10000);})()";
        webView.evaluateJavascript(js, value -> {
            String text = decode(value).trim();
            if (text.isEmpty()) { toast("这个网页没有提取到可保存文字"); return; }
            EditText note = new EditText(this); note.setHint("可选备注，例如：今天练这段");
            new AlertDialog.Builder(this).setTitle("保存到素材库")
                    .setMessage(AppData.ellipsize(webView.getTitle(), 70)).setView(note)
                    .setPositiveButton("保存", (d,w) -> { data.addMaterial(webView.getTitle(), text, url, note.getText().toString()); toast("已保存到素材库"); })
                    .setNeutralButton("直接AI练", (d,w) -> aiTutor.openMaterial(webView.getTitle(), text, url))
                    .setNegativeButton("取消", null).show();
        });
    }

    private void showMaterials() {
        JSONArray arr = data.materials();
        if (arr.length() == 0) { toast("素材库还是空的。网页里选中一段文字后点“存素材”。"); return; }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", "素材"), 40) + "\n" + AppData.ellipsize(o.optString("text", ""), 55);
        }
        new AlertDialog.Builder(this).setTitle("素材库 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showMaterialActions(arr.optJSONObject(which), which))
                .setNegativeButton("关闭", null).show();
    }

    private void showMaterialActions(JSONObject item, int index) {
        if (item == null) return;
        String[] actions = {"AI老师直接练", "打开原网页", "复制文字", "删除"};
        new AlertDialog.Builder(this).setTitle(AppData.ellipsize(item.optString("title"), 55)).setItems(actions, (d, which) -> {
            if (which == 0) aiTutor.openMaterial(item.optString("title"), item.optString("text"), item.optString("url"));
            else if (which == 1) { String url = item.optString("url"); if (AppData.isHttp(url)) webView.loadUrl(url); else toast("这个素材没有原网页"); }
            else if (which == 2) copy(item.optString("text"), "素材");
            else { data.removeMaterial(index); toast("已删除"); }
        }).setNegativeButton("返回", null).show();
    }

    private void showHistory() {
        JSONArray arr = data.history();
        if (arr.length() == 0) { toast("还没有历史记录"); return; }
        int n = Math.min(arr.length(), 80);
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", o.optString("url")), 42) + "\n" + AppData.compactHost(o.optString("url"));
        }
        new AlertDialog.Builder(this).setTitle("最近浏览 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> { JSONObject o = arr.optJSONObject(which); if (o != null) webView.loadUrl(o.optString("url")); })
                .setNegativeButton("关闭", null).show();
    }

    private void showDownloads() {
        JSONArray arr = data.downloads();
        if (arr.length() == 0) { toast("还没有下载记录"); return; }
        String[] labels = new String[arr.length()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("name"), 40) + "\n" + fmt.format(new Date(o.optLong("time",0)));
        }
        new AlertDialog.Builder(this).setTitle("下载 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showDownloadActions(arr.optJSONObject(which)))
                .setPositiveButton("系统下载", (d,w) -> { try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); } catch (Exception e) { toast("请在文件管理器打开 Downloads/EnglishListening"); } })
                .setNegativeButton("关闭", null).show();
    }

    private void showDownloadActions(JSONObject item) {
        if (item == null) return;
        String mime = item.optString("mime", "");
        boolean audio = mime.startsWith("audio/") || AppData.isAudio(item.optString("name"));
        String[] actions = audio ? new String[]{"APP播放器", "打开文件", "重新下载", "复制来源"} : new String[]{"打开文件", "重新下载", "复制来源"};
        new AlertDialog.Builder(this).setTitle(item.optString("name", "下载文件")).setItems(actions, (d, which) -> {
            String action = actions[which];
            if ("APP播放器".equals(action)) openDownloaded(item, true);
            else if ("打开文件".equals(action)) openDownloaded(item, false);
            else if ("重新下载".equals(action)) startDownload(new PendingDownload(item.optString("url"), webView.getSettings().getUserAgentString(), null, mime));
            else copy(item.optString("url"), "来源网址");
        }).show();
    }

    private void openDownloaded(JSONObject item, boolean play) {
        DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        Uri uri = dm.getUriForDownloadedFile(item.optLong("id", -1));
        if (uri == null) { toast("文件可能还没下完或已被删除"); return; }
        if (play) { audioPlayer.show(uri.toString(), item.optString("name", "音频")); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            String mime = item.optString("mime", "*/*");
            if (mime.isEmpty()) mime = "*/*";
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) { toast("没有应用可以打开这个文件"); }
    }

    private void showLinkActions(String url) {
        boolean audio = AppData.isAudio(url);
        String[] actions = audio ? new String[]{"打开", "APP播放", "下载", "复制"} : new String[]{"打开", "下载", "复制"};
        new AlertDialog.Builder(this).setTitle("链接操作").setItems(actions, (d, which) -> {
            String a = actions[which];
            if ("打开".equals(a)) webView.loadUrl(url);
            else if ("APP播放".equals(a)) audioPlayer.show(url, "网页音频");
            else if ("下载".equals(a)) startDownload(new PendingDownload(url, webView.getSettings().getUserAgentString(), null, AppData.guessMime(url)));
            else copy(url, "链接");
        }).show();
    }

    private void startDownload(PendingDownload d) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = d;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }
        enqueue(d);
    }

    private void enqueue(PendingDownload d) {
        try {
            String name = URLUtil.guessFileName(d.url, d.contentDisposition, d.mimeType).replaceAll("[\\\\/:*?\"<>|]", "_");
            String mime = d.mimeType == null || d.mimeType.isEmpty() ? AppData.guessMime(d.url) : d.mimeType;
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(d.url));
            r.setTitle(name);
            r.setDescription("EnglishListening 下载");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setMimeType(mime);
            if (d.userAgent != null && !d.userAgent.isEmpty()) r.addRequestHeader("User-Agent", d.userAgent);
            String cookies = CookieManager.getInstance().getCookie(d.url);
            if (cookies != null && !cookies.isEmpty()) r.addRequestHeader("Cookie", cookies);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EnglishListening/" + name);
            long id = ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            data.addDownload(id, name, d.url, mime);
            toast("开始下载：" + name);
        } catch (Exception e) { toast("下载失败：" + e.getMessage()); }
    }

    private void showMore() {
        String[] actions = {"刷新网页", "用系统浏览器打开", "显示收藏夹", "清除网页缓存"};
        new AlertDialog.Builder(this).setTitle("更多").setItems(actions, (d, which) -> {
            if (which == 0) webView.reload();
            else if (which == 1) { String u = webView.getUrl(); if (AppData.isHttp(u)) try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {} }
            else if (which == 2) showFavorites();
            else { webView.clearCache(true); toast("缓存已清除"); }
        }).show();
    }

    private void showFavorites() {
        JSONArray arr = data.favorites();
        if (arr.length() == 0) { toast("还没有收藏"); return; }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", o.optString("url")), 45);
        }
        new AlertDialog.Builder(this).setTitle("收藏夹").setItems(labels, (d, which) -> {
            JSONObject o = arr.optJSONObject(which); if (o != null) webView.loadUrl(o.optString("url"));
        }).setNegativeButton("关闭", null).show();
    }

    private String decode(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch (Exception e) { return value.replace("\\n", "\n").replace("\\\"", "\""); }
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        toast("已复制");
    }

    private Button button(String text, int size) {
        Button b = new Button(this); b.setText(text); b.setTextSize(size); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(2),0,dp(2),0); return b;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && pendingDownload != null) {
            PendingDownload d = pendingDownload; pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enqueue(d); else toast("需要存储权限才能下载");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == RealAiTutor.SPEECH_REQUEST && resultCode == RESULT_OK) aiTutor.handleSpeechResult(intent);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        long elapsed = Math.max(0, (System.currentTimeMillis() - sessionStarted) / 1000);
        data.addStudySeconds(Math.min(elapsed, 3600));
        if (aiTutor != null) aiTutor.destroy();
        if (originalMedia != null) originalMedia.stopStatus();
        if (audioPlayer != null) audioPlayer.stop();
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
