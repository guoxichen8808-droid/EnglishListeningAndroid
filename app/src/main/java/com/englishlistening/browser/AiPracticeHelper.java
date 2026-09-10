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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AiPracticeHelper {
    public static final int SPEECH_REQUEST = 703;

    private final Activity activity;
    private final WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String currentMaterial = "";

    public AiPracticeHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        tts = new TextToSpeech(activity, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    public void openFromCurrentPage() {
        String url = webView.getUrl();
        if (AppData.isHttp(url) && !AppData.isAiUrl(url)) {
            String js = "(function(){var s='';try{s=window.getSelection?window.getSelection().toString():'';}catch(e){};if(!s){try{s=document.body?document.body.innerText:'';}catch(e){}};return s.substring(0,6500);})()";
            webView.evaluateJavascript(js, value -> showDialog(decode(value)));
        } else showDialog("");
    }

    public void showDialog(String extracted) {
        currentMaterial = extracted == null ? "" : extracted.trim();
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), 0);

        TextView tip = new TextView(activity);
        tip.setText("APP 会读取当前网页文字。若你先在网页选中一句/一段，再点 AI练习，会优先使用选中的内容。");
        tip.setTextSize(12);
        box.addView(tip);

        EditText material = new EditText(activity);
        material.setHint("练习素材：可粘贴英文句子、对话或文章");
        material.setText(currentMaterial);
        material.setGravity(Gravity.TOP);
        material.setMinLines(5);
        material.setMaxLines(10);
        box.addView(material, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

        TextView modeTitle = new TextView(activity);
        modeTitle.setText("练习模式（默认：听力精练）");
        modeTitle.setTextSize(14);
        modeTitle.setPadding(0, dp(6), 0, dp(3));
        box.addView(modeTitle);

        LinearLayout modes = row();
        Button listening = button("听力精练");
        Button shadow = button("跟读纠错");
        Button retell = button("复述问答");
        Button engineering = button("工程英语");
        modes.addView(listening, cell());
        modes.addView(shadow, cell());
        modes.addView(retell, cell());
        modes.addView(engineering, cell());
        box.addView(modes);

        LinearLayout local = row();
        Button read = button("朗读素材");
        Button speech = button("跟读识别");
        Button stopRead = button("停止朗读");
        local.addView(read, cell());
        local.addView(speech, cell());
        local.addView(stopRead, cell());
        box.addView(local);

        final String[] mode = {"听力精练"};
        listening.setOnClickListener(v -> choose(mode, "听力精练"));
        shadow.setOnClickListener(v -> choose(mode, "跟读纠错"));
        retell.setOnClickListener(v -> choose(mode, "复述问答"));
        engineering.setOnClickListener(v -> choose(mode, "工程英语"));
        read.setOnClickListener(v -> {
            currentMaterial = material.getText().toString().trim();
            showTtsControls(currentMaterial);
        });
        speech.setOnClickListener(v -> {
            currentMaterial = material.getText().toString().trim();
            startSpeechRecognition();
        });
        stopRead.setOnClickListener(v -> { if (tts != null) tts.stop(); });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("AI 英语陪练")
                .setView(box)
                .setPositiveButton("ChatGPT", null)
                .setNeutralButton("豆包", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = material.getText().toString().trim();
                if (text.isEmpty()) { toast("请先准备练习素材"); return; }
                currentMaterial = text;
                copy(buildPrompt(text, mode[0]), "AI练习提示词");
                openProvider("https://chatgpt.com/studymode", "ChatGPT");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String text = material.getText().toString().trim();
                if (text.isEmpty()) { toast("请先准备练习素材"); return; }
                currentMaterial = text;
                copy(buildPrompt(text, mode[0]), "AI练习提示词");
                openProvider("https://www.doubao.com/chat/", "豆包");
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(dialog.getWindow().getAttributes());
            p.width = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setAttributes(p);
        }
    }

    private void choose(String[] holder, String value) {
        holder[0] = value;
        toast("已选：" + value);
    }

    private String buildPrompt(String material, String mode) {
        String rule;
        switch (mode) {
            case "跟读纠错":
                rule = "我会跟读或复述这段素材。请一次只处理一小段：先让我读/说，再根据我发来的内容指出听错、漏词、语法和不自然表达，并给出更自然版本。重点帮助我听懂和说出来，不要一次加入太多新词。";
                break;
            case "复述问答":
                rule = "请根据素材和我进行口语复述训练。一次只问一个简短问题，等我回答后再纠正并继续。优先使用素材已有词汇，不要一次把答案全部给我。";
                break;
            case "工程英语":
                rule = "请先帮我听懂和掌握素材中的核心表达，再把最有价值的表达迁移到新加坡建筑/装修现场。每次只扩展少量高频表达，并让我用口语回答或复述。";
                break;
            default:
                rule = "请把素材切成适合听力训练的小段。先挑出最需要听力识别的连读、弱读、短语和关键词，然后一次只训练一小段：先提问让我听懂/判断/复述，再给纠正。重点是听力识别和口语输出，不要变成单纯语法课。";
        }
        return "你是我的英语听力与口语陪练。训练模式：" + mode + "。\n" + rule +
                "\n如果我回答错，请用简单中文解释原因，并保留关键英文。\n\n【本次素材】\n" + material;
    }

    private void openProvider(String url, String provider) {
        new AlertDialog.Builder(activity)
                .setTitle("打开 " + provider)
                .setMessage("练习素材和指令已经复制。进入后长按输入框→粘贴→发送。\n\n这样不需要 API Key，也不会把付费密钥放进 APK。")
                .setPositiveButton("在本 APP 打开", (d, w) -> webView.loadUrl(url))
                .setNeutralButton("官方APP/浏览器", (d, w) -> {
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception e) { toast("无法打开 " + provider); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTtsControls(String text) {
        if (text == null || text.trim().isEmpty()) { toast("没有可朗读的素材"); return; }
        LinearLayout box = row();
        String[] names = {"0.75×", "1.0×", "1.25×", "停止"};
        for (String n : names) box.addView(button(n), cell());
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("素材朗读")
                .setMessage("使用手机系统英文语音。切换速度后会从头重新朗读。")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        dialog.show();
        float[] rates = {0.75f, 1.0f, 1.25f};
        for (int i = 0; i < rates.length; i++) {
            final float rate = rates[i];
            ((Button) box.getChildAt(i)).setOnClickListener(v -> speak(text, rate));
        }
        ((Button) box.getChildAt(3)).setOnClickListener(v -> { if (tts != null) tts.stop(); });
        speak(text, 1.0f);
    }

    private void speak(String text, float rate) {
        if (!ttsReady || tts == null) { toast("手机英文 TTS 暂不可用，请检查系统语音服务"); return; }
        tts.setSpeechRate(rate);
        String limited = text.length() > 3500 ? text.substring(0, 3500) : text;
        tts.speak(limited, TextToSpeech.QUEUE_FLUSH, null, "practice");
    }

    private void startSpeechRecognition() {
        if (currentMaterial == null || currentMaterial.trim().isEmpty()) { toast("请先准备素材"); return; }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请跟读或复述英文");
        try { activity.startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception e) { toast("手机没有可用的语音识别服务"); }
    }

    public void handleSpeechResult(Intent data) {
        if (data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String spoken = results.get(0);
        double score = overlap(currentMaterial, spoken);
        String message = "识别到：\n" + spoken + "\n\n与素材关键词重合约：" + Math.round(score * 100) + "%\n\n这个百分比只作为快速跟读参考，不是正式发音评分。可以继续交给 AI 判断漏词、听错和不自然表达。";
        new AlertDialog.Builder(activity)
                .setTitle("跟读识别结果")
                .setMessage(message)
                .setPositiveButton("发给 ChatGPT", (d, w) -> {
                    String prompt = "请比较我的目标素材和语音识别结果，重点指出我可能听错、漏读或说错的地方，并用简单中文解释。\n\n目标素材：\n" + currentMaterial + "\n\n我的识别结果：\n" + spoken;
                    copy(prompt, "跟读纠错提示词");
                    openProvider("https://chatgpt.com/studymode", "ChatGPT");
                })
                .setNeutralButton("复制识别结果", (d, w) -> copy(spoken, "识别结果"))
                .setNegativeButton("关闭", null)
                .show();
    }

    private double overlap(String target, String spoken) {
        Set<String> a = words(target);
        Set<String> b = words(spoken);
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button button(String text) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams cell() {
        return new LinearLayout.LayoutParams(0, dp(42), 1f);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
