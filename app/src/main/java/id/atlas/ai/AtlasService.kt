package id.atlas.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener as AndroidListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Alur: Vosk (offline, ringan) menunggu "Hey Atlas" -> Vosk berhenti (mic dilepas)
 * -> SpeechRecognizer Android (mode offline) menangkap perintah -> parse -> konfirmasi -> eksekusi -> TTS -> kembali menunggu.
 */
class AtlasService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var speaker: Speaker
    private lateinit var actions: Actions
    private lateinit var brain: Brain
    private var model: Model? = null
    private var rec: Recognizer? = null
    private var wakeSvc: SpeechService? = null
    private var busy = false
    private var lastContact: String? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("atlas", "ATLAS", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "atlas").setContentTitle("ATLAS aktif")
            .setContentText("Ucapkan \"Hey Atlas\"").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        speaker = Speaker(this); actions = Actions(this); brain = Brain(this)
        scope.launch { if (brain.load()) AtlasState.log("Mode ngobrol siap.") }
        AtlasState.running.value = true
        AtlasState.status.value = "Memuat model suara…"
        StorageService.unpack(this, "model-en-us", "model",
            { m: Model -> model = m; startWake() },
            { e: IOException -> AtlasState.status.value = "Model gagal dimuat: ${e.message}" })
    }

    private val wakeListener = object : RecognitionListener {
        private fun check(h: String?) { if (h != null && (h.contains("atlas"))) wake() }
        override fun onPartialResult(h: String?) = check(h)
        override fun onResult(h: String?) = check(h)
        override fun onFinalResult(h: String?) {}
        override fun onError(e: Exception?) {}
        override fun onTimeout() {}
    }

    private fun startWake() {
        val m = model ?: return
        rec = Recognizer(m, 16000f, "[\"hey atlas\", \"atlas\", \"[unk]\"]")
        wakeSvc = SpeechService(rec, 16000f).also { it.startListening(wakeListener) }
        AtlasState.status.value = "Menunggu \"Hey Atlas\""
        AtlasState.rms.value = 0f
    }

    private fun stopWake() {
        wakeSvc?.stop(); wakeSvc?.shutdown(); wakeSvc = null
        rec?.close(); rec = null
    }

    private fun wake() {
        handler.post {
            if (busy) return@post
            busy = true; stopWake()
            scope.launch {
                try { session() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { AtlasState.log("Error: ${e.message}") }
                finally { busy = false; if (AtlasState.running.value) startWake() }
            }
        }
    }

    private suspend fun say(t: String) { AtlasState.status.value = "Berbicara…"; AtlasState.log("Atlas: $t"); speaker.say(t) }

    private suspend fun listen(): String? = suspendCancellableCoroutine { cont ->
        AtlasState.status.value = "Mendengarkan…"
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : AndroidListener {
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                sr.destroy(); if (cont.isActive) cont.resume(t)
            }
            override fun onError(e: Int) { sr.destroy(); if (cont.isActive) cont.resume(null) }
            override fun onRmsChanged(r: Float) { AtlasState.rms.value = r }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L))
        cont.invokeOnCancellation { sr.destroy() }
    }

    private suspend fun session() {
        say("Ya?")
        var heard = listen()
        if (heard.isNullOrBlank()) { say("Aku tidak mendengar apa-apa."); return }
        while (true) {  // percakapan dua arah: lanjut mendengar selama masih ngobrol
            AtlasState.log("Kamu: $heard")
            if (!handle(heard!!)) return
            heard = listen()
            if (heard.isNullOrBlank()) return
        }
    }

    /** @return true jika tadi ngobrol dan sesi dilanjutkan untuk pertanyaan berikutnya. */
    private suspend fun handle(heard: String): Boolean {
        val cmds = CommandParser.parse(heard)
        if (cmds.size == 1 && cmds[0] is Cmd.Unknown) { say(brain.reply(heard)); return true }
        var done = 0
        for (raw in cmds) {
            var c: Cmd = raw
            if (c is Cmd.Stop) { speaker.stop(); return false }
            if (c is Cmd.SendLast) {
                val who = lastContact
                if (who == null) { say("Mau kirim ke siapa?"); continue }
                c = Cmd.Chat(who, c.text)
            }
            if (c is Cmd.Reply) {
                val who = NotifStore.lastSender
                if (who == null) { say("Tidak ada pesan untuk dibalas."); continue }
                c = Cmd.Chat(who, c.text)
            }
            if (c is Cmd.Unknown) { say("Maaf, aku belum paham: ${c.raw}"); continue }
            if (c.confirm) {
                say("${actions.describe(c)} Lanjutkan? Katakan ya atau tidak.")
                val a = listen()
                if (a == null || !CommandParser.isYes(a)) { say("Oke, dibatalkan."); continue }
            }
            if (c is Cmd.Chat) lastContact = c.contact
            say(actions.execute(c)); done++
            delay(1500)
        }
        if (done > 1) say("Semua perintah selesai.")
        return false
    }

    override fun onDestroy() {
        AtlasState.running.value = false
        scope.cancel(); brain.close(); stopWake(); model?.close(); speaker.shutdown()
        AtlasState.status.value = "Nonaktif"
        super.onDestroy()
    }
}
