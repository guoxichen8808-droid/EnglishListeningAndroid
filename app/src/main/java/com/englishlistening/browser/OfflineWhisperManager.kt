package com.englishlistening.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OfflineWhisperManager(private val activity: Activity) {
    interface Callback {
        fun onStatus(message: String)
        fun onResult(text: String)
        fun onError(message: String)
    }

    companion object {
        const val MODEL_BASE = "base.en"
        const val MODEL_SMALL = "small.en"
        private const val PREFS = "offline_whisper"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_DOWNLOAD_ID = "model_download_id"
        private const val SAMPLE_RATE = 16000
    }

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    private var rawFile: File? = null

    fun isRecording(): Boolean = recording
    fun selectedModel(): String = prefs.getString(KEY_MODEL, MODEL_SMALL) ?: MODEL_SMALL
    fun selectedModelLabel(): String = if (selectedModel() == MODEL_SMALL) "Whisper Small English · 高精度 · 约488MB" else "Whisper Base English · 较快 · 约148MB"
    fun setSelectedModel(model: String) { prefs.edit().putString(KEY_MODEL, if (model == MODEL_SMALL) MODEL_SMALL else MODEL_BASE).apply() }

    fun modelFile(model: String = selectedModel()): File {
        val dir = activity.getExternalFilesDir("models") ?: File(activity.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, if (model == MODEL_SMALL) "ggml-small.en.bin" else "ggml-base.en.bin")
    }

    fun hasModel(model: String = selectedModel()): Boolean {
        val f = modelFile(model)
        val minBytes = if (model == MODEL_SMALL) 400L * 1024 * 1024 else 120L * 1024 * 1024
        return f.exists() && f.length() > minBytes
    }

    fun downloadModel(model: String): Long {
        val normalized = if (model == MODEL_SMALL) MODEL_SMALL else MODEL_BASE
        setSelectedModel(normalized)
        val file = modelFile(normalized)
        if (file.exists()) file.delete()
        val url = if (normalized == MODEL_SMALL) "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin" else "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(if (normalized == MODEL_SMALL) "Whisper Small English" else "Whisper Base English")
            .setDescription("英语听力 APP 离线语音识别模型")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(activity, "models", file.name)
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun downloadStatus(): String {
        if (hasModel()) return "已安装"
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0) return "未安装"
        return try {
            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c != null && c.moveToFirst()) {
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> if (hasModel()) "已安装" else "下载完成但文件无效"
                        DownloadManager.STATUS_RUNNING -> "正在下载"
                        DownloadManager.STATUS_PENDING -> "等待下载"
                        DownloadManager.STATUS_PAUSED -> "下载已暂停"
                        DownloadManager.STATUS_FAILED -> "下载失败"
                        else -> "未安装"
                    }
                } else "未安装"
            }
        } catch (_: Exception) { "未安装" }
    }

    fun startRecording(callback: Callback) {
        if (recording) return
        if (!hasModel()) { callback.onError("离线 Whisper 模型还没有安装"); return }
        try {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuffer, 4096)
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) { audioRecord.release(); callback.onError("麦克风初始化失败"); return }
            val raw = File(activity.cacheDir, "whisper_record_${System.currentTimeMillis()}.pcm")
            rawFile = raw; recorder = audioRecord; recording = true; audioRecord.startRecording()
            recordThread = Thread {
                try { FileOutputStream(raw).use { out -> val buffer = ByteArray(bufferSize); while (recording) { val n = audioRecord.read(buffer,0,buffer.size); if(n>0) out.write(buffer,0,n) } } } catch (_: Exception) {}
            }.also { it.start() }
            callback.onStatus("正在录音… 再点一次麦克风结束并识别")
        } catch (e: SecurityException) { callback.onError("没有麦克风权限") }
        catch (e: Exception) { callback.onError("录音启动失败：${e.message ?: e.javaClass.simpleName}") }
    }

    fun stopAndTranscribe(callback: Callback) {
        if (!recording) return
        recording = false
        try { recorder?.stop() } catch (_: Exception) {}
        callback.onStatus("Whisper 正在离线识别…")
        scope.launch {
            try {
                try { recordThread?.join(2500) } catch (_: Exception) {}
                recorder?.release(); recorder = null; recordThread = null
                val raw = rawFile ?: throw IllegalStateException("录音文件不存在")
                if (!raw.exists() || raw.length() < 3200) throw IllegalStateException("录音太短，请重新说")
                val wav = File(activity.cacheDir, "whisper_${System.currentTimeMillis()}.wav")
                pcmToWav(raw,wav); raw.delete(); rawFile = null
                val model = Whisper.loadModel(activity, modelFile().absolutePath)
                try {
                    val result = Whisper.transcribe(model, wav.absolutePath, WhisperConfig(language = "en"))
                    val text = result.text.trim()
                    withContext(Dispatchers.Main) { if(text.isEmpty()) callback.onError("没有识别到清晰英语，请再说一次") else callback.onResult(text) }
                } finally { Whisper.releaseModel(model); wav.delete() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { callback.onError("Whisper 识别失败：${e.message ?: e.javaClass.simpleName}") } }
        }
    }

    private fun pcmToWav(raw: File, wav: File) {
        val dataSize = raw.length(); FileOutputStream(wav).use { out ->
            val header=ByteArray(44); val byteRate=SAMPLE_RATE*2; val totalDataLen=dataSize+36
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply { put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(totalDataLen.toInt()); put("WAVE".toByteArray(Charsets.US_ASCII)); put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1.toShort()); putShort(1.toShort()); putInt(SAMPLE_RATE); putInt(byteRate); putShort(2.toShort()); putShort(16.toShort()); put("data".toByteArray(Charsets.US_ASCII)); putInt(dataSize.toInt()) }
            out.write(header); FileInputStream(raw).use { input -> input.copyTo(out) }
        }
    }

    fun destroy() { recording=false; try{recorder?.stop()}catch(_:Exception){}; try{recorder?.release()}catch(_:Exception){}; recorder=null; recordThread=null; rawFile?.delete(); rawFile=null; scope.cancel() }
}
