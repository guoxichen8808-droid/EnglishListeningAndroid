package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class OriginalMediaController {
    private final Activity activity;
    private final WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable statusTask;
    private PopupWindow popup;
    private MediaProgressView progressView;
    private TextView statusView;
    private Button playButton;
    private Button speedButton;
    private Button loopButton;

    public OriginalMediaController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void show() {
        webView.evaluateJavascript(
                "(function(){var l=[].slice.call(document.querySelectorAll('audio,video'));var p=l.filter(function(x){return !x.paused&&!x.ended&&x.readyState>0;}).length;var f=[].slice.call(document.querySelectorAll('iframe')).filter(function(x){var s=(x.src||'').toLowerCase();return s.indexOf('youtube')>=0||s.indexOf('vimeo')>=0||s.indexOf('video')>=0;}).length;return JSON.stringify({n:l.length,p:p,f:f});})()",
                value -> {
                    try {
                        String decoded = new JSONArray("[" + value + "]").getString(0);
                        JSONObject o = new JSONObject(decoded);
                        int count = o.optInt("n", 0);
                        int playing = o.optInt("p", 0);
                        int iframeVideo = o.optInt("f", 0);
                        if (count <= 0) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("原声精听")
                                    .setMessage("当前网页没有检测到可直接控制的 audio/video。若是 YouTube/Vimeo 等跨域播放器，APP 无法直接读取它的进度。")
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        if (iframeVideo > 0 && playing == 0) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("先播放要练的原声")
                                    .setMessage("页面里检测到视频播放器和其他音频。请先在网页中点播放你真正要练的音频/视频，再打开“原声精听”，避免绑定到隐藏音频产生双声。")
                                    .setPositiveButton("继续打开", (d, w) -> showFloatingControls())
                                    .setNegativeButton("取消", null)
                                    .show();
                        } else {
                            showFloatingControls();
                        }
                    } catch (Exception e) {
                        showFloatingControls();
                    }
                });
    }

    private void showFloatingControls() {
        dismissPopup();
        installMediaBridge();

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(248, 255, 255, 255));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(215, 219, 226));
        box.setBackground(bg);
        box.setElevation(dp(10));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        statusView = new TextView(activity);
        statusView.setText("连接当前网页原声…");
        statusView.setTextSize(12);
        statusView.setTextColor(Color.rgb(55, 65, 81));
        statusView.setSingleLine(true);
        top.addView(statusView, new LinearLayout.LayoutParams(0, dp(28), 1f));

        Button close = compactButton("×", 18);
        close.setOnClickListener(v -> dismissPopup());
        top.addView(close, new LinearLayout.LayoutParams(dp(38), dp(30)));
        box.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        progressView = new MediaProgressView(activity);
        progressView.setSeekListener(seconds -> js(
                "var m=window.__elMedia;if(m&&isFinite(m.duration)&&m.duration>0){m.currentTime=Math.max(0,Math.min(m.duration," + seconds + "));}"));
        box.addView(progressView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Button back = compactButton("-5", 11);
        playButton = compactButton("▶", 14);
        Button forward = compactButton("+5", 11);
        speedButton = compactButton("1.0×", 11);
        Button aButton = compactButton("A", 12);
        Button bButton = compactButton("B", 12);
        loopButton = compactButton("循环", 10);
        Button clearButton = compactButton("清", 10);

        controls.addView(back, fixed(dp(42)));
        controls.addView(playButton, fixed(dp(44)));
        controls.addView(forward, fixed(dp(42)));
        controls.addView(speedButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        controls.addView(aButton, fixed(dp(40)));
        controls.addView(bButton, fixed(dp(40)));
        controls.addView(loopButton, fixed(dp(58)));
        controls.addView(clearButton, fixed(dp(42)));
        box.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        popup = new PopupWindow(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setTouchable(true);
        popup.setOutsideTouchable(false);
        popup.setClippingEnabled(true);
        popup.setElevation(dp(12));
        popup.setOnDismissListener(this::stopStatus);

        back.setOnClickListener(v -> js("var m=window.__elMedia;if(m){m.currentTime=Math.max(0,(m.currentTime||0)-5);}"));
        forward.setOnClickListener(v -> js("var m=window.__elMedia;if(m){m.currentTime=Math.min((isFinite(m.duration)?m.duration:1e9),(m.currentTime||0)+5);}"));
        playButton.setOnClickListener(v -> js(
                "var m=window.__elMedia;if(m){window.__elActivate&&window.__elActivate(m);if(m.paused){var p=m.play();if(p&&p.catch)p.catch(function(){});}else{m.pause();}}"));
        speedButton.setOnClickListener(this::showSpeedMenu);

        aButton.setOnClickListener(v -> webView.evaluateJavascript(
                "(function(){var m=window.__elMedia;if(!m||!isFinite(m.duration)||m.duration<=0)return 'NOMEDIA';window.__elA=m.currentTime||0;if(typeof window.__elB==='number'&&window.__elB<=window.__elA){window.__elB=null;window.__elLoop=false;}return window.__elA.toFixed(1);})()",
                x -> {
                    String s = clean(x);
                    if ("NOMEDIA".equals(s)) toast("当前媒体还没加载好");
                    else toast("A点 " + s + " 秒");
                    refreshNow();
                }));

        bButton.setOnClickListener(v -> webView.evaluateJavascript(
                "(function(){var m=window.__elMedia;if(!m||!isFinite(m.duration)||m.duration<=0)return 'NOMEDIA';if(typeof window.__elA!=='number')return 'NOA';var b=m.currentTime||0;if(b<=window.__elA)return 'BAD';window.__elB=b;window.__elLoop=true;return b.toFixed(1);})()",
                x -> {
                    String s = clean(x);
                    if ("NOMEDIA".equals(s)) toast("当前媒体还没加载好");
                    else if ("NOA".equals(s)) toast("请先设置 A 点");
                    else if ("BAD".equals(s)) toast("B 点必须在 A 点之后");
                    else toast("B点 " + s + " 秒，AB循环已开启");
                    refreshNow();
                }));

        loopButton.setOnClickListener(v -> webView.evaluateJavascript(
                "(function(){var a=window.__elA,b=window.__elB;if(typeof a!=='number'||typeof b!=='number'||b<=a)return 'NOAB';window.__elLoop=!window.__elLoop;if(window.__elLoop){var m=window.__elMedia;if(m&&(m.currentTime<a||m.currentTime>=b))m.currentTime=a;}return window.__elLoop?'ON':'OFF';})()",
                x -> {
                    String s = clean(x);
                    if ("NOAB".equals(s)) toast("请先设置 A 点和 B 点");
                    else toast("ON".equals(s) ? "AB循环已开启" : "AB循环已关闭");
                    refreshNow();
                }));

        clearButton.setOnClickListener(v -> {
            js("window.__elA=null;window.__elB=null;window.__elLoop=false;");
            toast("AB 已清除");
            refreshNow();
        });

        popup.showAtLocation(webView, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(62));
        startStatus();
    }

    private void installMediaBridge() {
        js("var list=function(){return [].slice.call(document.querySelectorAll('audio,video'));};"
                + "window.__elActivate=function(m){if(!m)return;window.__elMedia=m;list().forEach(function(x){if(x!==m&&!x.paused){try{x.pause();}catch(e){}}});};"
                + "var bind=function(m){if(!m||m.__elBound)return;m.__elBound=true;"
                + "m.addEventListener('play',function(){window.__elActivate(m);},true);"
                + "m.addEventListener('playing',function(){window.__elActivate(m);},true);"
                + "m.addEventListener('loadedmetadata',function(){if(!window.__elMedia)window.__elMedia=m;},true);};"
                + "list().forEach(bind);"
                + "if(!window.__elMediaObserver){window.__elMediaObserver=new MutationObserver(function(){list().forEach(bind);});window.__elMediaObserver.observe(document.documentElement,{childList:true,subtree:true});}"
                + "var l=list();var active=l.find(function(x){return !x.paused&&!x.ended&&x.readyState>0;})"
                + "||l.find(function(x){return (x.currentTime||0)>0&&isFinite(x.duration)&&x.duration>0;})"
                + "||l.find(function(x){return x.tagName==='VIDEO'&&isFinite(x.duration)&&x.duration>0;})"
                + "||l.find(function(x){return isFinite(x.duration)&&x.duration>0;})||l[0];"
                + "if(active)window.__elActivate(active);"
                + "if(window.__elABTimer)clearInterval(window.__elABTimer);"
                + "window.__elABTimer=setInterval(function(){var m=window.__elMedia,a=window.__elA,b=window.__elB;"
                + "if(m&&window.__elLoop===true&&typeof a==='number'&&typeof b==='number'&&b>a&&!m.paused&&m.currentTime>=b){m.currentTime=a;var p=m.play();if(p&&p.catch)p.catch(function(){});}},60);");
    }

    private void showSpeedMenu(View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        String[] speeds = {"0.65×", "0.75×", "0.85×", "1.0×", "1.15×", "1.25×", "1.5×", "2.0×"};
        float[] values = {0.65f, 0.75f, 0.85f, 1.0f, 1.15f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < speeds.length; i++) {
            final float value = values[i];
            menu.getMenu().add(speeds[i]).setOnMenuItemClickListener(item -> { setSpeed(value); return true; });
        }
        menu.show();
    }

    private void setSpeed(float speed) {
        js("var m=window.__elMedia;if(m){m.playbackRate=" + speed + ";m.defaultPlaybackRate=" + speed + ";}");
        if (speedButton != null) speedButton.setText(trimSpeed(speed) + "×");
    }

    private void refreshNow() {
        handler.postDelayed(() -> updateStatusOnce(), 80);
    }

    private void startStatus() {
        stopStatus();
        statusTask = new Runnable() {
            @Override public void run() {
                if (popup == null || !popup.isShowing()) { stopStatus(); return; }
                updateStatusOnce();
                handler.postDelayed(this, 220);
            }
        };
        handler.post(statusTask);
    }

    private void updateStatusOnce() {
        webView.evaluateJavascript(
                "(function(){"
                        + "var l=[].slice.call(document.querySelectorAll('audio,video'));"
                        + "l.forEach(function(x){if(!x.__elBound&&window.__elActivate){x.__elBound=true;x.addEventListener('play',function(){window.__elActivate(x);},true);x.addEventListener('playing',function(){window.__elActivate(x);},true);}});"
                        + "var live=l.find(function(x){return !x.paused&&!x.ended&&x.readyState>0;});"
                        + "if(live&&live!==window.__elMedia&&window.__elActivate)window.__elActivate(live);"
                        + "var m=window.__elMedia;if(!m||!document.contains(m)){m=live||l.find(function(x){return isFinite(x.duration)&&x.duration>0;})||l[0];window.__elMedia=m;}"
                        + "if(!m)return '';"
                        + "return JSON.stringify({t:m.currentTime||0,d:(isFinite(m.duration)?m.duration:0),p:!m.paused,r:m.playbackRate||1,a:(typeof window.__elA==='number'?window.__elA:null),b:(typeof window.__elB==='number'?window.__elB:null),loop:window.__elLoop===true,tag:m.tagName||''});"
                        + "})()",
                value -> {
                    try {
                        String decoded = new JSONArray("[" + value + "]").getString(0);
                        if (decoded == null || decoded.isEmpty()) return;
                        JSONObject o = new JSONObject(decoded);
                        double t = o.optDouble("t", 0);
                        double d = o.optDouble("d", 0);
                        double a = o.isNull("a") ? -1 : o.optDouble("a", -1);
                        double b = o.isNull("b") ? -1 : o.optDouble("b", -1);
                        boolean playing = o.optBoolean("p", false);
                        boolean loop = o.optBoolean("loop", false);
                        double rate = o.optDouble("r", 1);
                        String tag = o.optString("tag", "");

                        if (statusView != null) {
                            String text = format(t) + " / " + format(d) + ("VIDEO".equals(tag) ? "  视频" : "  音频");
                            if (a >= 0) text += "  A " + format(a);
                            if (b >= 0) text += "  B " + format(b);
                            statusView.setText(text);
                        }
                        if (progressView != null) progressView.setState(t, d, a, b);
                        if (playButton != null) playButton.setText(playing ? "❚❚" : "▶");
                        if (speedButton != null) speedButton.setText(trimSpeed((float) rate) + "×");
                        if (loopButton != null) loopButton.setText(loop ? "循环✓" : "循环");
                    } catch (Exception ignored) {}
                });
    }

    public void stopStatus() {
        if (statusTask != null) handler.removeCallbacks(statusTask);
        statusTask = null;
    }

    public void dismissPopup() {
        stopStatus();
        if (popup != null) {
            try { popup.dismiss(); } catch (Exception ignored) {}
            popup = null;
        }
    }

    private void js(String code) { webView.evaluateJavascript("(function(){" + code + "})()", null); }
    private String clean(String s) { return s == null ? "" : s.replace("\"", ""); }

    private String format(double sec) {
        int total = (int) Math.max(0, Math.round(sec));
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60);
    }

    private String trimSpeed(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.001f) return String.format(Locale.US, "%.1f", speed);
        String s = String.format(Locale.US, "%.2f", speed);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private Button compactButton(String text, int size) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(1), 0, dp(1), 0);
        return b;
    }

    private LinearLayout.LayoutParams fixed(int width) { return new LinearLayout.LayoutParams(width, dp(38)); }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(activity, s, Toast.LENGTH_SHORT).show(); }

    private static class MediaProgressView extends View {
        interface SeekListener { void onSeek(double seconds); }

        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerA = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerB = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);

        private double current;
        private double duration;
        private double a = -1;
        private double b = -1;
        private SeekListener seekListener;
        private long lastSeekAt;

        MediaProgressView(Activity context) {
            super(context);
            float den = context.getResources().getDisplayMetrics().density;
            setClickable(true);
            track.setColor(Color.rgb(210, 214, 221));
            track.setStrokeWidth(3f * den);
            played.setColor(Color.rgb(30, 100, 210));
            played.setStrokeWidth(4f * den);
            markerA.setColor(Color.rgb(0, 150, 110));
            markerA.setStrokeWidth(3f * den);
            markerB.setColor(Color.rgb(230, 120, 30));
            markerB.setStrokeWidth(3f * den);
            thumb.setColor(Color.rgb(30, 100, 210));
        }

        void setSeekListener(SeekListener listener) { this.seekListener = listener; }

        void setState(double current, double duration, double a, double b) {
            this.current = current;
            this.duration = duration;
            this.a = a;
            this.b = b;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = getPaddingLeft() + dpLocal(8);
            float right = getWidth() - getPaddingRight() - dpLocal(8);
            float y = getHeight() / 2f;
            canvas.drawLine(left, y, right, y, track);
            if (duration > 0) {
                float px = left + (float) Math.max(0, Math.min(1, current / duration)) * (right - left);
                canvas.drawLine(left, y, px, y, played);
                if (a >= 0) drawMarker(canvas, left, right, y, a, duration, markerA, "A");
                if (b >= 0) drawMarker(canvas, left, right, y, b, duration, markerB, "B");
                canvas.drawCircle(px, y, dpLocal(5), thumb);
            }
        }

        private void drawMarker(Canvas canvas, float left, float right, float y, double value, double total, Paint paint, String label) {
            if (total <= 0) return;
            float ratio = (float) Math.max(0, Math.min(1, value / total));
            float x = left + ratio * (right - left);
            canvas.drawLine(x, y - dpLocal(10), x, y + dpLocal(10), paint);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(paint.getColor());
            text.setTextSize(dpLocal(9));
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, x, dpLocal(9), text);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (duration <= 0) return false;
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) return false;
            float left = getPaddingLeft() + dpLocal(8);
            float right = getWidth() - getPaddingRight() - dpLocal(8);
            float x = Math.max(left, Math.min(right, event.getX()));
            double ratio = (x - left) / Math.max(1f, right - left);
            current = ratio * duration;
            invalidate();
            long now = System.currentTimeMillis();
            if (seekListener != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN || now - lastSeekAt > 70)) {
                lastSeekAt = now;
                seekListener.onSeek(current);
            }
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
        private float dpLocal(float v) { return v * getResources().getDisplayMetrics().density; }
    }
}
