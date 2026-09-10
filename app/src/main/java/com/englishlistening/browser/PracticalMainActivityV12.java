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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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
import java.util.List;
import java.util.Locale;

public class PracticalMainActivityV12 extends Activity {
    private static final int STORAGE_PERMISSION_CODE = 1201;
    private static final int FILE_CHOOSER_CODE = 1202;

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private AppData data;
    private AudioPlayerDialog audioPlayer;
    private OriginalMediaController originalMedia;
    private AdBlocker adBlocker;
    private MediaSniffer mediaSniffer;
    private PendingDownload pendingDownload;
    private ValueCallback<Uri[]> filePathCallback;
    private long sessionStarted;

    private static class PendingDownload {
        String url, userAgent, contentDisposition, mimeType;
        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url=url; this.userAgent=userAgent; this.contentDisposition=contentDisposition; this.mimeType=mimeType;
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247,248,250));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        data = new AppData(this);
        adBlocker = new AdBlocker(this);
        mediaSniffer = new MediaSniffer();
        buildUi();
        configureWebView();
        audioPlayer = new AudioPlayerDialog(this);
        originalMedia = new OriginalMediaController(this, webView);
        sessionStarted = System.currentTimeMillis();

        String openUrl = getIntent().getStringExtra("openUrl");
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            // restored
        } else if (AppData.isHttp(openUrl)) {
            webView.loadUrl(openUrl);
        } else {
            loadHome();
        }
        if (adBlocker.ruleCount() < 100) adBlocker.updateRules(null);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8),dp(5),dp(8),dp(5));
        top.setBackgroundColor(Color.rgb(247,248,250));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("输入网址或搜索内容");
        addressBar.setTextSize(14);
        top.addView(addressBar,new LinearLayout.LayoutParams(0,dp(42),1f));

        Button go=button("前往",12);
        go.setOnClickListener(v->navigate());
        top.addView(go,new LinearLayout.LayoutParams(dp(58),dp(42)));

        Button more=button("⋮",23);
        more.setOnClickListener(this::showMore);
        top.addView(more,new LinearLayout.LayoutParams(dp(46),dp(42)));
        addressBar.setOnEditorActionListener((v,a,e)->{navigate();return true;});
        root.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(2)));

        webView=new WebView(this);
        root.addView(webView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setBackgroundColor(Color.rgb(248,249,251));
        String[] names={"前进","原声精听","存素材","AI老师"};
        for(int i=0;i<names.length;i++){
            Button b=button(names[i],i==1?11:12);
            final int x=i;
            b.setOnClickListener(v->{
                if(x==0){ if(webView.canGoForward()) webView.goForward(); else toast("没有可前进页面"); }
                else if(x==1) originalMedia.show();
                else if(x==2) saveCurrentMaterial();
                else openAiTutor();
            });
            bottom.addView(b,new LinearLayout.LayoutParams(0,dp(58),1f));
        }
        root.addView(bottom,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(60)));
        setContentView(root);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView(){
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setSupportZoom(true); s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); s.setJavaScriptCanOpenWindowsAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);

        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){ progressBar.setProgress(p); progressBar.setVisibility(p>=100?View.INVISIBLE:View.VISIBLE); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params){
                if(filePathCallback!=null) filePathCallback.onReceiveValue(null);
                filePathCallback=cb;
                try { startActivityForResult(params.createIntent(),FILE_CHOOSER_CODE); return true; }
                catch(Exception e){ filePathCallback=null; toast("没有可用文件选择器"); return false; }
            }
        });

        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){ return handleUrl(r.getUrl().toString()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){ return handleUrl(u); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                if(r!=null && r.getUrl()!=null) mediaSniffer.observe(r.getUrl().toString());
                return adBlocker.intercept(r);
            }
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap f){ mediaSniffer.clear(); super.onPageStarted(v,u,f); }
            @Override public void onPageFinished(WebView v,String u){
                super.onPageFinished(v,u);
                boolean local=u!=null && u.startsWith("https://local.english-listening.app/");
                addressBar.setText(local?"":(u==null?"":u));
                adBlocker.injectCosmeticRules(v);
                if(AppData.isHttp(u) && !local && !AppData.isAiUrl(u)){ data.addHistory(u,v.getTitle()); data.setLastLearning(u,v.getTitle()); }
            }
        });

        webView.setDownloadListener(new DownloadListener(){
            @Override public void onDownloadStart(String u,String ua,String cd,String mime,long len){
                if(!AppData.isHttp(u)){toast("不是普通 HTTP/HTTPS 文件，不能直接下载");return;}
                mediaSniffer.observe(u); startDownload(new PendingDownload(u,ua,cd,mime));
            }
        });

        webView.setOnLongClickListener(v->{
            WebView.HitTestResult h=webView.getHitTestResult();
            if(h==null||h.getExtra()==null)return false;
            int t=h.getType();
            if(t==WebView.HitTestResult.SRC_ANCHOR_TYPE||t==WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE||t==WebView.HitTestResult.IMAGE_TYPE){showLinkActions(h.getExtra());return true;}
            return false;
        });
    }

    private boolean handleUrl(String u){
        if(u==null)return false;
        if(u.startsWith("app://")){
            if("app://addsite".equals(u))showAddSite();
            else if("app://continue".equals(u))continueLast();
            return true;
        }
        if(AppData.isHttp(u))return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){toast("无法打开此链接");}
        return true;
    }

    private void loadHome(){
        JSONArray shortcuts=data.shortcuts();
        StringBuilder mine=new StringBuilder();
        for(int i=0;i<shortcuts.length();i++){
            JSONObject o=shortcuts.optJSONObject(i); if(o==null)continue;
            String name=AppData.html(o.optString("name",AppData.compactHost(o.optString("url"))));
            String url=AppData.html(o.optString("url"));
            mine.append("<a class='c' href='").append(url).append("'><b>").append(name).append("</b><span>")
                    .append(AppData.html(AppData.compactHost(o.optString("url")))).append("</span></a>");
        }
        mine.append("<a class='c plus' href='app://addsite'><b>＋</b><span>添加常用网站</span></a>");
        String html="<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"+
                "<style>body{font-family:sans-serif;background:#f5f7fb;color:#111827;margin:0}.w{padding:16px}.h{background:#1d4ed8;color:white;padding:18px;border-radius:18px}.h h1{margin:0 0 5px;font-size:23px}.h p{margin:0;font-size:13px;opacity:.92}.g{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.c{background:white;border:1px solid #e5e7eb;border-radius:15px;padding:16px;text-decoration:none;color:#111827;min-height:90px}.c b{display:block;margin-bottom:7px;font-size:17px}.c span{font-size:12px;color:#667085}.plus b{font-size:30px;line-height:24px;color:#1d4ed8}.sec{font-size:15px;margin:18px 2px 4px;color:#475467}</style></head><body><div class='w'>"+
                "<div class='h'><h1>英语听力</h1><p>网站原声 · 素材 · AI练习</p></div>"+
                "<div class='g'><a class='c' href='https://elllo.org/'><b>ELLLO</b><span>多口音与原文</span></a>"+
                "<a class='c' href='https://www.bbc.co.uk/learningenglish/'><b>BBC Learning English</b><span>真实英式英语</span></a>"+
                "<a class='c' href='https://learningenglish.voanews.com/'><b>VOA Learning English</b><span>清晰美音</span></a>"+
                "<a class='c' href='https://youglish.com/'><b>YouGlish</b><span>真人视频查发音</span></a></div>"+
                "<div class='sec'>我的网站</div><div class='g'>"+mine+"</div></div></body></html>";
        webView.loadDataWithBaseURL("https://local.english-listening.app/",html,"text/html","UTF-8",null);
    }

    private void navigate(){
        String in=addressBar.getText().toString().trim(); if(in.isEmpty())return;
        String u;
        if(in.startsWith("http://")||in.startsWith("https://"))u=in;
        else if(in.matches("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*$"))u="https://"+in;
        else u="https://www.google.com/search?q="+Uri.encode(in);
        webView.loadUrl(u);
    }

    private void continueLast(){ String u=data.lastLearningUrl(); if(AppData.isHttp(u))webView.loadUrl(u);else toast("还没有上次学习记录"); }

    private void openAiTutor(){
        String u=webView.getUrl();
        if(!AppData.isHttp(u)||u.startsWith("https://local.english-listening.app/")){
            Intent i=new Intent(this,AiTutorActivityV12.class); startActivity(i); return;
        }
        String js="(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,9000);})()";
        webView.evaluateJavascript(js,v->{
            Intent i=new Intent(this,AiTutorActivityV12.class);
            i.putExtra("material",decode(v)); i.putExtra("title",webView.getTitle()); i.putExtra("url",u); startActivity(i);
        });
    }

    private void saveCurrentMaterial(){
        String u=webView.getUrl();
        if(!AppData.isHttp(u)||u.startsWith("https://local.english-listening.app/")){toast("请先打开学习网页");return;}
        String js="(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,10000);})()";
        webView.evaluateJavascript(js,v->{String text=decode(v).trim();if(text.isEmpty()){toast("没有提取到可保存文字");return;}
            EditText note=new EditText(this);note.setHint("可选备注");
            new AlertDialog.Builder(this).setTitle("保存素材").setView(note)
                    .setPositiveButton("保存",(d,w)->{data.addMaterial(webView.getTitle(),text,u,note.getText().toString());toast("已保存到素材库");})
                    .setNeutralButton("直接AI练",(d,w)->{Intent i=new Intent(this,AiTutorActivityV12.class);i.putExtra("material",text);i.putExtra("title",webView.getTitle());i.putExtra("url",u);startActivity(i);})
                    .setNegativeButton("取消",null).show();});
    }

    private void showMore(View anchor){
        PopupMenu m=new PopupMenu(this,anchor);
        m.getMenu().add("首页");m.getMenu().add("继续上次");m.getMenu().add("素材库");m.getMenu().add("下载");m.getMenu().add("历史记录");m.getMenu().add("收藏夹");
        m.getMenu().add("媒体嗅探（AIX模式）");
        m.getMenu().add(adBlocker.isEnabled()?"广告屏蔽：已开启":"广告屏蔽：已关闭");m.getMenu().add("更新广告规则");
        m.getMenu().add("添加当前网站到首页");m.getMenu().add("管理首页网站");m.getMenu().add("刷新网页");m.getMenu().add("系统浏览器打开");m.getMenu().add("清除网页缓存");
        m.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();
            if("首页".equals(t))loadHome(); else if("继续上次".equals(t))continueLast(); else if("素材库".equals(t))showMaterials(); else if("下载".equals(t))showDownloads();
            else if("历史记录".equals(t))startActivity(new Intent(this,HistoryActivity.class)); else if("收藏夹".equals(t))showFavorites();
            else if(t.startsWith("广告屏蔽：")){adBlocker.setEnabled(!adBlocker.isEnabled());toast(adBlocker.isEnabled()?"广告屏蔽已开启":"广告屏蔽已关闭");webView.reload();}
            else if("更新广告规则".equals(t)){toast("正在更新广告规则…");adBlocker.updateRules(this::toast);}
            else if("媒体嗅探（AIX模式）".equals(t))showMediaSniffer(); else if("添加当前网站到首页".equals(t))showAddSite(); else if("管理首页网站".equals(t))showManageSites();
            else if("刷新网页".equals(t))webView.reload(); else if("系统浏览器打开".equals(t))openExternal(webView.getUrl()); else {webView.clearCache(true);toast("缓存已清除");}
            return true;});m.show();
    }

    private void showMediaSniffer(){
        mediaSniffer.scan(webView,items->{
            if(items==null||items.isEmpty()){toast("当前页面暂未嗅探到可直接下载的媒体/文件");return;}
            String[] labels=new String[items.size()];for(int i=0;i<items.size();i++)labels[i]=items.get(i).label();
            new AlertDialog.Builder(this).setTitle("媒体嗅探 · "+items.size()+" 项").setItems(labels,(d,w)->showMediaActions(items.get(w)))
                    .setNeutralButton("重新扫描",(d,w)->showMediaSniffer()).setNegativeButton("关闭",null).show();
        });
    }

    private void showMediaActions(MediaSniffer.Item item){
        boolean audio="音频".equals(item.type); boolean hls="HLS".equals(item.type);
        String[] actions=hls?new String[]{"打开","复制链接"}:(audio?new String[]{"APP播放","下载","打开","复制链接"}:new String[]{"下载","打开","复制链接"});
        new AlertDialog.Builder(this).setTitle(item.type).setItems(actions,(d,w)->{String a=actions[w];
            if("APP播放".equals(a))audioPlayer.show(item.url,"嗅探音频"); else if("下载".equals(a))startDownload(new PendingDownload(item.url,webView.getSettings().getUserAgentString(),null,AppData.guessMime(item.url)));
            else if("打开".equals(a))webView.loadUrl(item.url); else copy(item.url,"媒体链接");}).show();
    }

    private void showAddSite(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
        EditText name=new EditText(this);name.setHint("网站名称，例如 TED");name.setSingleLine(true);EditText url=new EditText(this);url.setHint("网址");url.setSingleLine(true);
        box.addView(name,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));box.addView(url,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        String cur=webView.getUrl();if(AppData.isHttp(cur)&&!cur.startsWith("https://local.english-listening.app/")){name.setText(AppData.safeTitle(webView.getTitle(),AppData.compactHost(cur)));url.setText(cur);}
        new AlertDialog.Builder(this).setTitle("添加到首页").setView(box).setPositiveButton("添加",(d,w)->{String n=name.getText().toString().trim();String u=url.getText().toString().trim();if(!u.startsWith("http://")&&!u.startsWith("https://"))u="https://"+u;if(n.isEmpty())n=AppData.compactHost(u);if(data.addShortcut(n,u)){toast("已添加到首页");loadHome();}else toast("网址无效、已存在或首页已满");}).setNegativeButton("取消",null).show();
    }

    private void showManageSites(){
        JSONArray arr=data.shortcuts();if(arr.length()==0){toast("还没有自定义网站");return;}String[] labels=new String[arr.length()];for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);labels[i]=o==null?"":o.optString("name")+"\n"+AppData.compactHost(o.optString("url"));}
        new AlertDialog.Builder(this).setTitle("管理首页网站").setItems(labels,(d,w)->{JSONObject o=arr.optJSONObject(w);if(o==null)return;new AlertDialog.Builder(this).setTitle(o.optString("name")).setItems(new String[]{"打开","删除"},(x,a)->{if(a==0)webView.loadUrl(o.optString("url"));else{data.removeShortcut(w);toast("已删除");loadHome();}}).show();}).setNegativeButton("关闭",null).show();
    }

    private void showMaterials(){
        JSONArray arr=data.materials();if(arr.length()==0){toast("素材库还是空的");return;}String[] labels=new String[arr.length()];for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);labels[i]=o==null?"":AppData.ellipsize(o.optString("title","素材"),42)+"\n"+AppData.ellipsize(o.optString("text",""),58);}
        new AlertDialog.Builder(this).setTitle("素材库 · "+arr.length()).setItems(labels,(d,w)->{JSONObject o=arr.optJSONObject(w);if(o==null)return;new AlertDialog.Builder(this).setTitle(AppData.ellipsize(o.optString("title"),55)).setItems(new String[]{"AI老师直接练","打开原网页","复制文字","删除"},(x,a)->{if(a==0){Intent i=new Intent(this,AiTutorActivityV12.class);i.putExtra("material",o.optString("text"));i.putExtra("title",o.optString("title"));i.putExtra("url",o.optString("url"));startActivity(i);}else if(a==1){String u=o.optString("url");if(AppData.isHttp(u))webView.loadUrl(u);else toast("没有原网页");}else if(a==2)copy(o.optString("text"),"素材");else{data.removeMaterial(w);toast("已删除");}}).show();}).setNegativeButton("关闭",null).show();
    }

    private void showFavorites(){
        JSONArray arr=data.favorites();if(arr.length()==0){toast("还没有收藏");return;}String[] labels=new String[arr.length()];for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);labels[i]=o==null?"":AppData.ellipsize(o.optString("title",o.optString("url")),48);}
        new AlertDialog.Builder(this).setTitle("收藏夹").setItems(labels,(d,w)->{JSONObject o=arr.optJSONObject(w);if(o!=null)webView.loadUrl(o.optString("url"));}).setNegativeButton("关闭",null).show();
    }

    private void addFavorite(){String u=webView.getUrl();if(!AppData.isHttp(u)){toast("当前页面不能收藏");return;}if(data.isFavorite(u)){toast("已经收藏过了");return;}data.addFavorite(u,webView.getTitle(),"");toast("已收藏");}

    private void showDownloads(){
        JSONArray arr=data.downloads();if(arr.length()==0){toast("还没有下载记录");return;}SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault());String[] labels=new String[arr.length()];for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);labels[i]=o==null?"":AppData.ellipsize(o.optString("name"),44)+"\n"+f.format(new Date(o.optLong("time",0)));}
        new AlertDialog.Builder(this).setTitle("下载 · "+arr.length()).setItems(labels,(d,w)->showDownloadActions(arr.optJSONObject(w))).setPositiveButton("系统下载",(d,w)->{try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception e){toast("请到 Downloads/EnglishListening 查看");}}).setNegativeButton("关闭",null).show();
    }

    private void showDownloadActions(JSONObject o){if(o==null)return;String mime=o.optString("mime","");boolean audio=mime.startsWith("audio/")||AppData.isAudio(o.optString("name"));String[] a=audio?new String[]{"APP播放器","打开文件","重新下载","复制来源"}:new String[]{"打开文件","重新下载","复制来源"};new AlertDialog.Builder(this).setTitle(o.optString("name","下载文件")).setItems(a,(d,w)->{String x=a[w];if("APP播放器".equals(x))openDownloaded(o,true);else if("打开文件".equals(x))openDownloaded(o,false);else if("重新下载".equals(x))startDownload(new PendingDownload(o.optString("url"),webView.getSettings().getUserAgentString(),null,mime));else copy(o.optString("url"),"来源");}).show();}

    private void openDownloaded(JSONObject o,boolean play){DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);Uri uri=dm.getUriForDownloadedFile(o.optLong("id",-1));if(uri==null){toast("文件未完成或已删除");return;}if(play){audioPlayer.show(uri.toString(),o.optString("name","音频"));return;}try{Intent i=new Intent(Intent.ACTION_VIEW);String m=o.optString("mime","*/*");if(m.isEmpty())m="*/*";i.setDataAndType(uri,m);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("没有应用可以打开此文件");}}

    private void showLinkActions(String u){boolean audio=AppData.isAudio(u);String[] a=audio?new String[]{"打开","APP播放","下载","复制"}:new String[]{"打开","下载","复制"};new AlertDialog.Builder(this).setTitle("链接操作").setItems(a,(d,w)->{String x=a[w];if("打开".equals(x))webView.loadUrl(u);else if("APP播放".equals(x))audioPlayer.show(u,"网页音频");else if("下载".equals(x))startDownload(new PendingDownload(u,webView.getSettings().getUserAgentString(),null,AppData.guessMime(u)));else copy(u,"链接");}).show();}

    private void startDownload(PendingDownload d){if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.P&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){pendingDownload=d;requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_PERMISSION_CODE);return;}enqueue(d);}
    private void enqueue(PendingDownload d){try{String name=URLUtil.guessFileName(d.url,d.contentDisposition,d.mimeType).replaceAll("[\\\\/:*?\"<>|]","_");String mime=d.mimeType==null||d.mimeType.isEmpty()?AppData.guessMime(d.url):d.mimeType;DownloadManager.Request r=new DownloadManager.Request(Uri.parse(d.url));r.setTitle(name);r.setDescription("EnglishListening 下载");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setMimeType(mime);if(d.userAgent!=null&&!d.userAgent.isEmpty())r.addRequestHeader("User-Agent",d.userAgent);String c=CookieManager.getInstance().getCookie(d.url);if(c!=null&&!c.isEmpty())r.addRequestHeader("Cookie",c);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"EnglishListening/"+name);long id=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);data.addDownload(id,name,d.url,mime);toast("开始下载："+name);}catch(Exception e){toast("下载失败："+e.getMessage());}}

    private void openExternal(String u){if(!AppData.isHttp(u)){toast("当前不是外部网页");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){toast("无法打开系统浏览器");}}
    private void copy(String text,String label){ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText(label,text==null?"":text));toast("已复制");}
    private String decode(String v){if(v==null||"null".equals(v))return"";try{return new JSONArray("["+v+"]").getString(0);}catch(Exception e){return v.replace("\\n","\n").replace("\\\"","\"");}}
    private Button button(String t,int s){Button b=new Button(this);b.setText(t);b.setTextSize(s);b.setAllCaps(false);b.setMinWidth(0);b.setMinHeight(0);b.setPadding(dp(2),0,dp(2),0);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==STORAGE_PERMISSION_CODE&&pendingDownload!=null){PendingDownload d=pendingDownload;pendingDownload=null;if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)enqueue(d);else toast("需要存储权限才能下载");}}
    @Override protected void onActivityResult(int r,int c,Intent i){super.onActivityResult(r,c,i);if(r==FILE_CHOOSER_CODE){if(filePathCallback!=null){filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c,i));filePathCallback=null;}}}
    @Override protected void onSaveInstanceState(Bundle b){webView.saveState(b);super.onSaveInstanceState(b);}
    @Override public boolean onKeyDown(int k,KeyEvent e){if(k==KeyEvent.KEYCODE_BACK&&webView.canGoBack()){webView.goBack();return true;}return super.onKeyDown(k,e);}
    @Override protected void onDestroy(){long elapsed=Math.max(0,(System.currentTimeMillis()-sessionStarted)/1000);data.addStudySeconds(Math.min(elapsed,3600));if(adBlocker!=null)adBlocker.destroy();if(originalMedia!=null)originalMedia.stopStatus();if(audioPlayer!=null)audioPlayer.stop();if(webView!=null){webView.stopLoading();webView.destroy();}super.onDestroy();}
}
