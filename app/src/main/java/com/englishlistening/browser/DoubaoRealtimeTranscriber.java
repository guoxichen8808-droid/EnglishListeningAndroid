package com.englishlistening.browser;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class DoubaoRealtimeTranscriber {
    public interface Callback {
        void onStatus(String message);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final String WS_URL = "wss://ai-gateway.vei.volces.com/v1/realtime?model=bigmodel";

    private final Activity activity;
    private final SecureDoubaoKeyStore keyStore;
    private final OkHttpClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean active;
    private volatile boolean stopping;
    private AudioRecord recorder;
    private WebSocket socket;
    private Thread audioThread;
    private Callback callback;
    private String lastTranscript = "";

    public DoubaoRealtimeTranscriber(Activity activity, SecureDoubaoKeyStore keyStore) {
        this.activity = activity;
        this.keyStore = keyStore;
        this.client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
    }

    public boolean isActive() { return active; }

    public void start(Callback cb) {
        if (active) return;
        String key = keyStore.load();
        if (key == null || key.trim().isEmpty()) {
            cb.onError("还没有配置豆包实时语音识别 Key");
            return;
        }
        callback = cb;
        active = true;
        stopping = false;
        lastTranscript = "";
        cb.onStatus("正在连接豆包实时语音识别…");

        Request req = new Request.Builder()
                .url(WS_URL)
                .addHeader("Authorization", "Bearer " + key.trim())
                .build();
        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    JSONObject session = new JSONObject();
                    session.put("input_audio_format", "pcm");
                    session.put("input_audio_codec", "raw");
                    session.put("input_audio_sample_rate", SAMPLE_RATE);
                    session.put("input_audio_bits", 16);
                    session.put("input_audio_channel", 1);
                    session.put("input_audio_transcription", new JSONObject().put("model", "bigmodel"));
                    JSONObject event = new JSONObject();
                    event.put("type", "transcription_session.update");
                    event.put("session", session);
                    webSocket.send(event.toString());
                    startMic(webSocket);
                } catch (Exception e) {
                    fail("豆包会话初始化失败：" + safe(e));
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject o = new JSONObject(text);
                    String type = o.optString("type", "");
                    if ("transcription_session.updated".equals(type)) {
                        postStatus("豆包已连接，正在实时识别…");
                        return;
                    }
                    if ("conversation.item.input_audio_transcription.result".equals(type)) {
                        String t = o.optString("transcript", "").trim();
                        if (!t.isEmpty()) {
                            lastTranscript = t;
                            activity.runOnUiThread(() -> { if (callback != null) callback.onPartial(t); });
                        }
                        return;
                    }
                    if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                        String t = o.optString("transcript", "").trim();
                        if (t.isEmpty()) t = lastTranscript;
                        final String result = t;
                        active = false;
                        cleanupRecorder();
                        activity.runOnUiThread(() -> {
                            if (callback != null) {
                                if (result == null || result.trim().isEmpty()) callback.onError("豆包没有识别到清晰英语，请再说一次");
                                else callback.onFinal(result.trim());
                            }
                        });
                        try { webSocket.close(1000, "done"); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                fail("豆包实时识别连接失败：" + (t == null ? "网络异常" : t.getMessage()));
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (active && !stopping) fail("豆包连接已断开");
            }
        });
    }

    private void startMic(WebSocket ws) {
        executor.execute(() -> {
            try {
                int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bufferSize = Math.max(min, 3200);
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("麦克风初始化失败");
                recorder.startRecording();
                postStatus("豆包已连接，开始说英语…");
                audioThread = new Thread(() -> {
                    byte[] buf = new byte[3200];
                    while (active && !stopping) {
                        int n;
                        try { n = recorder.read(buf, 0, buf.length); }
                        catch (Exception e) { break; }
                        if (n > 0) {
                            try {
                                String audio = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP);
                                JSONObject event = new JSONObject();
                                event.put("type", "input_audio_buffer.append");
                                event.put("event_id", UUID.randomUUID().toString());
                                event.put("audio", audio);
                                if (!ws.send(event.toString())) break;
                            } catch (Exception ignored) {}
                        }
                    }
                }, "doubao-asr-audio");
                audioThread.start();
            } catch (SecurityException e) {
                fail("没有麦克风权限");
            } catch (Exception e) {
                fail("豆包录音启动失败：" + safe(e));
            }
        });
    }

    public void stop() {
        if (!active || stopping) return;
        stopping = true;
        postStatus("正在结束录音，等待豆包最终结果…");
        cleanupRecorder();
        executor.execute(() -> {
            try {
                if (audioThread != null) audioThread.join(800);
            } catch (InterruptedException ignored) {}
            try {
                JSONObject event = new JSONObject();
                event.put("type", "input_audio_buffer.commit");
                event.put("event_id", UUID.randomUUID().toString());
                WebSocket ws = socket;
                if (ws == null || !ws.send(event.toString())) fail("豆包提交录音失败");
            } catch (Exception e) {
                fail("豆包提交录音失败：" + safe(e));
            }
        });
    }

    public void cancel() {
        active = false;
        stopping = true;
        cleanupRecorder();
        if (socket != null) {
            try { socket.cancel(); } catch (Exception ignored) {}
            socket = null;
        }
    }

    public void shutdown() {
        cancel();
        executor.shutdownNow();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void cleanupRecorder() {
        AudioRecord r = recorder;
        recorder = null;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) {}
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private void fail(String message) {
        active = false;
        stopping = true;
        cleanupRecorder();
        if (socket != null) {
            try { socket.cancel(); } catch (Exception ignored) {}
            socket = null;
        }
        activity.runOnUiThread(() -> { if (callback != null) callback.onError(message); });
    }

    private void postStatus(String message) {
        activity.runOnUiThread(() -> { if (callback != null) callback.onStatus(message); });
    }

    private String safe(Throwable t) {
        if (t == null) return "未知错误";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }
}
