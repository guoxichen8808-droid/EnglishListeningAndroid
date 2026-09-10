package com.englishlistening.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private AppData data;
    private EditText search;
    private ListView list;
    private TextView empty;
    private final ArrayList<JSONObject> visible = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(Color.rgb(247,248,250));
        data = new AppData(this);
        buildUi();
        refresh("");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(5), dp(8), dp(5));
        top.setBackgroundColor(Color.rgb(247,248,250));
        Button back = button("←", 22);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(50), dp(46)));
        TextView title = new TextView(this);
        title.setText("历史记录");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button clear = button("清理", 12);
        clear.setOnClickListener(v -> confirmClear());
        top.addView(clear, new LinearLayout.LayoutParams(dp(64), dp(46)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索历史，例如 BBC / ceiling");
        search.setTextSize(14);
        search.setPadding(dp(14),0,dp(14),0);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        list = new ListView(this);
        list.setDividerHeight(1);
        list.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject o = visible.get(position);
            String url = o.optString("url");
            if (!AppData.isHttp(url)) return;
            Intent i = new Intent(this, PracticalMainActivityV12.class);
            i.putExtra("openUrl", url);
            startActivity(i);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            JSONObject o = visible.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(AppData.ellipsize(o.optString("title", "历史"), 60))
                    .setMessage(o.optString("url"))
                    .setPositiveButton("打开", (d,w) -> {
                        Intent i = new Intent(this, PracticalMainActivityV12.class);
                        i.putExtra("openUrl", o.optString("url"));
                        startActivity(i);
                    })
                    .setNegativeButton("关闭", null).show();
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new TextView(this);
        empty.setText("还没有历史记录");
        empty.setGravity(Gravity.CENTER);
        empty.setTextSize(15);
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (search != null) refresh(search.getText().toString());
    }

    private void refresh(String query) {
        visible.clear();
        JSONArray arr = data.history();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> labels = new ArrayList<>();
        for (int i=0;i<arr.length();i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String all = (o.optString("title") + " " + o.optString("url") + " " + o.optString("host")).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !all.contains(q)) continue;
            visible.add(o);
            labels.add(AppData.ellipsize(o.optString("title", o.optString("url")), 58) + "\n" +
                    fmt.format(new Date(o.optLong("time",0))) + " · " + o.optInt("visits",1) + "次 · " + AppData.compactHost(o.optString("url")));
        }
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView)super.getView(position, convertView, parent);
                v.setTextSize(14);
                v.setPadding(dp(16),dp(11),dp(12),dp(11));
                return v;
            }
        });
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClear() {
        if (data.history().length() == 0) { toast("没有可清理的历史记录"); return; }
        new AlertDialog.Builder(this).setTitle("清空历史记录？")
                .setMessage("收藏、素材和下载不会删除。")
                .setPositiveButton("清空", (d,w) -> { data.clearHistory(); refresh(search.getText().toString()); toast("历史已清空"); })
                .setNegativeButton("取消", null).show();
    }

    private Button button(String text, int size) { Button b=new Button(this); b.setText(text); b.setTextSize(size); b.setAllCaps(false); b.setMinWidth(0); b.setMinHeight(0); return b; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
