package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UltimateTools {
    public static final int BACKUP_EXPORT_REQUEST = 704;
    public static final int BACKUP_IMPORT_REQUEST = 705;

    private final Activity activity;
    private final WebView webView;
    private final AppData data;
    private final AiPracticeHelper practice;
    private String mobileUserAgent;
    private boolean desktopMode = false;

    public UltimateTools(Activity activity, WebView webView, AppData data, AiPracticeHelper practice) {
        this.activity = activity;
        this.webView = webView;
        this.data = data;
        this.practice = practice;
        this.mobileUserAgent = webView.getSettings().getUserAgentString();
    }

    public void showMaterials() {
        JSONArray arr = data.materials();
        if (arr.length() == 0) {
            new AlertDialog.Builder(activity).setTitle("素材库")
                    .setMessage("还没有保存的学习素材。\n\n在网页中点“训练”，或在更多菜单选择“保存网页文字到素材库”。")
                    .setPositiveButton("训练当前页", (d,w) -> practice.openFromCurrentPage())
                    .setNegativeButton("关闭", null).show();
            return;
        }
        String[] labels = new String[arr.length()];
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) { labels[i] = ""; continue; }
            String title = AppData.ellipsize(o.optString("title", "学习素材"), 40);
            String note = o.optString("note", "");
            labels[i] = title + "\n" + fmt.format(new Date(o.optLong("time", 0))) + (note.isEmpty() ? "" : " · " + AppData.ellipsize(note, 24));
        }
        new AlertDialog.Builder(activity).setTitle("素材库 · " + arr.length() + " 条")
                .setItems(labels, (d, which) -> showMaterialActions(arr.optJSONObject(which), which))
                .setNeutralButton("清空素材库", (d,w) -> confirmClearMaterials())
                .setNegativeButton("关闭", null).show();
    }

    private void showMaterialActions(JSONObject item, int index) {
        if (item == null) return;
        String[] actions = {"开始训练", "查看全文", "打开来源网页", "复制素材", "删除"};
        new AlertDialog.Builder(activity).setTitle(AppData.ellipsize(item.optString("title", "学习素材"), 55))
                .setItems(actions, (d, which) -> {
                    String text = item.optString("text", "");
                    String url = item.optString("url", "");
                    if (which == 0) practice.openMaterial(item.optString("title"), text, url);
                    else if (which == 1) showTextViewer(item.optString("title"), text);
                    else if (which == 2) { if (AppData.isHttp(url)) webView.loadUrl(url); else toast("没有保存来源网址"); }
                    else if (which == 3) copy(text, "学习素材");
                    else {
                        data.removeMaterial(index);
                        toast("素材已删除");
                    }
                }).setNegativeButton("返回", null).show();
    }

    private void showTextViewer(String title, String text) {
        ScrollView scroll = new ScrollView(activity);
        TextView tv = new TextView(activity);
        tv.setText(text); tv.setTextSize(17); tv.setTextIsSelectable(true); tv.setLineSpacing(0, 1.25f); tv.setPadding(dp(18), dp(10), dp(18), dp(18));
        scroll.addView(tv);
        new AlertDialog.Builder(activity).setTitle(AppData.ellipsize(title, 55)).setView(scroll)
                .setPositiveButton("训练这段", (d,w) -> practice.openMaterial(title, text, ""))
                .setNegativeButton("关闭", null).show();
    }

    private void confirmClearMaterials() {
        new AlertDialog.Builder(activity).setTitle("清空素材库？")
                .setMessage("收藏、历史和下载文件不会删除。")
                .setPositiveButton("清空", (d,w) -> { data.clearMaterials(); toast("素材库已清空"); })
                .setNegativeButton("取消", null).show();
    }

    public void showStats() {
        JSONObject s = data.stats();
        long todayMin = s.optLong("todaySeconds", 0) / 60;
        long totalMin = s.optLong("totalSeconds", 0) / 60;
        int todayPractice = s.optInt("todayPractices", 0);
        int totalPractice = s.optInt("totalPractices", 0);
        int streak = s.optInt("streak", 0);
        int goal = data.dailyGoalMinutes();
        int pct = (int) Math.min(100, todayMin * 100 / Math.max(1, goal));
        String text = "今日学习：" + todayMin + " 分钟 / 目标 " + goal + " 分钟\n" +
                "今日训练：" + todayPractice + " 次\n" +
                "今日完成：" + pct + "%\n\n" +
                "连续学习：" + streak + " 天\n" +
                "累计学习：" + totalMin + " 分钟\n" +
                "累计训练：" + totalPractice + " 次\n\n" +
                "说明：学习时间按 APP 前台使用时长统计，训练次数按逐句精听、听写、跟读和 AI 练习启动次数统计。";
        new AlertDialog.Builder(activity).setTitle("学习统计").setMessage(text)
                .setPositiveButton("调整每日目标", (d,w) -> showSettings())
                .setNegativeButton("关闭", null).show();
    }

    public void showShortcuts() {
        JSONArray arr = data.shortcuts();
        LinearLayout box = vertical();
        TextView tip = new TextView(activity);
        tip.setText("可以把任何常用学习网站加入首页。最多 12 个。当前已有 " + arr.length() + " 个。\n");
        box.addView(tip);
        Button addCurrent = button("把当前网页加入首页");
        Button addManual = button("手动添加网站");
        Button manage = button("管理 / 删除快捷入口");
        box.addView(addCurrent); box.addView(addManual); box.addView(manage);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("首页网站管理").setView(box)
                .setNegativeButton("关闭", null).create();
        dialog.show();
        addCurrent.setOnClickListener(v -> addCurrentShortcut());
        addManual.setOnClickListener(v -> addManualShortcut());
        manage.setOnClickListener(v -> showShortcutManager());
    }

    public void addCurrentShortcut() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url) || AppData.isAiUrl(url)) { toast("当前页面不能加入首页"); return; }
        if (data.addShortcut(webView.getTitle(), url)) toast("已加入首页网站");
        else toast(data.hasShortcut(url) ? "这个网站已经在首页" : "首页快捷入口已满（最多12个）");
    }

    private void addManualShortcut() {
        LinearLayout box = vertical();
        EditText name = new EditText(activity); name.setHint("名称，例如 Cambridge Dictionary");
        EditText url = new EditText(activity); url.setHint("https://..."); url.setSingleLine(true);
        box.addView(name); box.addView(url);
        new AlertDialog.Builder(activity).setTitle("添加网站").setView(box)
                .setPositiveButton("添加", (d,w) -> {
                    String u = url.getText().toString().trim();
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    if (data.addShortcut(name.getText().toString(), u)) toast("已加入首页"); else toast("网址无效、重复或数量已满");
                }).setNegativeButton("取消", null).show();
    }

    private void showShortcutManager() {
        JSONArray arr = data.shortcuts();
        if (arr.length() == 0) { toast("还没有自定义网站"); return; }
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = "删除：" + (o == null ? "" : AppData.ellipsize(o.optString("name", "网站"), 38));
        }
        new AlertDialog.Builder(activity).setTitle("删除首页网站").setItems(labels, (d, which) -> {
            data.removeShortcut(which); toast("已删除快捷入口");
        }).setNegativeButton("取消", null).show();
    }

    public void showSettings() {
        LinearLayout box = vertical();
        TextView goalLabel = new TextView(activity); goalLabel.setText("每日学习目标（分钟）");
        EditText goal = new EditText(activity); goal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); goal.setText(String.valueOf(data.dailyGoalMinutes())); goal.setSingleLine(true);
        CheckBox resume = new CheckBox(activity); resume.setText("启动 APP 时自动继续上次网页"); resume.setChecked(data.autoResume());
        box.addView(goalLabel); box.addView(goal); box.addView(resume);
        new AlertDialog.Builder(activity).setTitle("学习设置").setView(box)
                .setPositiveButton("保存", (d,w) -> {
                    int minutes = 20;
                    try { minutes = Integer.parseInt(goal.getText().toString().trim()); } catch (Exception ignored) {}
                    data.setDailyGoalMinutes(minutes); data.setAutoResume(resume.isChecked()); toast("设置已保存");
                }).setNegativeButton("取消", null).show();
    }

    public void showBackupActions() {
        String[] items = {"导出全部学习数据", "从备份恢复学习数据"};
        new AlertDialog.Builder(activity).setTitle("备份与恢复")
                .setMessage("备份包含收藏、历史、素材库、下载记录、自定义网站和学习统计；不包含网页登录密码/Cookie，也不复制已下载的音频文件本体。")
                .setItems(items, (d, which) -> { if (which == 0) startExport(); else startImport(); })
                .setNegativeButton("关闭", null).show();
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "EnglishListening_Ultimate_Backup_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + ".json");
        activity.startActivityForResult(intent, BACKUP_EXPORT_REQUEST);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
        activity.startActivityForResult(intent, BACKUP_IMPORT_REQUEST);
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != BACKUP_EXPORT_REQUEST && requestCode != BACKUP_IMPORT_REQUEST) return false;
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) return true;
        Uri uri = intent.getData();
        if (requestCode == BACKUP_EXPORT_REQUEST) {
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("无法写入文件");
                out.write(data.exportAll().getBytes(StandardCharsets.UTF_8)); out.flush();
                toast("学习数据备份完成");
            } catch (Exception e) { toast("备份失败：" + e.getMessage()); }
        } else {
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("无法读取文件");
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null && sb.length() < 5_000_000) sb.append(line).append('\n');
                if (data.importAll(sb.toString())) toast("学习数据已恢复"); else toast("这不是有效的 EnglishListening 备份文件");
            } catch (Exception e) { toast("恢复失败：" + e.getMessage()); }
        }
        return true;
    }

    public void showReaderMode() {
        practice.extractCurrentPage(text -> {
            if (text == null || text.trim().isEmpty()) { toast("当前页面没有提取到正文"); return; }
            ScrollView scroll = new ScrollView(activity);
            TextView tv = new TextView(activity); tv.setText(text); tv.setTextSize(18); tv.setTextIsSelectable(true); tv.setLineSpacing(0, 1.3f); tv.setPadding(dp(18), dp(10), dp(18), dp(18)); scroll.addView(tv);
            AlertDialog d = new AlertDialog.Builder(activity).setTitle("纯文本阅读")
                    .setView(scroll).setPositiveButton("训练这段", null).setNeutralButton("保存素材", null).setNegativeButton("关闭", null).create();
            d.setOnShowListener(x -> {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { d.dismiss(); practice.openMaterial(webView.getTitle(), text, webView.getUrl()); });
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { data.addMaterial(webView.getTitle(), text, webView.getUrl(), "纯文本阅读保存"); toast("已保存到素材库"); });
            });
            d.show();
        });
    }

    public void findInPage() {
        EditText input = new EditText(activity); input.setHint("输入网页内要查找的英文"); input.setSingleLine(true);
        AlertDialog d = new AlertDialog.Builder(activity).setTitle("网页内查找").setView(input)
                .setPositiveButton("查找", null).setNeutralButton("下一个", null).setNegativeButton("关闭", null).create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String q = input.getText().toString().trim(); if (!q.isEmpty()) webView.findAllAsync(q);
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> webView.findNext(true));
        });
        d.setOnDismissListener(x -> webView.clearMatches()); d.show();
    }

    public void shareCurrentPage() {
        String url = webView.getUrl();
        if (!AppData.isHttp(url)) { toast("当前不是可分享网页"); return; }
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, webView.getTitle()); send.putExtra(Intent.EXTRA_TEXT, AppData.safeTitle(webView.getTitle(), "") + "\n" + url);
        try { activity.startActivity(Intent.createChooser(send, "分享网页")); } catch (Exception e) { toast("没有可分享的应用"); }
    }

    public void toggleDesktopMode() {
        WebSettings s = webView.getSettings();
        desktopMode = !desktopMode;
        if (desktopMode) {
            s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36");
            s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        } else s.setUserAgentString(mobileUserAgent);
        webView.reload(); toast(desktopMode ? "已切换电脑网页模式" : "已恢复手机网页模式");
    }

    public void openAndroidWebViewSettings() {
        try { activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()))); }
        catch (Exception e) { toast("无法打开系统设置"); }
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text)); toast("已复制：" + label);
    }
    private LinearLayout vertical() { LinearLayout b = new LinearLayout(activity); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(dp(18), dp(8), dp(18), dp(8)); return b; }
    private Button button(String text) { Button b = new Button(activity); b.setText(text); b.setAllCaps(false); return b; }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }
    private void toast(String t) { Toast.makeText(activity, t, Toast.LENGTH_LONG).show(); }
}
