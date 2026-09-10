package com.englishlistening.browser;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class PracticalMainActivityV13 extends PracticalMainActivityV12 {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tuneCurrentWebView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View content = findViewById(android.R.id.content);
        if (content != null) content.post(this::tuneCurrentWebView);
    }

    private void tuneCurrentWebView() {
        View content = findViewById(android.R.id.content);
        if (content != null) tuneRecursive(content);
    }

    private void tuneRecursive(View view) {
        if (view instanceof WebView) {
            tuneWebView((WebView) view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tuneRecursive(group.getChildAt(i));
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void tuneWebView(WebView webView) {
        try {
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setLoadsImagesAutomatically(true);
            s.setBlockNetworkImage(false);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccess(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            s.setDefaultTextEncodingName("UTF-8");
            s.setOffscreenPreRaster(true);

            String ua = s.getUserAgentString();
            if (ua != null && ua.contains("Chrome/")) {
                String chromeUa = ua.replace("; wv", "").replace("Version/4.0 ", "");
                if (!chromeUa.equals(ua)) s.setUserAgentString(chromeUa);
            }

            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setNetworkAvailable(true);
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        } catch (Exception ignored) {
        }
    }
}
