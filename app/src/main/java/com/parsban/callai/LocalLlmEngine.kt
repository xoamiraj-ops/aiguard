package com.parsban.callai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

object LocalLlmEngine {
    @JvmStatic
    fun analyzeBlocking(context: Context, session: CallSession, transcript: String): LocalAnalysis = runBlocking {
        if (!LocalModels.isQwenReady(context)) throw IllegalStateException("مدل Qwen هنوز دانلود نشده است")
        val engine = AiChat.getInferenceEngine(context.applicationContext)
        try {
            withTimeout(30_000) {
                engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error }
            }
            val current = engine.state.value
            if (current is InferenceEngine.State.Error) throw current.exception
            engine.loadModel(LocalModels.qwenFile(context).absolutePath)
            engine.setSystemPrompt("""
                تو موتور آفلاین تحلیل مکالمات آواکاو هستی.
                پاسخ فقط یک JSON معتبر و بدون Markdown باشد، با همین سه کلید:
                {"summary":"...","highlights":["..."],"action_items":["..."]}
                همه خروجی‌ها فارسی روان و کوتاه باشند.
                summary حداکثر ۴ جمله و فقط بر اساس متن باشد.
                highlights فقط نکات مهم واقعی، عددها، تاریخ‌ها، تصمیم‌ها و موضوعات مهم را بیاورد.
                action_items فقط کارهایی را بیاورد که صریحاً گفته، درخواست یا توافق شده‌اند.
                هیچ اسم، تاریخ، مبلغ یا تعهدی را حدس نزن.
                اگر نکته یا کاری وجود ندارد آرایه خالی بده.
            """.trimIndent())

            val who = if (session.contactName.isNullOrBlank()) session.phone.orEmpty() else session.contactName
            val clean = transcript.trim()
            val limited = if (clean.length > 22000)
                clean.take(11000) + "\n\n[بخش میانی برای محدودیت حافظه حذف شد]\n\n" + clean.takeLast(11000)
            else clean
            val prompt = "طرف مکالمه: $who\nزمان ثبت: ${session.createdAt}\nمتن مکالمه:\n$limited"

            val output = StringBuilder()
            withTimeout(180_000) {
                engine.sendUserPrompt(prompt, 450).collect { output.append(it) }
            }
            parseOrFallback(output.toString(), transcript)
        } finally {
            try { engine.cleanUp() } catch (_: Exception) {}
        }
    }

    private fun parseOrFallback(raw: String, transcript: String): LocalAnalysis {
        return try {
            val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start)
            val json = JSONObject(raw.substring(start, end + 1))
            val summary = json.optString("summary").trim()
            if (summary.isBlank()) throw IllegalArgumentException("empty summary")
            val highlights = json.optJSONArray("highlights") ?: JSONArray()
            val actions = json.optJSONArray("action_items") ?: JSONArray()
            LocalAnalysis(summary, highlights.toString(), actions.toString())
        } catch (_: Exception) { fallback(transcript) }
    }

    private fun fallback(transcript: String): LocalAnalysis {
        val sentences = transcript.replace("\n", " ")
            .split(Regex("[.!؟?]+")).map { it.trim() }.filter { it.length >= 8 }
        val summary = sentences.take(3).joinToString("، ").ifBlank { "متن مکالمه ذخیره شد." }
        val highlights = JSONArray(); sentences.take(5).forEach { highlights.put(it) }
        val actionWords = listOf("باید","فردا","قرار","پیگیری","زنگ","تماس","بفرست","ارسال","بخر","خرید","یادم")
        val actions = JSONArray()
        // No tasks are inferred from keyword-only matches.
        return LocalAnalysis(summary, highlights.toString(), actions.toString())
    }
}

