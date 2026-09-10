package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AiPracticeHelper {
    public static final int SPEECH_REQUEST = 703;

    public interface MaterialCallback { void onMaterial(String text); }

    private final Activity activity;
    private final WebView webView;
    private final AppData data;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String currentMaterial = "";
    private String currentTitle = "学习素材";
    private String currentUrl = "";
    private String pendingSpeechTarget = "";

    public AiPracticeHelper(Activity activity, WebView webView) {
        this(activity, webView, new AppData(activity));
    }

    public AiPracticeHelper(Activity activity, WebView webView, AppData data) {
        this.activity = activity;
        this.webView = webView;
        this.data = data;
        tts = new TextToSpeech(activity, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    public void extractCurrentPage(MaterialCallback callback) {
        String url = webView.getUrl();
        if (AppData.isHttp(url) && !AppData.isAiUrl(url)) {
            String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,10000);})()";
            webView.evaluateJavascript(js, value -> callback.onMaterial(decode(value)));
        } else callback.onMaterial("");
    }

    public void openFromCurrentPage() {
        currentTitle = AppData.safeTitle(webView.getTitle(), "网页素材");
        currentUrl = webView.getUrl() == null ? "" : webView.getUrl();
        extractCurrentPage(this::showDialog);
    }

    public void openMaterial(String title, String text, String url) {
        currentTitle = AppData.safeTitle(title, "素材库");
        currentUrl = url == null ? "" : url;
        showDialog(text == null ? "" : text);
    }

    public void saveCurrentPageToLibrary() {
        currentTitle = AppData.safeTitle(webView.getTitle(), "网页素材");
        currentUrl = webView.getUrl() == null ? "" : webView.getUrl();
        extractCurrentPage(text -> {
            if (text.trim().isEmpty()) { toast("当前页面没有提取到可保存的文字"); return; }
            askSaveMaterial(text);
        });
    }

    public void showDialog(String extracted) {
        currentMaterial = extracted == null ? "" : extracted.trim();
        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(8));
        scroll.addView(box);

        TextView tip = new TextView(activity);
        tip.setText("先在网页选中文字，会优先只训练选中内容；未选中时自动提取当前页正文。所有本地训练不需要 AI。\n");
        tip.setTextSize(12);
        box.addView(tip);

        EditText material = new EditText(activity);
        material.setHint("粘贴或编辑本次英文素材");
        material.setText(currentMaterial);
        material.setGravity(Gravity.TOP);
        material.setMinLines(6);
        material.setMaxLines(12);
        box.addView(material, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));

        TextView localTitle = title("本地免费训练");
        box.addView(localTitle);
        LinearLayout local1 = row();
        Button sentence = button("逐句精听");
        Button dictation = button("听写模式");
        Button shadow = button("跟读识别");
        local1.addView(sentence, cell()); local1.addView(dictation, cell()); local1.addView(shadow, cell());
        box.addView(local1);

        LinearLayout local2 = row();
        Button read = button("朗读全文");
        Button save = button("保存素材");
        Button copy = button("复制素材");
        local2.addView(read, cell()); local2.addView(save, cell()); local2.addView(copy, cell());
        box.addView(local2);

        TextView aiTitle = title("AI陪练模式");
        box.addView(aiTitle);
        LinearLayout modes1 = row();
        Button listening = button("听力精练");
        Button retell = button("复述问答");
        Button correction = button("口语纠错");
        modes1.addView(listening, cell()); modes1.addView(retell, cell()); modes1.addView(correction, cell());
        box.addView(modes1);
        LinearLayout modes2 = row();
        Button engineering = button("工程英语");
        Button vocab = button("高频表达");
        Button quiz = button("随机测验");
        modes2.addView(engineering, cell()); modes2.addView(vocab, cell()); modes2.addView(quiz, cell());
        box.addView(modes2);

        final String[] mode = {"听力精练"};
        listening.setOnClickListener(v -> choose(mode, "听力精练"));
        retell.setOnClickListener(v -> choose(mode, "复述问答"));
        correction.setOnClickListener(v -> choose(mode, "口语纠错"));
        engineering.setOnClickListener(v -> choose(mode, "工程英语"));
        vocab.setOnClickListener(v -> choose(mode, "高频表达"));
        quiz.setOnClickListener(v -> choose(mode, "随机测验"));

        sentence.setOnClickListener(v -> {
            syncMaterial(material);
            if (needMaterial()) openSentenceTrainer(currentMaterial);
        });
        dictation.setOnClickListener(v -> {
            syncMaterial(material);
            if (needMaterial()) openDictation(currentMaterial);
        });
        shadow.setOnClickListener(v -> {
            syncMaterial(material);
            if (needMaterial()) {
                String[] s = sentences(currentMaterial);
                startSpeechRecognition(s.length == 0 ? currentMaterial : s[0]);
            }
        });
        read.setOnClickListener(v -> {
            syncMaterial(material);
            if (needMaterial()) showTtsControls(currentMaterial);
        });
        save.setOnClickListener(v -> {
            syncMaterial(material);
            if (needMaterial()) askSaveMaterial(currentMaterial);
        });
        copy.setOnClickListener(v -> { syncMaterial(material); copy(currentMaterial, "练习素材"); });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("终极训练工作室")
                .setView(scroll)
                .setPositiveButton("ChatGPT", null)
                .setNeutralButton("豆包", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                syncMaterial(material);
                if (!needMaterial()) return;
                data.markPractice();
                copy(buildPrompt(currentMaterial, mode[0]), "AI练习提示词");
                openProvider("https://chatgpt.com/", "ChatGPT");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                syncMaterial(material);
                if (!needMaterial()) return;
                data.markPractice();
                copy(buildPrompt(currentMaterial, mode[0]), "AI练习提示词");
                openProvider("https://www.doubao.com/chat/", "豆包");
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(dialog.getWindow().getAttributes());
            p.width = WindowManager.LayoutParams.MATCH_PARENT;
            p.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.9f);
            dialog.getWindow().setAttributes(p);
        }
    }

    private void syncMaterial(EditText editor) { currentMaterial = editor.getText().toString().trim(); }
    private boolean needMaterial() {
        if (currentMaterial == null || currentMaterial.trim().isEmpty()) { toast("请先准备英文素材"); return false; }
        return true;
    }

    private void openSentenceTrainer(String text) {
        String[] lines = sentences(text);
        if (lines.length == 0) { toast("没有识别到可训练的英文句子"); return; }
        data.markPractice();
        final int[] index = {0};
        final boolean[] visible = {false};
        final float[] rate = {0.85f};

        LinearLayout box = vertical();
        TextView pos = new TextView(activity);
        TextView answer = new TextView(activity);
        answer.setTextSize(20);
        answer.setTextIsSelectable(true);
        answer.setPadding(0, dp(12), 0, dp(12));
        box.addView(pos); box.addView(answer);

        LinearLayout r1 = row();
        Button hear = button("▶ 听一句");
        Button reveal = button("显示答案");
        Button shadow = button("跟读这句");
        r1.addView(hear, cell()); r1.addView(reveal, cell()); r1.addView(shadow, cell());
        box.addView(r1);
        LinearLayout r2 = row();
        Button prev = button("上一句");
        Button speed = button("0.85×");
        Button next = button("下一句");
        r2.addView(prev, cell()); r2.addView(speed, cell()); r2.addView(next, cell());
        box.addView(r2);

        Runnable render = () -> {
            pos.setText("第 " + (index[0] + 1) + " / " + lines.length + " 句");
            answer.setText(visible[0] ? lines[index[0]] : "••••••  答案已隐藏  ••••••");
            reveal.setText(visible[0] ? "隐藏答案" : "显示答案");
        };
        render.run();
        hear.setOnClickListener(v -> speak(lines[index[0]], rate[0]));
        reveal.setOnClickListener(v -> { visible[0] = !visible[0]; render.run(); });
        shadow.setOnClickListener(v -> startSpeechRecognition(lines[index[0]]));
        prev.setOnClickListener(v -> { if (index[0] > 0) index[0]--; visible[0] = false; render.run(); speak(lines[index[0]], rate[0]); });
        next.setOnClickListener(v -> { if (index[0] < lines.length - 1) index[0]++; visible[0] = false; render.run(); speak(lines[index[0]], rate[0]); });
        speed.setOnClickListener(v -> {
            if (rate[0] < 0.9f) rate[0] = 1.0f; else if (rate[0] < 1.1f) rate[0] = 1.15f; else if (rate[0] < 1.2f) rate[0] = 0.7f; else rate[0] = 0.85f;
            speed.setText(String.format(Locale.US, "%.2f×", rate[0]));
        });

        AlertDialog d = new AlertDialog.Builder(activity).setTitle("逐句精听")
                .setView(box).setPositiveButton("保存整段", (x,w) -> askSaveMaterial(text))
                .setNegativeButton("关闭", null).create();
        d.setOnDismissListener(x -> { if (tts != null) tts.stop(); });
        d.show();
        speak(lines[0], rate[0]);
    }

    private void openDictation(String text) {
        String[] lines = sentences(text);
        if (lines.length == 0) { toast("没有识别到可听写的英文句子"); return; }
        data.markPractice();
        final int[] index = {0};
        final float[] rate = {0.85f};
        LinearLayout box = vertical();
        TextView pos = new TextView(activity);
        pos.setTextSize(13);
        EditText input = new EditText(activity);
        input.setHint("听完后输入你听到的英文");
        input.setGravity(Gravity.TOP);
        input.setMinLines(3);
        TextView result = new TextView(activity);
        result.setTextSize(14);
        result.setTextIsSelectable(true);
        result.setPadding(0, dp(8), 0, dp(8));
        box.addView(pos); box.addView(input); box.addView(result);
        LinearLayout controls = row();
        Button hear = button("▶ 再听");
        Button check = button("检查");
        Button next = button("下一句");
        controls.addView(hear, cell()); controls.addView(check, cell()); controls.addView(next, cell());
        box.addView(controls);
        LinearLayout more = row();
        Button reveal = button("看答案");
        Button slow = button("0.85×");
        Button shadow = button("跟读");
        more.addView(reveal, cell()); more.addView(slow, cell()); more.addView(shadow, cell());
        box.addView(more);

        Runnable render = () -> {
            pos.setText("听写第 " + (index[0] + 1) + " / " + lines.length + " 句");
            input.setText(""); result.setText("");
        };
        render.run();
        hear.setOnClickListener(v -> speak(lines[index[0]], rate[0]));
        check.setOnClickListener(v -> result.setText(compareText(lines[index[0]], input.getText().toString())));
        reveal.setOnClickListener(v -> result.setText("答案：\n" + lines[index[0]]));
        next.setOnClickListener(v -> { if (index[0] < lines.length - 1) index[0]++; else index[0] = 0; render.run(); speak(lines[index[0]], rate[0]); });
        shadow.setOnClickListener(v -> startSpeechRecognition(lines[index[0]]));
        slow.setOnClickListener(v -> {
            rate[0] = rate[0] < 0.8f ? 0.85f : (rate[0] < 0.9f ? 1.0f : 0.7f);
            slow.setText(String.format(Locale.US, "%.2f×", rate[0]));
        });

        AlertDialog d = new AlertDialog.Builder(activity).setTitle("听写模式")
                .setView(box).setNegativeButton("关闭", null).create();
        d.setOnDismissListener(x -> { if (tts != null) tts.stop(); });
        d.show();
        speak(lines[0], rate[0]);
    }

    private String compareText(String target, String typed) {
        Set<String> a = words(target);
        Set<String> b = words(typed);
        if (typed == null || typed.trim().isEmpty()) return "你还没有输入内容。\n\n答案：\n" + target;
        int hit = 0;
        StringBuilder missed = new StringBuilder();
        for (String w : a) {
            if (b.contains(w)) hit++;
            else if (missed.length() < 140) missed.append(w).append(' ');
        }
        int score = a.isEmpty() ? 0 : (int) Math.round(hit * 100.0 / a.size());
        return "关键词匹配约 " + score + "%\n可能漏掉：" + (missed.length() == 0 ? "无明显关键词" : missed.toString().trim()) + "\n\n答案：\n" + target;
    }

    private void askSaveMaterial(String text) {
        EditText note = new EditText(activity);
        note.setHint("可选备注：例如 今天反复听这段");
        new AlertDialog.Builder(activity).setTitle("保存到素材库")
                .setMessage(AppData.ellipsize(currentTitle, 80))
                .setView(note)
                .setPositiveButton("保存", (d,w) -> {
                    data.addMaterial(currentTitle, text, currentUrl, note.getText().toString());
                    toast("已保存到素材库");
                }).setNegativeButton("取消", null).show();
    }

    private void choose(String[] holder, String value) {
        holder[0] = value;
        toast("AI模式：" + value);
    }

    private String buildPrompt(String material, String mode) {
        String rule;
        switch (mode) {
            case "复述问答":
                rule = "根据素材做口语复述训练。一次只问一个简短问题，必须等我回答后再纠正并继续。不要提前把答案全部告诉我。"; break;
            case "口语纠错":
                rule = "我会用英语复述或回答。请优先指出影响理解的错误、漏词和不自然表达，再给一个更自然且容易说出口的版本。一次不要纠正太多点。"; break;
            case "工程英语":
                rule = "先确保我听懂素材，再把其中最有价值的表达迁移到新加坡建筑、办公室装修和施工现场。每次只扩展少量高频表达，并要求我开口回答。"; break;
            case "高频表达":
                rule = "只提炼真正高频、值得口语输出的短语和句型。每个表达用简单中文解释，再让我用同一结构替换关键词说一个新句子。"; break;
            case "随机测验":
                rule = "把素材做成随机听力与口语测验。题型可包含听辨、补词、中文转英文、复述。每次只出一题，等我回答后再评分和纠正。"; break;
            default:
                rule = "把素材切成小段，优先训练连读、弱读、吞音、关键词和真实语速。一次只训练一小段，先让我听懂、判断或复述，再纠正。不要变成纯语法课。";
        }
        return "你是我的英语听力与口语陪练。训练模式：" + mode + "。\n" + rule +
                "\n要求：重点提升听力识别和口语输出；中文解释要直白；不要一次增加大量新词；我回答错时先指出关键问题，再让我重说一次。\n\n【本次素材】\n" + material;
    }

    private void openProvider(String url, String provider) {
        String[] actions = {"在本APP打开", "用已安装APP/浏览器打开", "分享到其他AI应用"};
        new AlertDialog.Builder(activity)
                .setTitle("打开 " + provider)
                .setMessage("完整训练指令已经复制。进入 AI 后直接粘贴发送即可；不需要在这个 APK 内保存任何 API Key。")
                .setItems(actions, (d, which) -> {
                    if (which == 0) webView.loadUrl(url);
                    else if (which == 1) {
                        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                        catch (Exception e) { toast("无法打开 " + provider); }
                    } else shareText(buildPrompt(currentMaterial, "听力精练"));
                })
                .setNegativeButton("取消", null).show();
    }

    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try { activity.startActivity(Intent.createChooser(send, "选择 AI 或聊天应用")); }
        catch (Exception e) { toast("没有可分享文本的应用"); }
    }

    private void showTtsControls(String text) {
        if (text == null || text.trim().isEmpty()) { toast("没有可朗读的素材"); return; }
        LinearLayout box = row();
        String[] names = {"0.7×", "0.85×", "1.0×", "1.2×", "停止"};
        for (String n : names) box.addView(button(n), cell());
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("素材朗读").setMessage("使用手机系统英文语音。")
                .setView(box).setNegativeButton("关闭", null).create();
        dialog.show();
        float[] rates = {0.7f, 0.85f, 1.0f, 1.2f};
        for (int i = 0; i < rates.length; i++) {
            final float rate = rates[i];
            ((Button) box.getChildAt(i)).setOnClickListener(v -> speak(text, rate));
        }
        ((Button) box.getChildAt(4)).setOnClickListener(v -> { if (tts != null) tts.stop(); });
        speak(text, 0.85f);
    }

    private void speak(String text, float rate) {
        if (!ttsReady || tts == null) { toast("手机英文 TTS 暂不可用，请检查系统语音服务"); return; }
        tts.setSpeechRate(rate);
        String limited = text.length() > 3900 ? text.substring(0, 3900) : text;
        tts.speak(limited, TextToSpeech.QUEUE_FLUSH, null, "practice");
    }

    private void startSpeechRecognition(String target) {
        pendingSpeechTarget = target == null ? "" : target.trim();
        if (pendingSpeechTarget.isEmpty()) { toast("没有可跟读的句子"); return; }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请跟读英文句子");
        try { activity.startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception e) { toast("手机没有可用的语音识别服务"); }
    }

    public void handleSpeechResult(Intent intent) {
        if (intent == null) return;
        ArrayList<String> results = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        data.markPractice();
        String spoken = results.get(0);
        String target = pendingSpeechTarget.isEmpty() ? currentMaterial : pendingSpeechTarget;
        int score = (int) Math.round(overlap(target, spoken) * 100);
        String message = "目标：\n" + target + "\n\n识别到：\n" + spoken + "\n\n关键词重合约：" + score + "%\n\n该百分比只是快速参考，不是专业发音评分。";
        new AlertDialog.Builder(activity)
                .setTitle("跟读识别结果")
                .setMessage(message)
                .setPositiveButton("交给 ChatGPT 纠错", (d,w) -> {
                    String prompt = "请比较目标句与我的语音识别结果，指出最可能的漏读、错词和不自然发音位置。用简单中文说明，然后让我重新说一次。\n\n目标句：\n" + target + "\n\n识别结果：\n" + spoken;
                    copy(prompt, "跟读纠错提示词");
                    openProvider("https://chatgpt.com/", "ChatGPT");
                })
                .setNeutralButton("再读一次", (d,w) -> startSpeechRecognition(target))
                .setNegativeButton("关闭", null).show();
    }

    private double overlap(String target, String spoken) {
        Set<String> a = words(target), b = words(spoken);
        if (a.isEmpty()) return 0;
        int hit = 0;
        for (String word : a) if (b.contains(word)) hit++;
        return hit / (double) a.size();
    }

    private Set<String> words(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) return set;
        for (String word : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']", " ").split("\\s+")) {
            if (word.length() >= 2) set.add(word);
        }
        return set;
    }

    private String[] sentences(String text) {
        if (text == null) return new String[0];
        String clean = text.replace('\r', ' ').replaceAll("[\\t ]+", " ").replaceAll("\\n+", " ").trim();
        if (clean.isEmpty()) return new String[0];
        String[] raw = clean.split("(?<=[.!?])\\s+(?=[A-Z0-9\"'])");
        ArrayList<String> out = new ArrayList<>();
        for (String s : raw) {
            String x = s.trim();
            if (x.length() >= 3) out.add(x.length() > 500 ? x.substring(0, 500) : x);
            if (out.size() >= 60) break;
        }
        if (out.isEmpty()) out.add(clean.length() > 500 ? clean.substring(0, 500) : clean);
        return out.toArray(new String[0]);
    }

    private String decode(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch (Exception e) { return value.replace("\\n", "\n").replace("\\\"", "\""); }
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        toast("已复制：" + label);
    }

    public void destroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    private TextView title(String text) {
        TextView v = new TextView(activity);
        v.setText(text); v.setTextSize(14); v.setPadding(0, dp(10), 0, dp(4));
        return v;
    }
    private LinearLayout vertical() {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(8), dp(18), dp(8)); return box;
    }
    private LinearLayout row() { LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); return row; }
    private Button button(String text) { Button b = new Button(activity); b.setText(text); b.setTextSize(11); b.setAllCaps(false); b.setMinWidth(0); b.setMinHeight(0); return b; }
    private LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(0, dp(44), 1f); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
