package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

public class RealAiTutor {
    public static final int SPEECH_REQUEST = 804;

    private final Activity activity;
    private final WebView webView;
    private final AppData data;
    private final SecureApiKeyStore keyStore;
    private final GeminiClient client;

    private String currentMaterial = "";
    private String currentTitle = "学习素材";
    private String currentUrl = "";
    private String lastAiReply = "";
    private EditText activeAnswerBox;
    private TextView activeResultView;

    public RealAiTutor(Activity activity, WebView webView, AppData data) {
        this.activity = activity;
        this.webView = webView;
        this.data = data;
        this.keyStore = new SecureApiKeyStore(activity);
        this.client = new GeminiClient(keyStore);
    }

    public void openFromCurrentPage() {
        currentTitle = AppData.safeTitle(webView.getTitle(), "网页素材");
        currentUrl = webView.getUrl() == null ? "" : webView.getUrl();
        String url = webView.getUrl();
        if (!AppData.isHttp(url)) {
            showTutor("");
            return;
        }
        String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,9000);})()";
        webView.evaluateJavascript(js, value -> showTutor(decode(value)));
    }

    public void openMaterial(String title, String text, String url) {
        currentTitle = AppData.safeTitle(title, "素材库");
        currentUrl = url == null ? "" : url;
        showTutor(text == null ? "" : text);
    }

    private void showTutor(String extracted) {
        currentMaterial = extracted == null ? "" : extracted.trim();
        if (!keyStore.hasKey()) {
            showSetup(() -> showTutor(currentMaterial));
            return;
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(4), dp(14), dp(8));

        TextView label = new TextView(activity);
        label.setText("当前素材（可直接修改）");
        label.setTextSize(13);
        root.addView(label);

        EditText material = new EditText(activity);
        material.setGravity(Gravity.TOP);
        material.setMinLines(4);
        material.setMaxLines(7);
        material.setHint("粘贴英文素材，或先在网页选中文字再打开 AI老师");
        material.setText(currentMaterial);
        root.addView(material, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));

        LinearLayout task1 = row();
        Button listening = button("听力分析");
        Button phrases = button("高频表达");
        Button engineering = button("工程迁移");
        task1.addView(listening, cell()); task1.addView(phrases, cell()); task1.addView(engineering, cell());
        root.addView(task1);

        LinearLayout task2 = row();
        Button quiz = button("出一道题");
        Button summarize = button("简单讲解");
        Button settings = button("AI设置");
        task2.addView(quiz, cell()); task2.addView(summarize, cell()); task2.addView(settings, cell());
        root.addView(task2);

        TextView result = new TextView(activity);
        result.setTextSize(15);
        result.setTextIsSelectable(true);
        result.setPadding(dp(4), dp(10), dp(4), dp(10));
        ScrollView resultScroll = new ScrollView(activity);
        resultScroll.addView(result);
        root.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout answerRow = row();
        EditText answer = new EditText(activity);
        answer.setSingleLine(false);
        answer.setHint("回答 AI，或说一句英语");
        answerRow.addView(answer, new LinearLayout.LayoutParams(0, dp(56), 1f));
        Button voice = button("语音");
        Button send = button("发送");
        answerRow.addView(voice, new LinearLayout.LayoutParams(dp(62), dp(56)));
        answerRow.addView(send, new LinearLayout.LayoutParams(dp(62), dp(56)));
        root.addView(answerRow);

        activeAnswerBox = answer;
        activeResultView = result;

        listening.setOnClickListener(v -> runTask("listening", material, result));
        phrases.setOnClickListener(v -> runTask("phrases", material, result));
        engineering.setOnClickListener(v -> runTask("engineering", material, result));
        quiz.setOnClickListener(v -> runTask("quiz", material, result));
        summarize.setOnClickListener(v -> runTask("explain", material, result));
        settings.setOnClickListener(v -> showSetup(null));
        voice.setOnClickListener(v -> startSpeech());
        send.setOnClickListener(v -> {
            currentMaterial = material.getText().toString().trim();
            String user = answer.getText().toString().trim();
            if (user.isEmpty()) { toast("先输入或说一句"); return; }
            askFollowUp(user, result);
            answer.setText("");
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("AI老师 · 真正在线分析")
                .setView(root)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnDismissListener(d -> {
            activeAnswerBox = null;
            activeResultView = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(dialog.getWindow().getAttributes());
            p.width = WindowManager.LayoutParams.MATCH_PARENT;
            p.height = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.92f);
            dialog.getWindow().setAttributes(p);
        }
    }

    private void runTask(String task, EditText materialBox, TextView result) {
        currentMaterial = materialBox.getText().toString().trim();
        if (currentMaterial.isEmpty()) { toast("当前没有英文素材"); return; }
        data.markPractice();
        String instruction;
        switch (task) {
            case "phrases":
                instruction = "从素材里只挑 5 个最值得真实口语和听力掌握的高频表达。每个写：英文表达：大白话中文意思；在这段素材里是什么意思；一个自然短例句。不要凑数，不要写生僻词。";
                break;
            case "engineering":
                instruction = "把素材里最值得迁移的 3-5 个表达改造成新加坡办公室装修/建筑现场能直接说的英语。每条给：原表达、现场自然说法、中文意思、一个很短的真实现场例句。不要硬迁移不相关内容。";
                break;
            case "quiz":
                instruction = "根据素材只出一道适合初级到中级学习者的英语理解或复述题。只出题，不给答案。题目尽量用简单英语。";
                break;
            case "explain":
                instruction = "用简单中文把这段英语讲明白：先一句话说核心意思，再解释最难懂的 3 个地方。不要长篇语法课。";
                break;
            default:
                instruction = "把这段素材当听力材料分析。直接告诉我：1核心意思；2最容易听不出来的 3-5 个短语；3哪些地方真实语速下可能连读/弱读/吞音；4最后给我一个很短的复述任务。中文要直白。";
        }
        String prompt = baseContext() + "\n\n任务：" + instruction + "\n\n【素材】\n" + currentMaterial;
        result.setText("AI 正在分析…");
        client.ask(prompt, new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                result.setText(text);
            }
            @Override public void onError(String message) {
                result.setText("AI 暂时不可用：\n" + message);
            }
        });
    }

    private void askFollowUp(String user, TextView result) {
        if (currentMaterial == null || currentMaterial.trim().isEmpty()) { toast("没有学习素材"); return; }
        data.markPractice();
        String prompt = baseContext() +
                "\n\n【素材】\n" + currentMaterial +
                "\n\n【AI上一条回复】\n" + trim(lastAiReply, 4500) +
                "\n\n【我的回答】\n" + user +
                "\n\n请直接评价我的回答。先指出最影响理解的1-3个问题，再给一个我容易说出口的自然版本。如果上一条是题目，评价后再出下一道题；否则继续围绕素材和我练。不要一次讲太多。";
        result.setText("AI 正在检查你的回答…");
        client.ask(prompt, new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                result.setText(text);
            }
            @Override public void onError(String message) {
                result.setText("AI 暂时不可用：\n" + message);
            }
        });
    }

    private String baseContext() {
        return "你是英语听力和口语教练。用户主要目标是听懂真实口语并能在日常工作和建筑/装修现场说出来。回答要短、具体、能马上练，不要堆术语，不要为了显得丰富而增加无用内容。";
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请用英语回答");
        try { activity.startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception e) { toast("手机没有可用的语音识别服务"); }
    }

    public void handleSpeechResult(Intent dataIntent) {
        if (dataIntent == null || activeAnswerBox == null) return;
        ArrayList<String> results = dataIntent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String spoken = results.get(0);
        activeAnswerBox.setText(spoken);
        activeAnswerBox.setSelection(activeAnswerBox.getText().length());
        if (activeResultView != null) askFollowUp(spoken, activeResultView);
    }

    private void showSetup(Runnable afterSave) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), 0, dp(18), 0);
        TextView info = new TextView(activity);
        info.setText("AI老师使用 Google Gemini 免费层。第一次只需要在 Google AI Studio 创建一个免费 API Key，然后粘贴一次。Key 会加密保存在本机。\n\n注意：免费层提交的内容可能用于改进 Google 产品，不要发送公司机密或敏感资料。");
        info.setTextSize(13);
        box.addView(info);
        EditText key = new EditText(activity);
        key.setSingleLine(true);
        key.setHint(keyStore.hasKey() ? "已配置。粘贴新 Key 可替换" : "粘贴 Gemini API Key");
        box.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle("免费 AI 设置")
                .setView(box)
                .setPositiveButton("保存并测试", null)
                .setNeutralButton("获取免费 Key", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))); }
                catch (Exception e) { toast("无法打开 Google AI Studio"); }
            });
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = key.getText().toString().trim();
                if (!value.isEmpty()) {
                    try { keyStore.save(value); }
                    catch (Exception e) { toast("保存 Key 失败"); return; }
                }
                if (!keyStore.hasKey()) { toast("请先粘贴 API Key"); return; }
                d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                client.ask("Reply with exactly: OK", new GeminiClient.Callback() {
                    @Override public void onSuccess(String text) {
                        toast("免费 AI 已连接成功");
                        d.dismiss();
                        if (afterSave != null) afterSave.run();
                    }
                    @Override public void onError(String message) {
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast(message);
                    }
                });
            });
        });
        d.show();
    }

    private String decode(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch (Exception e) { return value.replace("\\n", "\n").replace("\\\"", "\""); }
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    public void destroy() { client.shutdown(); }

    private LinearLayout row() { LinearLayout r = new LinearLayout(activity); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private Button button(String text) { Button b = new Button(activity); b.setText(text); b.setTextSize(11); b.setAllCaps(false); b.setMinWidth(0); b.setMinHeight(0); return b; }
    private LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(0, dp(44), 1f); }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(activity, s, Toast.LENGTH_LONG).show(); }
}
