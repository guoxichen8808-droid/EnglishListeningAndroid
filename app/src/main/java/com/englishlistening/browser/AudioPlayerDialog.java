package com.englishlistening.browser;

import android.app.AlertDialog;
import android.content.Context;
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
    private final Context context;
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressTask;
    private long loopA = -1;
    private long loopB = -1;

    public AudioPlayerDialog(Context context) {
        this.context = context;
    }

    public void show(String source, String title) {
        stop();
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
        Button back = button("-5秒");
        Button play = button("播放/暂停");
        Button forward = button("+10秒");
        controls.addView(back, cell());
        controls.addView(play, cell());
        controls.addView(forward, cell());
        box.addView(controls);

        LinearLayout speeds = row();
        float[] speedValues = {0.75f, 1.0f, 1.25f, 1.5f};
        String[] speedNames = {"0.75×", "1.0×", "1.25×", "1.5×"};
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
        Button clear = button("清除AB");
        loops.addView(a, cell());
        loops.addView(b, cell());
        loops.addView(clear, cell());
        box.addView(loops);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("听力播放器")
                .setView(box)
                .setNegativeButton("关闭面板", null)
                .create();
        dialog.show();

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            if (source != null && source.startsWith("content://")) player.setDataSource(context, Uri.parse(source));
            else player.setDataSource(source);
            player.setOnPreparedListener(mp -> {
                mp.start();
                startProgress(seek, status, title == null ? "音频" : title);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                status.setText((title == null ? "音频" : title) + "\n播放失败");
                return false;
            });
            player.prepareAsync();
        } catch (Exception e) {
            status.setText((title == null ? "音频" : title) + "\n无法播放：" + e.getMessage());
        }

        back.setOnClickListener(v -> seekRelative(-5000));
        forward.setOnClickListener(v -> seekRelative(10000));
        play.setOnClickListener(v -> {
            if (player == null) return;
            try {
                if (player.isPlaying()) player.pause(); else player.start();
            } catch (Exception ignored) {}
        });
        a.setOnClickListener(v -> {
            if (player == null) return;
            try {
                loopA = player.getCurrentPosition();
                Toast.makeText(context, "A点：" + format(loopA), Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        });
        b.setOnClickListener(v -> {
            if (player == null) return;
            try {
                loopB = player.getCurrentPosition();
                if (loopA < 0 || loopB <= loopA) Toast.makeText(context, "请先设A点，再在后面设B点", Toast.LENGTH_LONG).show();
                else Toast.makeText(context, "AB循环已启用", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        });
        clear.setOnClickListener(v -> {
            loopA = -1;
            loopB = -1;
            Toast.makeText(context, "AB循环已清除", Toast.LENGTH_SHORT).show();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || player == null) return;
                try {
                    int duration = player.getDuration();
                    player.seekTo((int) (duration * (progress / 1000f)));
                } catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
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
                            player.seekTo((int) loopA);
                            position = (int) loopA;
                        }
                        seek.setProgress((int) (position * 1000L / duration));
                        String ab = loopA >= 0 && loopB > loopA ? "  AB " + format(loopA) + "–" + format(loopB) : "";
                        status.setText(title + "\n" + format(position) + " / " + format(duration) + ab);
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
            int pos = player.getCurrentPosition();
            int duration = player.getDuration();
            player.seekTo(Math.max(0, Math.min(duration, pos + delta)));
        } catch (Exception ignored) {}
    }

    private void setSpeed(float speed) {
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            Toast.makeText(context, "播放速度 " + speed + "×", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "当前音频不支持变速", Toast.LENGTH_SHORT).show();
        }
    }

    public void stop() {
        if (progressTask != null) handler.removeCallbacks(progressTask);
        progressTask = null;
        loopA = -1;
        loopB = -1;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button button(String text) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams cell() {
        return new LinearLayout.LayoutParams(0, dp(44), 1f);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String format(long ms) {
        long total = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60);
    }
}
