package com.englishlistening.browser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class AudioPlayerDialog {
    private static final String PREFS = "english_audio_positions";
    private final Context context;
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressTask;
    private long loopA = -1;
    private long loopB = -1;
    private String currentSource = "";
    private float currentSpeed = 1.0f;

    public AudioPlayerDialog(Context context) { this.context = context; }

    public void show(String source, String title) {
        stop();
        currentSource = source == null ? "" : source;
        currentSpeed = loadSpeed();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView status = new TextView(context);
        status.setText((title == null ? "音频" : title) + "\n正在准备…");
        status.setTextSize(15);
        box.addView(status);

        SeekBar seek = new SeekBar(context);
        seek.setMax(1000);
        box.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout controls = row();
        Button back10 = button("-10秒");
        Button back5 = button("-5秒");
        Button play = button("播放/暂停");
        Button forward10 = button("+10秒");
        controls.addView(back10, cell()); controls.addView(back5, cell()); controls.addView(play, cell()); controls.addView(forward10, cell());
        box.addView(controls);

        LinearLayout speeds = row();
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] speedNames = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        for (int i = 0; i < speedNames.length; i++) {
            final float speed = speedValues[i];
            Button b = button(speedNames[i]);
            b.setOnClickListener(v -> setSpeed(speed));
            speeds.addView(b, cell());
        }
        box.addView(speeds);

        LinearLayout loops = row();
        Button a = button("设A点");
        Button b = button("设B点");
        Button goA = button("回A点");
        Button clear = button("清除AB");
        loops.addView(a, cell()); loops.addView(b, cell()); loops.addView(goA, cell()); loops.addView(clear, cell());
        box.addView(loops);

        TextView tip = new TextView(context);
        tip.setText("关闭面板后音频可继续播放；再次打开同一音频会尽量从上次位置继续。A-B 循环适合反复听一句。\n");
        tip.setTextSize(11);
        box.addView(tip);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("听力播放器")
                .setView(box)
                .setPositiveButton("停止播放", (d,w) -> stop())
                .setNegativeButton("关闭面板", null)
                .create();
        dialog.show();

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            if (currentSource.startsWith("content://")) player.setDataSource(context, Uri.parse(currentSource));
            else player.setDataSource(currentSource);
            player.setOnPreparedListener(mp -> {
                int saved = loadPosition(currentSource);
                if (saved > 0 && saved < mp.getDuration() - 3000) mp.seekTo(saved);
                setSpeed(currentSpeed);
                mp.start();
                startProgress(seek, status, title == null ? "音频" : title);
            });
            player.setOnCompletionListener(mp -> savePosition(currentSource, 0));
            player.setOnErrorListener((mp, what, extra) -> {
                status.setText((title == null ? "音频" : title) + "\n播放失败");
                return false;
            });
            player.prepareAsync();
        } catch (Exception e) {
            status.setText((title == null ? "音频" : title) + "\n无法播放：" + e.getMessage());
        }

        back10.setOnClickListener(v -> seekRelative(-10000));
        back5.setOnClickListener(v -> seekRelative(-5000));
        forward10.setOnClickListener(v -> seekRelative(10000));
        play.setOnClickListener(v -> {
            if (player == null) return;
            try { if (player.isPlaying()) player.pause(); else player.start(); } catch (Exception ignored) {}
        });
        a.setOnClickListener(v -> {
            if (player == null) return;
            try { loopA = player.getCurrentPosition(); toast("A点：" + format(loopA)); } catch (Exception ignored) {}
        });
        b.setOnClickListener(v -> {
            if (player == null) return;
            try {
                loopB = player.getCurrentPosition();
                if (loopA < 0 || loopB <= loopA) toast("请先设A点，再在后面设B点"); else toast("AB循环已启用");
            } catch (Exception ignored) {}
        });
        goA.setOnClickListener(v -> { if (player != null && loopA >= 0) { try { player.seekTo((int) loopA); } catch (Exception ignored) {} } });
        clear.setOnClickListener(v -> { loopA = -1; loopB = -1; toast("AB循环已清除"); });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || player == null) return;
                try { player.seekTo((int) (player.getDuration() * (progress / 1000f))); } catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveCurrentPosition(); }
        });
    }

    private void startProgress(SeekBar seek, TextView status, String title) {
        if (progressTask != null) handler.removeCallbacks(progressTask);
        progressTask = new Runnable() {
            @Override public void run() {
                if (player != null) {
                    try {
                        int position = player.getCurrentPosition();
                        int duration = Math.max(1, player.getDuration());
                        if (loopA >= 0 && loopB > loopA && position >= loopB) {
                            player.seekTo((int) loopA); position = (int) loopA;
                        }
                        seek.setProgress((int) (position * 1000L / duration));
                        String ab = loopA >= 0 && loopB > loopA ? "  AB " + format(loopA) + "–" + format(loopB) : "";
                        status.setText(title + "\n" + format(position) + " / " + format(duration) + "  " + trimSpeed(currentSpeed) + "×" + ab);
                        if (position % 5000 < 400) savePosition(currentSource, position);
                    } catch (Exception ignored) {}
                }
                handler.postDelayed(this, 300);
            }
        };
        handler.post(progressTask);
    }

    private void seekRelative(int delta) {
        if (player == null) return;
        try {
            int pos = player.getCurrentPosition(), duration = player.getDuration();
            player.seekTo(Math.max(0, Math.min(duration, pos + delta))); saveCurrentPosition();
        } catch (Exception ignored) {}
    }

    private void setSpeed(float speed) {
        currentSpeed = speed;
        saveSpeed(speed);
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            if (player.isPlaying()) player.start();
            toast("播放速度 " + trimSpeed(speed) + "×");
        } catch (Exception ignored) {}
    }

    private String trimSpeed(float s) {
        if (Math.abs(s - Math.round(s)) < .01f) return String.valueOf(Math.round(s));
        return String.valueOf(s);
    }

    private void saveCurrentPosition() {
        if (player == null || currentSource.isEmpty()) return;
        try { savePosition(currentSource, player.getCurrentPosition()); } catch (Exception ignored) {}
    }

    public void stop() {
        saveCurrentPosition();
        if (progressTask != null) handler.removeCallbacks(progressTask);
        progressTask = null; loopA = -1; loopB = -1;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release(); player = null;
        }
    }

    private SharedPreferences prefs() { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private String key(String source) { return "p_" + Integer.toHexString(source == null ? 0 : source.hashCode()); }
    private int loadPosition(String source) { return prefs().getInt(key(source), 0); }
    private void savePosition(String source, int position) { if (source != null && !source.isEmpty()) prefs().edit().putInt(key(source), Math.max(0, position)).apply(); }
    private float loadSpeed() { return prefs().getFloat("speed", 1.0f); }
    private void saveSpeed(float speed) { prefs().edit().putFloat("speed", speed).apply(); }

    private LinearLayout row() { LinearLayout row = new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL); return row; }
    private Button button(String text) { Button b = new Button(context); b.setText(text); b.setTextSize(10); b.setAllCaps(false); b.setMinWidth(0); b.setMinHeight(0); return b; }
    private LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(0, dp(44), 1f); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private String format(long ms) { long total = Math.max(0, ms / 1000); return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60); }
    private void toast(String t) { Toast.makeText(context, t, Toast.LENGTH_SHORT).show(); }
}
