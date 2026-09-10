package com.englishlistening.browser;

import android.app.Activity;
import android.media.MediaRecorder;

import java.io.File;

public class VoiceRecorder {
    private final Activity activity;
    private MediaRecorder recorder;
    private File outputFile;
    private boolean recording;

    public VoiceRecorder(Activity activity) {
        this.activity = activity;
    }

    public boolean isRecording() {
        return recording;
    }

    public File start() throws Exception {
        if (recording) return outputFile;
        outputFile = new File(activity.getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(16000);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.prepare();
        recorder.start();
        recording = true;
        return outputFile;
    }

    public File stop() throws Exception {
        if (!recording) return outputFile;
        recording = false;
        Exception failure = null;
        try {
            recorder.stop();
        } catch (Exception e) {
            failure = e;
        }
        try {
            recorder.reset();
            recorder.release();
        } catch (Exception ignored) {}
        recorder = null;
        if (failure != null) {
            if (outputFile != null) outputFile.delete();
            throw failure;
        }
        if (outputFile == null || !outputFile.exists() || outputFile.length() < 1500) {
            if (outputFile != null) outputFile.delete();
            throw new IllegalStateException("录音太短，请重新说");
        }
        return outputFile;
    }

    public void cancel() {
        recording = false;
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.reset(); recorder.release(); } catch (Exception ignored) {}
        }
        recorder = null;
        if (outputFile != null) outputFile.delete();
        outputFile = null;
    }
}
