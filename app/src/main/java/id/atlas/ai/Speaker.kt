package id.atlas.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class Speaker(ctx: Context) {
    private lateinit var tts: TextToSpeech
    init {
        tts = TextToSpeech(ctx) { if (it == TextToSpeech.SUCCESS) tts.language = Locale("id", "ID") }
    }

    suspend fun say(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        val id = UUID.randomUUID().toString()
        fun finish(u: String?) { if (u == id && cont.isActive) cont.resume(Unit) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) = finish(u)
            @Deprecated("Deprecated in Java") override fun onError(u: String?) = finish(u)
            override fun onStop(u: String?, interrupted: Boolean) = finish(u)
        })
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) cont.resume(Unit)
        cont.invokeOnCancellation { tts.stop() }
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}
