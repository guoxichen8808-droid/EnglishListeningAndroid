package com.englishlistening.browser;

import android.app.Activity;
import android.content.Intent;
import android.webkit.WebView;

import org.json.JSONArray;

public class RealAiTutor {
    private final Activity activity;
    private final WebView webView;

    public RealAiTutor(Activity activity, WebView webView, AppData data) {
        this.activity = activity;
        this.webView = webView;
    }

    public void openFromCurrentPage() {
        String title = AppData.safeTitle(webView.getTitle(), "网页素材");
        String url = webView.getUrl() == null ? "" : webView.getUrl();
        if (!AppData.isHttp(url)) {
            openActivity(title, "", url);
            return;
        }
        String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,9000);})()";
        webView.evaluateJavascript(js, value -> openActivity(title, decode(value), url));
    }

    public void openMaterial(String title, String text, String url) {
        openActivity(AppData.safeTitle(title, "素材库"), text == null ? "" : text, url == null ? "" : url);
    }

    private void openActivity(String title, String text, String url) {
        Intent intent = new Intent(activity, AiTutorActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("material", text);
        intent.putExtra("url", url);
        activity.startActivity(intent);
    }

    private String decode(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch (Exception e) { return value.replace("\\n", "\n").replace("\\\"", "\""); }
    }

    public void destroy() {}
}
