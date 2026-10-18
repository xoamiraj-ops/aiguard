package com.parsban.callai

import android.content.Context
import android.net.Uri
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object LocalWhisperEngine {
    @JvmStatic
    fun transcribeBlocking(context: Context, uri: Uri): String = runBlocking {
        if (!LocalModels.isWhisperReady(context)) throw IllegalStateException("مدل Whisper هنوز دانلود نشده است")
        val samples = withContext(Dispatchers.IO) { AudioDecoder.decode16kMono(context, uri) }
        val whisper = WhisperContext.createContextFromFile(LocalModels.whisperFile(context).absolutePath)
        try {
            val text = whisper.transcribeData(samples, false).trim()
            if (text.isBlank()) throw IllegalStateException("Whisper متنی از مکالمه استخراج نکرد")
            text
        } finally { whisper.release() }
    }
}
