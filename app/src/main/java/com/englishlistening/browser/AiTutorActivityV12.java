package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class AiTutorActivityV12 extends Activity {
    private static final int SPEECH_REQUEST = 2201;

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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247,248,250));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        data = new AppData(this);
        keyStore = new SecureApiKeyStore(this);
        client = new GeminiClient(keyStore);

        Intent i = getIntent();
        currentMaterial = safe(i.getStringExtra("material"));
        currentTitle = safe(i.getStringExtra("title"));
        currentUrl = safe(i.getStringExtra("url"));
        if (currentTitle.trim().isEmpty()) currentTitle = "学习素材";

        buildUi();
        renderMaterialPreview();
        if (!keyStore.hasKey()) {
            resultView.setText("AI老师还没有配置 Gemini Key。右上角 ⋮ → AI设置。\n\n麦克风语音输入不需要任何 AI Key。 ");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6),dp(4),dp(6),dp(4));
        top.setBackgroundColor(Color.rgb(247,248,250));

        Button back = button("←",22);
        back.setOnClickListener(v -> finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(50),dp(46)));

        TextView title = new TextView(this);
        title.setText("AI老师");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8),0,0,0);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1f));

        Button more = button("⋮",24);
        more.setOnClickListener(this::showMoreMenu);
        top.addView(more,new LinearLayout.LayoutParams(dp(50),dp(46)));
        root.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        materialPreview = new TextView(this);
        materialPreview.setTextSize(13);
        materialPreview.setTextColor(Color.rgb(71,84,103));
        materialPreview.setBackgroundColor(Color.rgb(248,250,252));
        materialPreview.setPadding(dp(14),dp(9),dp(14),dp(9));
        materialPreview.setMaxLines(2);
        materialPreview.setOnClickListener(v -> editMaterial());
        root.addView(materialPreview);

        LinearLayout quick = new LinearLayout(this);
        quick.setPadding(dp(8),dp(6),dp(8),dp(6));
        String[] names = {"听力分析","高频表达","出一道题"};
        String[] tasks = {"listening","phrases","quiz"};
        for (int x=0;x<names.length;x++) {
            Button b = button(names[x],12);
            final String task = tasks[x];
            b.setOnClickListener(v -> runTask(task));
            quick.addView(b,new LinearLayout.LayoutParams(0,dp(44),1f));
        }
        root.addView(quick,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));

        resultView = new TextView(this);
        resultView.setTextSize(16);
        resultView.setTextColor(Color.rgb(17,24,39));
        resultView.setTextIsSelectable(true);
        resultView.setLineSpacing(0,1.18f);
        resultView.setPadding(dp(16),dp(14),dp(16),dp(18));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(resultView,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8),dp(6),dp(8),dp(8));
        composer.setBackgroundColor(Color.rgb(247,248,250));

        answerBox = new EditText(this);
        answerBox.setHint("输入回答，或点麦克风说英语");
        answerBox.setTextSize(15);
        answerBox.setMaxLines(3);
        composer.addView(answerBox,new LinearLayout.LayoutParams(0,dp(58),1f));

        Button voice = button("🎤",21);
        voice.setOnClickListener(v -> startSystemSpeechInput());
        voice.setOnLongClickListener(v -> { openKeyboard(); return true; });
        composer.addView(voice,new LinearLayout.LayoutParams(dp(58),dp(58)));

        Button send = button("发送",13);
        send.setOnClickListener(v -> sendAnswer());
        composer.addView(send,new LinearLayout.LayoutParams(dp(64),dp(58)));
        root.addView(composer,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)));

        setContentView(root);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu m = new PopupMenu(this,anchor);
        m.getMenu().add("编辑素材");
        m.getMenu().add("工程现场迁移");
        m.getMenu().add("简单讲解");
        m.getMenu().add("AI设置");
        m.getMenu().add("复制AI结果");
        if (AppData.isHttp(currentUrl)) m.getMenu().add("打开原网页");
        m.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if ("编辑素材".equals(t)) editMaterial();
            else if ("工程现场迁移".equals(t)) runTask("engineering");
            else if ("简单讲解".equals(t)) runTask("explain");
            else if ("AI设置".equals(t)) showAiSetup();
            else if ("复制AI结果".equals(t)) copy(resultView.getText().toString(),"AI结果");
            else if ("打开原网页".equals(t)) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))); }
                catch (Exception e) { toast("无法打开原网页"); }
            }
            return true;
        });
        m.show();
    }

    private void startSystemSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak English");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (ActivityNotFoundException e) {
            openKeyboard();
            resultView.setText("手机没有独立语音识别服务，已打开当前输入法。直接使用输入法自带的麦克风即可。 ");
        } catch (Exception e) {
            openKeyboard();
            resultView.setText("系统语音输入暂不可用，已打开当前输入法。直接使用输入法自带的麦克风即可。 ");
        }
    }

    private void openKeyboard() {
        answerBox.requestFocus();
        answerBox.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(answerBox, InputMethodManager.SHOW_IMPLICIT);
        },120);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode,resultCode,dataIntent);
        if (requestCode != SPEECH_REQUEST || resultCode != RESULT_OK || dataIntent == null) return;
        ArrayList<String> results = dataIntent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String text = safe(results.get(0)).trim();
        if (text.isEmpty()) return;
        answerBox.setText(text);
        answerBox.setSelection(answerBox.length());
        if (keyStore.hasKey() && !currentMaterial.trim().isEmpty()) {
            answerBox.setText("");
            askFollowUp(text);
        } else {
            resultView.setText("识别结果：\n" + text);
        }
    }

    private void runTask(String task) {
        if (!ensureReady()) return;
        data.markPractice();
        String instruction;
        switch(task) {
            case "phrases":
                instruction = "从素材中只挑5个真正值得听懂和开口用的高频表达。每个写：英文表达：大白话中文意思；这段里什么意思；一个自然短例句。不要凑数。";
                break;
            case "engineering":
                instruction = "从素材挑3到5个能迁移到新加坡办公室装修/建筑现场的表达。给原表达、现场自然说法、中文意思和短例句。不要硬迁移。";
                break;
            case "quiz":
                instruction = "根据素材只出一道适合初级到中级学习者的听力理解或复述题。只出题，不给答案，用简单英语。";
                break;
            case "explain":
                instruction = "用简单中文讲明白这段英语：先一句话说核心意思，再解释最难懂的3个地方。不要长篇语法课。";
                break;
            default:
                instruction = "把素材当真实听力材料分析：1核心意思；2最容易听不出来的3到5个短语；3真实语速可能出现的连读、弱读、吞音；4给一个很短的复述任务。中文直白。";
        }
        resultView.setText("AI 正在处理…");
        client.ask(base()+"\n\n任务："+instruction+"\n\n【素材】\n"+currentMaterial,new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                resultView.setText(text);
            }
            @Override public void onError(String message) {
                resultView.setText("AI暂时不可用：\n"+message);
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
        resultView.setText("AI 正在检查…");
        String p = base()+"\n\n【素材】\n"+currentMaterial+"\n\n【AI上一条回复】\n"+trim(lastAiReply,4500)+"\n\n【我的回答】\n"+user+"\n\n直接评价我的回答。先指出最影响理解的1到3个问题，再给一个我容易说出口的自然版本。如果上一条是题目，评价后再出下一题；否则继续围绕素材练。不要一次讲太多。";
        client.ask(p,new GeminiClient.Callback() {
            @Override public void onSuccess(String text) {
                lastAiReply = text;
                resultView.setText(text);
            }
            @Override public void onError(String message) {
                resultView.setText("AI暂时不可用：\n"+message);
            }
        });
    }

    private boolean ensureReady() {
        if (currentMaterial.trim().isEmpty()) {
            toast("先准备英文素材");
            editMaterial();
            return false;
        }
        if (!keyStore.hasKey()) {
            showAiSetup();
            return false;
        }
        return true;
    }

    private String base() {
        return "你是英语听力和口语教练。重点帮助用户听懂真实口语并能在日常工作和建筑/装修现场说出来。回答短、具体、能马上练，不堆术语。";
    }

    private void renderMaterialPreview() {
        String b = currentMaterial.trim().replaceAll("\\s+"," ");
        materialPreview.setText(b.isEmpty()?"素材：未载入（点这里编辑）":"素材："+currentTitle+"\n"+AppData.ellipsize(b,120));
    }

    private void editMaterial() {
        EditText e = new EditText(this);
        e.setGravity(Gravity.TOP);
        e.setMinLines(8);
        e.setMaxLines(16);
        e.setText(currentMaterial);
        e.setHint("粘贴或修改英文素材");
        new AlertDialog.Builder(this)
                .setTitle("编辑素材")
                .setView(e)
                .setPositiveButton("保存",(d,w) -> {
                    currentMaterial = e.getText().toString().trim();
                    renderMaterialPreview();
                })
                .setNegativeButton("取消",null)
                .show();
    }

    private void showAiSetup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),0,dp(18),0);

        TextView info = new TextView(this);
        info.setText("AI老师使用 Gemini Key；语音输入不需要 Key。普通英语学习素材即可，不要发送公司机密。 ");
        box.addView(info);

        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint(keyStore.hasKey()?"已配置；粘贴新Key可替换":"粘贴 Gemini API Key");
        box.addView(key,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("AI设置")
                .setView(box)
                .setPositiveButton("保存并测试",null)
                .setNeutralButton("获取免费Key",null)
                .setNegativeButton("取消",null)
                .create();

        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://aistudio.google.com/apikey"))); }
                catch(Exception e) { toast("无法打开 Google AI Studio"); }
            });
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String k = key.getText().toString().trim();
                if (!k.isEmpty()) {
                    try { keyStore.save(k); }
                    catch(Exception e) { toast("保存失败"); return; }
                }
                if (!keyStore.hasKey()) { toast("请先粘贴 API Key"); return; }
                d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                client.ask("Reply with exactly: OK",new GeminiClient.Callback() {
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

    @Override protected void onDestroy() {
        if (client != null) client.shutdown();
        super.onDestroy();
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String trim(String s,int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(s.length()-max); }
    private Button button(String text,int size) { Button b = new Button(this); b.setText(text); b.setTextSize(size); b.setAllCaps(false); b.setMinWidth(0); b.setMinHeight(0); return b; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void copy(String s,String label) { ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText(label,s==null?"":s)); toast("已复制"); }
}
