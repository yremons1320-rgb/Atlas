package id.atlas.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Otak ngobrol offline: model Gemma (format .task) lewat MediaPipe LLM Inference. Ingat 6 giliran terakhir. */
class Brain(private val ctx: Context) {
    private var llm: LlmInference? = null
    private val turns = ArrayDeque<Pair<String, String>>()
    private val sys = "Kamu adalah ATLAS, asisten pribadi suara yang ramah dan cerdas. " +
        "Jawab singkat (maksimal 3 kalimat), enak diucapkan, tanpa markdown atau emoji. Balas dalam bahasa yang dipakai pengguna."

    private val modelFile get() = File(ctx.getExternalFilesDir(null), "model.task")

    suspend fun load(): Boolean = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) return@withContext false
        try {
            llm = LlmInference.createFromOptions(ctx, LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath).setMaxTokens(1024).setTopK(40).setTemperature(0.8f).build())
            true
        } catch (e: Exception) { AtlasState.log("LLM gagal dimuat: ${e.message}"); false }
    }

    suspend fun reply(user: String): String {
        val m = llm ?: return "Mode ngobrol belum aktif. Taruh file model.task di folder aplikasi, caranya ada di README."
        val prompt = StringBuilder()
        (turns.toList() + (user to "")).forEachIndexed { i, (u, a) ->
            prompt.append("<start_of_turn>user\n")
            if (i == 0) prompt.append(sys).append("\n\n")
            prompt.append(u).append("<end_of_turn>\n")
            if (a.isNotEmpty()) prompt.append("<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
        }
        prompt.append("<start_of_turn>model\n")
        val out = withContext(Dispatchers.Default) { try { m.generateResponse(prompt.toString()) } catch (e: Exception) { null } }
            ?: return "Maaf, aku gagal berpikir."
        val clean = out.replace(Regex("[*#`_]"), "").trim()
        turns.addLast(user to clean); while (turns.size > 6) turns.removeFirst()
        return clean.ifEmpty { "Hmm, aku belum punya jawabannya." }
    }

    fun close() { llm?.close(); llm = null }
}
