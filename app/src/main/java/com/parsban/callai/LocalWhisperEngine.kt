package com.parsban.callai

import android.content.Context
import android.net.Uri
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object LocalWhisperEngine {
    @JvmStatic
    fun transcribeBlocking(context: Context, uri: Uri, progress: AnalysisProgress): String = runBlocking {
        check(LocalModels.isWhisperReady(context)) { "مدل Whisper ناقص است یا دانلود نشده است" }
        progress.update("DecodingAudio")
        val samples = withContext(Dispatchers.IO) { AudioDecoder.decode16kMono(context, uri) }
        progress.update("LoadingWhisper")
        val whisper = WhisperContext.createContextFromFile(LocalModels.whisperFile(context).absolutePath)
        try {
            val chunkSize = 30 * 16000
            val count = (samples.size + chunkSize - 1) / chunkSize
            val text = StringBuilder()
            for (chunk in 0 until count) {
                coroutineContext.ensureActive()
                progress.update("تبدیل صدا به متن؛ بخش ${chunk + 1} از $count")
                val start = chunk * chunkSize
                val part = samples.copyOfRange(start, minOf(samples.size, start + chunkSize))
                val result = whisper.transcribeData(part, false).trim()
                if (result.isNotBlank()) {
                    if (text.isNotEmpty()) text.append('\n')
                    text.append(result)
                }
            }
            text.toString().trim().also {
                check(it.isNotBlank()) { "Whisper متنی از مکالمه استخراج نکرد" }
            }
        } finally { whisper.release() }
    }
}
