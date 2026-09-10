package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.Locale;

public class OriginalMediaController {
    private final Activity activity;
    private final WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable statusTask;

    public OriginalMediaController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void show() {
        webView.evaluateJavascript("(function(){return document.querySelectorAll('audio,video').length;})()", value -> {
            int count = 0;
            try { count = Integer.parseInt(value.replace("\"", "").trim()); } catch (Exception ignored) {}
            if (count <= 0) {
                new AlertDialog.Builder(activity)
                        .setTitle("原声精听")
                        .setMessage("当前网页没有检测到可直接控制的 audio/video。这个网站可能使用内嵌播放器或跨域播放器。\n\n这种情况请直接用网页自己的播放器，或下载音频后使用 APP 播放器。")
                        .setPositiveButton("知道了", null).show();
                return;
            }
            showControls();
        });
    }

    private void showControls() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), dp(8));

        TextView status = new TextView(activity);
        status.setText("正在连接网页原声…");
        status.setTextSize(14);
        box.addView(status);

        LinearLayout r1 = row();
        Button back = button("-5秒");
        Button play = button("播放/暂停");
        Button forward = button("+5秒");
        r1.addView(back, cell()); r1.addView(play, cell()); r1.addView(forward, cell());
        box.addView(r1);

        LinearLayout r2 = row();
        Button s075 = button("0.75×");
        Button s085 = button("0.85×");
        Button s100 = button("1.0×");
        Button s115 = button("1.15×");
        r2.addView(s075, cell()); r2.addView(s085, cell()); r2.addView(s100, cell()); r2.addView(s115, cell());
        box.addView(r2);

        LinearLayout r3 = row();
        Button a = button("设A点");
        Button b = button("设B点");
        Button clear = button("清AB");
        r3.addView(a, cell()); r3.addView(b, cell()); r3.addView(clear, cell());
        box.addView(r3);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("原声精听 · 网页真人音频")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnDismissListener(d -> stopStatus());
        dialog.show();

        initMedia();
        startStatus(status);
        back.setOnClickListener(v -> js("var m=window.__elMedia;if(m){m.currentTime=Math.max(0,m.currentTime-5);}"));
        forward.setOnClickListener(v -> js("var m=window.__elMedia;if(m){m.currentTime=Math.min(m.duration||1e9,m.currentTime+5);}"));
        play.setOnClickListener(v -> js("var m=window.__elMedia;if(m){if(m.paused){m.play();}else{m.pause();}}"));
        s075.setOnClickListener(v -> setSpeed(0.75f));
        s085.setOnClickListener(v -> setSpeed(0.85f));
        s100.setOnClickListener(v -> setSpeed(1.0f));
        s115.setOnClickListener(v -> setSpeed(1.15f));
        a.setOnClickListener(v -> webView.evaluateJavascript("(function(){var m=window.__elMedia;if(!m)return '';window.__elA=m.currentTime;return m.currentTime.toFixed(1);})()", x -> toast("A点 " + clean(x) + " 秒")));
        b.setOnClickListener(v -> webView.evaluateJavascript("(function(){var m=window.__elMedia;if(!m)return '';window.__elB=m.currentTime;if(window.__elABTimer)clearInterval(window.__elABTimer);window.__elABTimer=setInterval(function(){var mm=window.__elMedia;if(mm&&typeof window.__elA==='number'&&typeof window.__elB==='number'&&window.__elB>window.__elA&&mm.currentTime>=window.__elB){mm.currentTime=window.__elA;mm.play();}},120);return m.currentTime.toFixed(1);})()", x -> toast("B点 " + clean(x) + " 秒，AB循环已开启")));
        clear.setOnClickListener(v -> { js("if(window.__elABTimer)clearInterval(window.__elABTimer);window.__elABTimer=null;window.__elA=null;window.__elB=null;"); toast("AB循环已清除"); });
    }

    private void initMedia() {
        js("(function(){var list=[].slice.call(document.querySelectorAll('audio,video'));if(!list.length)return;var m=list.find(function(x){return !x.paused&&x.readyState>0;})||list.find(function(x){return x.duration&&isFinite(x.duration);})||list[0];window.__elMedia=m;})()");
    }

    private void setSpeed(float speed) {
        js("var m=window.__elMedia;if(m){m.playbackRate=" + speed + ";m.defaultPlaybackRate=" + speed + ";}");
        toast("原声速度 " + speed + "×");
    }

    private void startStatus(TextView status) {
        stopStatus();
        statusTask = new Runnable() {
            @Override public void run() {
                webView.evaluateJavascript("(function(){var m=window.__elMedia;if(!m)return '';var a=(typeof window.__elA==='number')?window.__elA:null;var b=(typeof window.__elB==='number')?window.__elB:null;return JSON.stringify({t:m.currentTime||0,d:(isFinite(m.duration)?m.duration:0),p:!m.paused,r:m.playbackRate||1,a:a,b:b});})()", value -> {
                    try {
                        String decoded = new JSONArray("[" + value + "]").getString(0);
                        org.json.JSONObject o = new org.json.JSONObject(decoded);
                        String text = format(o.optDouble("t", 0)) + " / " + format(o.optDouble("d", 0)) + "   " + String.format(Locale.US, "%.2f×", o.optDouble("r", 1));
                        if (!o.isNull("a") && !o.isNull("b")) text += "   AB " + format(o.optDouble("a",0)) + "–" + format(o.optDouble("b",0));
                        status.setText(text + (o.optBoolean("p", false) ? "   播放中" : "   已暂停"));
                    } catch (Exception ignored) {}
                });
                handler.postDelayed(this, 400);
            }
        };
        handler.post(statusTask);
    }

    public void stopStatus() {
        if (statusTask != null) handler.removeCallbacks(statusTask);
        statusTask = null;
    }

    private void js(String code) { webView.evaluateJavascript("(function(){" + code + "})()", null); }
    private String clean(String s) { return s == null ? "" : s.replace("\"", ""); }
    private String format(double sec) {
        int total = (int)Math.max(0, Math.round(sec));
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60);
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(activity); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private Button button(String text) { Button b = new Button(activity); b.setText(text); b.setTextSize(11); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0); return b; }
    private LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(0, dp(44), 1f); }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(activity, s, Toast.LENGTH_SHORT).show(); }
}
