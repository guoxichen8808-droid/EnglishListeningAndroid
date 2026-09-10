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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
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
        aiTutor = new RealAiTutor(this, webView, data);
        originalMedia = new OriginalMediaController(this, webView);
        sessionStarted = System.currentTimeMillis();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) loadHome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(5), dp(8), dp(5));
        top.setBackgroundColor(Color.rgb(247, 248, 250));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("输入网址或搜索内容");
        addressBar.setTextSize(14);
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button go = button("前往", 12);
        go.setOnClickListener(v -> navigate());
        top.addView(go, new LinearLayout.LayoutParams(dp(58), dp(42)));

        Button more = button("⋮", 23);
        more.setOnClickListener(this::showMore);
        top.addView(more, new LinearLayout.LayoutParams(dp(46), dp(42)));
        addressBar.setOnEditorActionListener((v, actionId, event) -> { navigate(); return true; });
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setBackgroundColor(Color.rgb(248, 249, 251));
        String[] names = {"前进", "原声精听", "存素材", "AI老师"};
        for (int i = 0; i < names.length; i++) {
            Button b = button(names[i], i == 1 ? 11 : 12);
            final int index = i;
            b.setOnClickListener(v -> {
                if (index == 0) {
                    if (webView.canGoForward()) webView.goForward();
                    else toast("没有可前进的页面");
                } else if (index == 1) {
                    originalMedia.show();
                } else if (index == 2) {
                    saveCurrentMaterial();
                } else {
                    aiTutor.openFromCurrentPage();
                }
            });
            bottom.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1f));
        }
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
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
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                boolean local = url != null && url.startsWith("https://local.english-listening.app/");
                addressBar.setText(local ? "" : (url == null ? "" : url));
                if (AppData.isHttp(url) && !AppData.isAiUrl(url) && !local) {
                    data.addHistory(url, view.getTitle());
                    data.setLastLearning(url, view.getTitle());
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (!AppData.isHttp(url)) {
                    toast("这个链接不是普通 HTTP/HTTPS 文件，不能直接下载");
                    return;
                }
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
            else if ("app://addsite".equals(url)) showAddSite();
            return true;
        }
        if (AppData.isHttp(url)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("无法打开这个链接"); }
        return true;
    }

    private void loadHome() {
        StringBuilder custom = new StringBuilder();
        JSONArray shortcuts = data.shortcuts();
        for (int i = 0; i < shortcuts.length(); i++) {
            JSONObject o = shortcuts.optJSONObject(i);
            if (o == null) continue;
            String name = AppData.html(o.optString("name", AppData.compactHost(o.optString("url"))));
            String url = AppData.html(o.optString("url"));
            custom.append("<a class='c' href='").append(url).append("'><b>").append(name)
                    .append("</b><span>").append(AppData.html(AppData.compactHost(o.optString("url")))).append("</span></a>");
        }
        custom.append("<a class='c plus' href='app://addsite'><b>＋</b><span>添加你常用的网站</span></a>");

        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
                "body{font-family:sans-serif;background:#f5f7fb;color:#111827;margin:0}.w{padding:16px}.h{background:#1d4ed8;color:white;padding:18px;border-radius:18px}.h h1{margin:0 0 5px;font-size:23px}.h p{margin:0;font-size:13px;opacity:.92}.g{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.c{background:white;border:1px solid #e5e7eb;border-radius:15px;padding:16px;text-decoration:none;color:#111827;min-height:92px}.c b{display:block;margin-bottom:7px;font-size:17px}.c span{font-size:12px;color:#667085;line-height:1.45}.plus b{font-size:30px;line-height:24px;color:#1d4ed8}.sec{font-size:15px;margin:18px 2px 4px;color:#475467}@media(max-width:390px){.g{grid-template-columns:1fr 1fr}}</style></head><body><div class='w'>" +
                "<div class='h'><h1>英语听力</h1><p>打开网站 → 听原声 → 存素材 → AI练习</p></div>" +
                "<div class='g'><a class='c' href='https://elllo.org/'><b>ELLLO</b><span>多口音、原文、听力练习</span></a>" +
                "<a class='c' href='https://www.bbc.co.uk/learningenglish/'><b>BBC Learning English</b><span>真实英式英语</span></a>" +
                "<a class='c' href='https://learningenglish.voanews.com/'><b>VOA Learning English</b><span>清晰美音、分级内容</span></a>" +
                "<a class='c' href='https://youglish.com/'><b>YouGlish</b><span>真人视频查发音</span></a></div>" +
                (shortcuts.length() > 0 ? "<div class='sec'>我的网站</div><div class='g'>" + custom + "</div>" : "<div class='sec'>我的网站</div><div class='g'>" + custom + "</div>") +
                "</div></body></html>";
        webView.loadDataWithBaseURL("https://local.english-listening.app/", html, "text/html", "UTF-8", null);
    }

    private void showAddSite() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), 0, dp(18), 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("网站名称，例如 TED");
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("网址，例如 https://www.ted.com");
        box.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        box.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        String current = webView.getUrl();
        if (AppData.isHttp(current) && !current.startsWith("https://local.english-listening.app/")) {
            name.setText(AppData.safeTitle(webView.getTitle(), AppData.compactHost(current)));
            url.setText(current);
        }
        new AlertDialog.Builder(this)
                .setTitle("添加到首页")
                .setView(box)
                .setPositiveButton("添加", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    if (n.isEmpty()) n = AppData.compactHost(u);
                    if (data.addShortcut(n, u)) {
                        toast("已添加到首页");
                        loadHome();
                    } else {
                        toast("网址无效、已经存在，或首页网站已满");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showManageSites() {
        JSONArray arr = data.shortcuts();
        if (arr.length() == 0) {
            toast("还没有自定义网站，首页点＋添加");
            return;
        }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : o.optString("name") + "\n" + AppData.compactHost(o.optString("url"));
        }
        new AlertDialog.Builder(this)
                .setTitle("管理首页网站")
                .setItems(labels, (d, which) -> {
                    JSONObject o = arr.optJSONObject(which);
                    if (o == null) return;
                    new AlertDialog.Builder(this)
                            .setTitle(o.optString("name"))
                            .setItems(new String[]{"打开", "删除"}, (x, action) -> {
                                if (action == 0) webView.loadUrl(o.optString("url"));
                                else {
                                    data.removeShortcut(which);
                                    toast("已从首页删除");
                                    loadHome();
                                }
                            }).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void continueLast() {
        String url = data.lastLearningUrl();
        if (AppData.isHttp(url)) webView.loadUrl(url);
        else toast("还没有上次学习记录");
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
        if (!AppData.isHttp(url) || url.startsWith("https://local.english-listening.app/")) {
            toast("当前不是可收藏网页");
            return;
        }
        if (data.isFavorite(url)) {
            toast("已经收藏过了");
            return;
        }
        EditText note = new EditText(this);
        note.setHint("可选备注");
        new AlertDialog.Builder(this)
                .setTitle("收藏当前网页")
                .setView(note)
                .setPositiveButton("收藏", (d, w) -> {
                    data.addFavorite(url, webView.getTitle(), note.getText().toString());
                    toast("已收藏");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveCurrentMaterial() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url) || url.startsWith("https://local.english-listening.app/")) {
            toast("请先打开学习网页");
            return;
        }
        String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,10000);})()";
        webView.evaluateJavascript(js, value -> {
            String text = decode(value).trim();
            if (text.isEmpty()) {
                toast("这个网页没有提取到可保存文字");
                return;
            }
            EditText note = new EditText(this);
            note.setHint("可选备注，例如：今天练这段");
            new AlertDialog.Builder(this)
                    .setTitle("保存到素材库")
                    .setMessage(AppData.ellipsize(webView.getTitle(), 70))
                    .setView(note)
                    .setPositiveButton("保存", (d, w) -> {
                        data.addMaterial(webView.getTitle(), text, url, note.getText().toString());
                        toast("已保存到素材库");
                    })
                    .setNeutralButton("直接AI练", (d, w) -> aiTutor.openMaterial(webView.getTitle(), text, url))
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void showMaterials() {
        JSONArray arr = data.materials();
        if (arr.length() == 0) {
            toast("素材库还是空的。网页里选中一段文字后点“存素材”。");
            return;
        }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", "素材"), 40) + "\n" + AppData.ellipsize(o.optString("text", ""), 55);
        }
        new AlertDialog.Builder(this)
                .setTitle("素材库 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showMaterialActions(arr.optJSONObject(which), which))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showMaterialActions(JSONObject item, int index) {
        if (item == null) return;
        String[] actions = {"AI老师直接练", "打开原网页", "复制文字", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(AppData.ellipsize(item.optString("title"), 55))
                .setItems(actions, (d, which) -> {
                    if (which == 0) aiTutor.openMaterial(item.optString("title"), item.optString("text"), item.optString("url"));
                    else if (which == 1) {
                        String url = item.optString("url");
                        if (AppData.isHttp(url)) webView.loadUrl(url);
                        else toast("这个素材没有原网页");
                    } else if (which == 2) copy(item.optString("text"), "素材");
                    else {
                        data.removeMaterial(index);
                        toast("已删除");
                    }
                })
                .setNegativeButton("返回", null)
                .show();
    }

    private void showHistory() {
        JSONArray arr = data.history();
        if (arr.length() == 0) {
            toast("还没有历史记录");
            return;
        }
        int n = Math.min(arr.length(), 80);
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", o.optString("url")), 42) + "\n" + AppData.compactHost(o.optString("url"));
        }
        new AlertDialog.Builder(this)
                .setTitle("最近浏览 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> {
                    JSONObject o = arr.optJSONObject(which);
                    if (o != null) webView.loadUrl(o.optString("url"));
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showDownloads() {
        JSONArray arr = data.downloads();
        if (arr.length() == 0) {
            toast("还没有下载记录");
            return;
        }
        String[] labels = new String[arr.length()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("name"), 40) + "\n" + fmt.format(new Date(o.optLong("time", 0)));
        }
        new AlertDialog.Builder(this)
                .setTitle("下载 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showDownloadActions(arr.optJSONObject(which)))
                .setPositiveButton("系统下载", (d, w) -> {
                    try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
                    catch (Exception e) { toast("请在文件管理器打开 Downloads/EnglishListening"); }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showDownloadActions(JSONObject item) {
        if (item == null) return;
        String mime = item.optString("mime", "");
        boolean audio = mime.startsWith("audio/") || AppData.isAudio(item.optString("name"));
        String[] actions = audio ? new String[]{"APP播放器", "打开文件", "重新下载", "复制来源"} : new String[]{"打开文件", "重新下载", "复制来源"};
        new AlertDialog.Builder(this)
                .setTitle(item.optString("name", "下载文件"))
                .setItems(actions, (d, which) -> {
                    String action = actions[which];
                    if ("APP播放器".equals(action)) openDownloaded(item, true);
                    else if ("打开文件".equals(action)) openDownloaded(item, false);
                    else if ("重新下载".equals(action)) startDownload(new PendingDownload(item.optString("url"), webView.getSettings().getUserAgentString(), null, mime));
                    else copy(item.optString("url"), "来源网址");
                })
                .show();
    }

    private void openDownloaded(JSONObject item, boolean play) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri uri = dm.getUriForDownloadedFile(item.optLong("id", -1));
        if (uri == null) {
            toast("文件可能还没下完或已被删除");
            return;
        }
        if (play) {
            audioPlayer.show(uri.toString(), item.optString("name", "音频"));
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            String mime = item.optString("mime", "*/*");
            if (mime.isEmpty()) mime = "*/*";
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            toast("没有应用可以打开这个文件");
        }
    }

    private void showLinkActions(String url) {
        boolean audio = AppData.isAudio(url);
        String[] actions = audio ? new String[]{"打开", "APP播放", "下载", "复制"} : new String[]{"打开", "下载", "复制"};
        new AlertDialog.Builder(this)
                .setTitle("链接操作")
                .setItems(actions, (d, which) -> {
                    String a = actions[which];
                    if ("打开".equals(a)) webView.loadUrl(url);
                    else if ("APP播放".equals(a)) audioPlayer.show(url, "网页音频");
                    else if ("下载".equals(a)) startDownload(new PendingDownload(url, webView.getSettings().getUserAgentString(), null, AppData.guessMime(url)));
                    else copy(url, "链接");
                })
                .show();
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
            long id = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            data.addDownload(id, name, d.url, mime);
            toast("开始下载：" + name);
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
        }
    }

    private void showMore(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("首页");
        menu.getMenu().add("继续上次");
        menu.getMenu().add("素材库");
        menu.getMenu().add("下载");
        menu.getMenu().add("历史");
        menu.getMenu().add("收藏夹");
        menu.getMenu().add("添加当前网站到首页");
        menu.getMenu().add("管理首页网站");
        menu.getMenu().add("刷新网页");
        menu.getMenu().add("系统浏览器打开");
        menu.getMenu().add("清除网页缓存");
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if ("首页".equals(t)) loadHome();
            else if ("继续上次".equals(t)) continueLast();
            else if ("素材库".equals(t)) showMaterials();
            else if ("下载".equals(t)) showDownloads();
            else if ("历史".equals(t)) showHistory();
            else if ("收藏夹".equals(t)) showFavorites();
            else if ("添加当前网站到首页".equals(t)) showAddSite();
            else if ("管理首页网站".equals(t)) showManageSites();
            else if ("刷新网页".equals(t)) webView.reload();
            else if ("系统浏览器打开".equals(t)) {
                String u = webView.getUrl();
                if (AppData.isHttp(u) && !u.startsWith("https://local.english-listening.app/")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }
                    catch (Exception ignored) {}
                } else toast("当前不是外部网页");
            } else {
                webView.clearCache(true);
                toast("缓存已清除");
            }
            return true;
        });
        menu.show();
    }

    private void showFavorites() {
        JSONArray arr = data.favorites();
        if (arr.length() == 0) {
            toast("还没有收藏");
            return;
        }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = o == null ? "" : AppData.ellipsize(o.optString("title", o.optString("url")), 45);
        }
        new AlertDialog.Builder(this)
                .setTitle("收藏夹")
                .setItems(labels, (d, which) -> {
                    JSONObject o = arr.optJSONObject(which);
                    if (o != null) webView.loadUrl(o.optString("url"));
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private String decode(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch (Exception e) { return value.replace("\\n", "\n").replace("\\\"", "\""); }
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        toast("已复制");
    }

    private Button button(String text, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && pendingDownload != null) {
            PendingDownload d = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enqueue(d);
            else toast("需要存储权限才能下载");
        }
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
        long elapsed = Math.max(0, (System.currentTimeMillis() - sessionStarted) / 1000);
        data.addStudySeconds(Math.min(elapsed, 3600));
        if (aiTutor != null) aiTutor.destroy();
        if (originalMedia != null) originalMedia.stopStatus();
        if (audioPlayer != null) audioPlayer.stop();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
