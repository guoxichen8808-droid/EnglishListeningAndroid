package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class TtsRepairActivity extends Activity {
    private TextToSpeech tts;
    private TextView status;
    private boolean pendingTest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247, 248, 250));
        buildUi();
        initTts(false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("英文语音修复");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(17, 24, 39));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("用于修复“手机英文 TTS 暂不可用”。先点“测试英文语音”。如果没有声音，再按顺序打开系统 TTS 设置或下载语音数据。\n\n不会清除你的收藏、历史或学习素材。");
        desc.setTextSize(14);
        desc.setTextColor(Color.rgb(75, 85, 99));
        desc.setPadding(0, dp(12), 0, dp(18));
        root.addView(desc);

        status = new TextView(this);
        status.setText("正在检查手机语音服务…");
        status.setTextSize(15);
        status.setTextColor(Color.rgb(31, 41, 55));
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button test = button("① 测试英文语音");
        Button settings = button("② 打开系统 TTS 设置");
        Button installData = button("③ 下载 / 安装语音数据");
        Button google = button("④ 安装 Google 英文语音服务");
        Button close = button("返回英语听力 APP");
        root.addView(test); root.addView(settings); root.addView(installData); root.addView(google); root.addView(close);

        test.setOnClickListener(v -> testVoice());
        settings.setOnClickListener(v -> openTtsSettings());
        installData.setOnClickListener(v -> installVoiceData());
        google.setOnClickListener(v -> openGoogleTts());
        close.setOnClickListener(v -> finish());

        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.topMargin = dp(10);
        b.setLayoutParams(p);
        return b;
    }

    private void initTts(boolean testAfterInit) {
        pendingTest = testAfterInit;
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        status.setText("正在检查手机语音服务…");
        tts = new TextToSpeech(this, result -> {
            if (result != TextToSpeech.SUCCESS) {
                status.setText("未检测到可用的文字转语音引擎。\n请点“打开系统 TTS 设置”或“安装 Google 英文语音服务”。");
                return;
            }
            Locale selected = chooseEnglishLocale();
            if (selected == null) {
                status.setText("检测到 TTS 引擎，但没有可用的英文语音数据。\n请点“下载 / 安装语音数据”。");
                return;
            }
            status.setText("英文语音服务可用：" + selected.toLanguageTag() + "\n现在可以返回 APP 使用逐句精听、听写和朗读。 ");
            if (pendingTest) speakTest();
            pendingTest = false;
        });
    }

    private Locale chooseEnglishLocale() {
        if (tts == null) return null;
        Locale[] candidates = {Locale.US, Locale.UK, Locale.ENGLISH, Locale.CANADA};
        for (Locale locale : candidates) {
            try {
                int available = tts.isLanguageAvailable(locale);
                if (available >= TextToSpeech.LANG_AVAILABLE) {
                    int result = tts.setLanguage(locale);
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) return locale;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void testVoice() {
        if (tts == null) {
            initTts(true);
            return;
        }
        Locale selected = chooseEnglishLocale();
        if (selected == null) {
            status.setText("目前仍没有可用的英文语音。\n请先下载英文语音数据，然后回来再测试。");
            return;
        }
        speakTest();
    }

    private void speakTest() {
        try {
            tts.setSpeechRate(0.9f);
            int result = tts.speak("This is an English voice test. Your listening practice is ready.", TextToSpeech.QUEUE_FLUSH, null, "tts_test");
            if (result == TextToSpeech.ERROR) status.setText("TTS 引擎已找到，但播放失败。请切换系统默认语音引擎后再试。");
            else Toast.makeText(this, "如果听到英文测试语音，说明修复成功", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            status.setText("测试播放失败。请打开系统 TTS 设置，选择一个可用语音引擎。");
        }
    }

    private void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }
    }

    private void installVoiceData() {
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (ActivityNotFoundException e) {
            openTtsSettings();
        }
    }

    private void openGoogleTts() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")));
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))); }
            catch (Exception ignored) { Toast.makeText(this, "本机没有可用的应用商店，请使用系统自带 TTS 引擎", Toast.LENGTH_LONG).show(); }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) initTts(false);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
