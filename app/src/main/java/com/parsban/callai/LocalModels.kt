package com.parsban.callai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.RandomAccessFile

object LocalModels {
    const val WHISPER_FILE = "ggml-small-q5_1.bin"
    const val QWEN_FILE = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val WHISPER_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
    private const val QWEN_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val MIN_WHISPER_BYTES = 170L * 1024L * 1024L
    private const val MIN_QWEN_BYTES = 430L * 1024L * 1024L
    private const val PREFS = "avakav_local_models"
    private const val WHISPER_ID = "whisper_download_id"
    private const val QWEN_ID = "qwen_download_id"

    @JvmStatic fun whisperFile(context: Context) = File(modelDir(context), WHISPER_FILE)
    @JvmStatic fun qwenFile(context: Context) = File(modelDir(context), QWEN_FILE)
    @JvmStatic fun isWhisperReady(context: Context) = validModel(whisperFile(context), MIN_WHISPER_BYTES, 0x67676d6c)
    @JvmStatic fun isQwenReady(context: Context) = validModel(qwenFile(context), MIN_QWEN_BYTES, 0x46554747)
    @JvmStatic fun ready(context: Context) = isWhisperReady(context) && isQwenReady(context)

    @JvmStatic
    fun startDownloads(context: Context) {
        val dir = modelDir(context)
        if (!dir.exists()) dir.mkdirs()
        val dm = context.getSystemService(DownloadManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!isWhisperReady(context) && !isActive(dm, prefs.getLong(WHISPER_ID, -1L))) {
            whisperFile(context).delete()
            val req = DownloadManager.Request(Uri.parse(WHISPER_URL))
                .setTitle("آواکاو • مدل تبدیل صدا به متن")
                .setDescription("Whisper Small Q5_1 • حدود 190MB")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "models/$WHISPER_FILE")
            prefs.edit().putLong(WHISPER_ID, dm.enqueue(req)).apply()
        }

        if (!isQwenReady(context) && !isActive(dm, prefs.getLong(QWEN_ID, -1L))) {
            qwenFile(context).delete()
            val req = DownloadManager.Request(Uri.parse(QWEN_URL))
                .setTitle("آواکاو • مدل خلاصه‌سازی")
                .setDescription("Qwen2.5 0.5B Q4_K_M • حدود 491MB")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "models/$QWEN_FILE")
            prefs.edit().putLong(QWEN_ID, dm.enqueue(req)).apply()
        }
    }

    @JvmStatic
    fun deleteModels(context: Context) {
        val dm = context.getSystemService(DownloadManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (id in listOf(prefs.getLong(WHISPER_ID, -1L), prefs.getLong(QWEN_ID, -1L))) {
            if (id > 0) runCatching { dm.remove(id) }
        }
        whisperFile(context).delete()
        qwenFile(context).delete()
        prefs.edit().clear().apply()
    }

    @JvmStatic
    fun status(context: Context): ModelStatus {
        val w = isWhisperReady(context)
        val q = isQwenReady(context)
        if (w && q) return ModelStatus(true, true, 100, "هر دو مدل آماده‌اند • پردازش کاملاً روی گوشی")

        val dm = context.getSystemService(DownloadManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val states = mutableListOf<QueryState>()
        if (!w) states += query(dm, prefs.getLong(WHISPER_ID, -1L), "Whisper")
        if (!q) states += query(dm, prefs.getLong(QWEN_ID, -1L), "Qwen")
        val active = states.filter { it.total > 0 && it.downloaded >= 0 }
        val progress = if (active.isEmpty()) 0 else {
            val done = active.sumOf { it.downloaded }
            val total = active.sumOf { it.total }
            if (total <= 0) 0 else ((done * 100L) / total).toInt().coerceIn(0, 99)
        }
        val failedState = states.firstOrNull { it.status == DownloadManager.STATUS_FAILED }
        val text = when {
            failedState != null -> "دانلود ${failedState.name} ناموفق شد؛ دوباره دکمه دانلود را بزن."
            states.any { it.status == DownloadManager.STATUS_RUNNING } -> "در حال دانلود مدل‌ها… $progress٪"
            states.any { it.status == DownloadManager.STATUS_PAUSED } -> "دانلود متوقف شده؛ اتصال اینترنت را بررسی کن."
            states.any { it.status == DownloadManager.STATUS_PENDING } -> "دانلود مدل‌ها در صف است…"
            else -> "مدل‌های محلی هنوز دانلود نشده‌اند • حدود 680MB"
        }
        return ModelStatus(w, q, progress, text)
    }

    private fun validModel(file: File, minimum: Long, magic: Int): Boolean = runCatching {
        file.isFile && file.length() >= minimum && RandomAccessFile(file, "r").use {
            Integer.reverseBytes(it.readInt()) == magic
        }
    }.getOrDefault(false)

    private fun modelDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        return File(root, "models")
    }

    private fun isActive(dm: DownloadManager, id: Long): Boolean {
        if (id <= 0) return false
        val s = query(dm, id, "")
        return s.status == DownloadManager.STATUS_PENDING || s.status == DownloadManager.STATUS_RUNNING || s.status == DownloadManager.STATUS_PAUSED
    }

    private fun query(dm: DownloadManager, id: Long, name: String): QueryState {
        if (id <= 0) return QueryState(name, 0, -1, -1)
        return try {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c != null && c.moveToFirst()) QueryState(
                    name,
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                ) else QueryState(name, 0, -1, -1)
            }
        } catch (_: Exception) { QueryState(name, 0, -1, -1) }
    }

    data class ModelStatus(val whisperReady: Boolean, val qwenReady: Boolean, val progress: Int, val text: String) {
        fun allReady() = whisperReady && qwenReady
    }
    private data class QueryState(val name: String, val status: Int, val downloaded: Long, val total: Long)
}

