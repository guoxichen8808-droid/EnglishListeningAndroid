package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class AiTutorActivity extends Activity {
    private static final int SPEECH_REQUEST = 904;

    private AppData data;
    private SecureApiKeyStore keyStore;
    private GeminiClient client;

    private String currentMaterial = "";
    private String currentTitle = "学习素材";
    private String currentUrl = "";
    private String lastAiReply = "";

    private TextView materialPreview;
    private TextView resultView;
    private EditText answerBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247, 248, 250));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        data = new AppData(this);
        keyStore = new SecureApiKeyStore(this);
        client = new GeminiClient(keyStore);

        Intent intent = getIntent();
        currentMaterial = intent.getStringExtra("material");
        currentTitle = intent.getStringExtra("title");
        currentUrl = intent.getStringExtra("url");
        if (currentMaterial == null) currentMaterial = "";
        if (currentTitle == null || currentTitle.trim().isEmpty()) currentTitle = "学习素材";
        if (currentUrl == null) currentUrl = "";

        buildUi();
        renderMaterialPreview();

        if (!keyStore.hasKey()) {
            resultView.setText("第一次使用 AI 老师，需要配置一次免费的 Gemini API Key。\n\n配置完成后，这里会直接显示 AI 分析结果，不会再跳网页或复制提示词。\n\n点右上角 ⋮ → AI设置。 ");
        } else if (!currentMaterial.trim().isEmpty()) {
            resultView.setText("素材已载入。\n\n可以直接点上方“听力分析 / 高频表达 / 出一道题”，也可以在底部用语音或文字和 AI 连续练习。 ");
        } else {
            resultView.setText("当前没有素材。\n\n点右上角 ⋮ → 编辑素材，粘贴英文后开始练习。 ");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(4), dp(6), dp(4));
        top.setBackgroundColor(Color.rgb(247, 248, 250));

        Button back = button("←", 22);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(50), dp(46)));

        TextView title = new TextView(this);
        title.setText("AI老师");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button more = button("⋮", 24);
        more.setOnClickListener(this::showMoreMenu);
        top.addView(more, new LinearLayout.LayoutParams(dp(50), dp(46)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        materialPreview = new TextView(this);
        materialPreview.setTextSize(13);
        materialPreview.setTextColor(Color.rgb(71, 84, 103));
        materialPreview.setBackgroundColor(Color.rgb(248, 250, 252));
        materialPreview.setPadding(dp(14), dp(9), dp(14), dp(9));
        materialPreview.setMaxLines(2);
        materialPreview.setOnClickListener(v -> editMaterial());
        root.addView(materialPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(dp(8), dp(6), dp(8), dp(6));
        quick.setBackgroundColor(Color.WHITE);
        String[] names = {"听力分析", "高频表达", "出一道题"};
        String[] tasks = {"listening", "phrases", "quiz"};
        for (int i = 0; i < names.length; i++) {
            Button b = button(names[i], 12);
            final String task = tasks[i];
            b.setOnClickListener(v -> runTask(task));
            quick.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(quick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        resultView = new TextView(this);
        resultView.setTextSize(16);
        resultView.setTextColor(Color.rgb(17, 24, 39));
        resultView.setTextIsSelectable(true);
        resultView.setLineSpacing(0, 1.18f);
        resultView.setPadding(dp(16), dp(14), dp(16), dp(18));
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.setFillViewport(true);
        resultScroll.addView(resultView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(6), dp(8), dp(8));
        composer.setBackgroundColor(Color.rgb(247, 248, 250));

        answerBox = new EditText(this);
        answerBox.setHint("输入回答，或点麦克风说英语");
        answerBox.setTextSize(15);
        answerBox.setMinLines(1);
        answerBox.setMaxLines(3);
        answerBox.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(answerBox, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button voice = button("🎤", 21);
        voice.setContentDescription("语音回答");
        voice.setOnClickListener(v -> startSpeech());
        composer.addView(voice, new LinearLayout.LayoutParams(dp(58), dp(58)));

        Button send = button("发送", 13);
        send.setOnClickListener(v -> sendAnswer());
        composer.addView(send, new LinearLayout.LayoutParams(dp(64), dp(58)));
        root.addView(composer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        setContentView(root);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("编辑素材");
        menu.getMenu().add("工程现场迁移");
        menu.getMenu().add("简单讲解");
        menu.getMenu().add("AI设置");
        menu.getMenu().add("复制AI结果");
        if (AppData.isHttp(currentUrl)) menu.getMenu().add("打开原网页");
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if ("编辑素材".equals(t)) editMaterial();
            else if ("工程现场迁移".equals(t)) runTask("engineering");
            else if ("简单讲解".equals(t)) runTask("explain");
            else if ("AI设置".equals(t)) showSetup();
            else if ("复制AI结果".equals(t)) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AI结果", resultView.getText()));
                toast("AI结果已复制");
            } else if ("打开原网页".equals(t)) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))); }
                catch (Exception e) { toast("无法打开原网页"); }
            }
            return true;
        });
        menu.show();
    }

    private void renderMaterialPreview() {
        String body = currentMaterial == null ? "" : currentMaterial.trim().replaceAll("\\s+", " ");
        if (body.isEmpty()) materialPreview.setText("素材：未载入（点这里可编辑）");
        else materialPreview.setText("素材：" + currentTitle + "\n" + AppData.ellipsize(body, 120));
    }

    private void editMaterial() {
        EditText edit = new EditText(this);
        edit.setGravity(Gravity.TOP);
        edit.setMinLines(8);
        edit.setMaxLines(16);
        edit.setText(currentMaterial);
        edit.setHint("粘贴或修改英文素材");
        new AlertDialog.Builder(this)
                .setTitle("编辑素材")
                .setView(edit)
                .setPositiveButton("保存", (d, w) -> {
                    currentMaterial = edit.getText().toString().trim();
                    renderMaterialPreview();
                    toast("素材已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void runTask(String task) {
        if (!ensureReady()) return;
        data.markPractice();
        String instruction;
        switch (task) {
            case "phrases":
                instruction = "从素材中只挑5个真正值得听懂和开口使用的高频表达。每个按：英文表达：大白话中文意思；这段里什么意思；一个自然短例句。不要凑数。";
                break;
            case "engineering":
                instruction = "从素材里挑最能迁移的3到5个表达，改造成新加坡办公室装修/建筑现场能直接说的英语。每条给原表达、现场自然说法、中文意思和一个短例句。不相关就不要硬迁移。";
                break;
            case "quiz":
                instruction = "根据素材只出一道适合初级到中级学习者的听力理解或口语复述题。只出题，不给答案，题目用简单英语。";
                break;
            case "explain":
                instruction = "用简单中文讲明白这段英语：先一句话说核心意思，再解释最难懂的3个地方。不要长篇语法课。";
                break;
            default:
                instruction = "把素材当真实听力材料分析。告诉我：1核心意思；2最容易听不出来的3到5个短语；3真实语速里哪些地方可能连读、弱读或吞音；4最后给一个很短的复述任务。中文要直白。";
        }
        String prompt = baseContext() + "\n\n任务：" + instruction + "\n\n【素材】\n" + currentMaterial;
        resultView.setText("AI 正在处理…");
        client.ask(prompt, new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                resultView.setText(text);
            }
            @Override public void onError(String message) {
                resultView.setText("AI 暂时不可用：\n" + message + "\n\n可点右上角 ⋮ → AI设置 检查连接。 ");
            }
        });
    }

    private void sendAnswer() {
        String user = answerBox.getText().toString().trim();
        if (user.isEmpty()) { toast("先输入或说一句英语"); return; }
        if (!ensureReady()) return;
        answerBox.setText("");
        askFollowUp(user);
    }

    private void askFollowUp(String user) {
        data.markPractice();
        String prompt = baseContext() +
                "\n\n【素材】\n" + currentMaterial +
                "\n\n【AI上一条回复】\n" + trim(lastAiReply, 4500) +
                "\n\n【我的回答】\n" + user +
                "\n\n直接评价我的回答。先指出最影响理解的1到3个问题，再给一个我容易说出口的自然版本。如果上一条是题目，评价后再出下一道题；否则继续围绕素材练。不要一次讲太多。";
        resultView.setText("AI 正在检查你的回答…");
        client.ask(prompt, new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                resultView.setText(text);
            }
            @Override public void onError(String message) {
                resultView.setText("AI 暂时不可用：\n" + message);
            }
        });
    }

    private boolean ensureReady() {
        if (!keyStore.hasKey()) {
            showSetup();
            return false;
        }
        if (currentMaterial == null || currentMaterial.trim().isEmpty()) {
            toast("先准备英文素材");
            editMaterial();
            return false;
        }
        return true;
    }

    private String baseContext() {
        return "你是英语听力和口语教练。重点是帮助用户听懂真实口语并能在日常工作和建筑/装修现场说出来。回答要短、具体、能马上练；不要堆术语，不要为了显得丰富增加无用内容。";
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请用英语回答");
        try { startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception e) { toast("手机没有可用的语音识别服务"); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && intent != null) {
            ArrayList<String> results = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spoken = results.get(0);
                answerBox.setText(spoken);
                answerBox.setSelection(answerBox.getText().length());
                if (ensureReady()) {
                    answerBox.setText("");
                    askFollowUp(spoken);
                }
            }
        }
    }

    private void showSetup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), 0, dp(18), 0);

        TextView info = new TextView(this);
        info.setText("只需设置一次 Gemini API Key。保存后，AI分析会直接显示在本页。\n\n免费层不适合发送公司机密或敏感资料。 ");
        info.setTextSize(13);
        box.addView(info);

        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint(keyStore.hasKey() ? "已配置；粘贴新Key可替换" : "粘贴 Gemini API Key");
        box.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("AI设置")
                .setView(box)
                .setPositiveButton("保存并测试", null)
                .setNeutralButton("获取Key", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))); }
                catch (Exception e) { toast("无法打开 Google AI Studio"); }
            });
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = key.getText().toString().trim();
                if (!value.isEmpty()) {
                    try { keyStore.save(value); }
                    catch (Exception e) { toast("保存Key失败"); return; }
                }
                if (!keyStore.hasKey()) { toast("请先粘贴API Key"); return; }
                d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                client.ask("Reply with exactly: OK", new GeminiClient.Callback() {
                    @Override public void onSuccess(String text) {
                        toast("AI连接成功");
                        d.dismiss();
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

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    private Button button(String text, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
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
    protected void onDestroy() {
        if (client != null) client.shutdown();
        super.onDestroy();
    }
}
