package id.atlas.ai

import android.view.KeyEvent

sealed class Cmd(val confirm: Boolean = false) {
    data class OpenApp(val name: String) : Cmd()
    data class Chat(val contact: String, val text: String?) : Cmd(confirm = text != null)
    data class SendLast(val text: String) : Cmd()
    data class Reply(val text: String) : Cmd()
    data class Call(val contact: String) : Cmd(confirm = true)
    object ReadWa : Cmd()
    data class InstaStory(val caption: String?) : Cmd(confirm = true)
    // Kontrol di dalam aplikasi mana pun (via Aksesibilitas)
    data class Click(val text: String, val sensitive: Boolean) : Cmd(confirm = sensitive)
    data class TypeText(val text: String) : Cmd()
    data class Scroll(val down: Boolean) : Cmd()
    object Back : Cmd()
    object Home : Cmd()
    object ReadScreen : Cmd()
    // Perangkat
    data class Volume(val up: Boolean) : Cmd()
    data class Brightness(val up: Boolean) : Cmd()
    object Lock : Cmd()
    object Screenshot : Cmd()
    object Camera : Cmd()
    object Wifi : Cmd()
    object Bluetooth : Cmd()
    object Airplane : Cmd()
    data class Media(val key: Int) : Cmd()
    data class Timer(val seconds: Int, val label: String) : Cmd()
    object Time : Cmd()
    object Stop : Cmd()
    /** Bukan perintah -> diserahkan ke otak LLM untuk ngobrol. */
    data class Unknown(val raw: String) : Cmd()
}

object CommandParser {
    private val splitter = Regex("\\s*(?:,|\\b(?:setelah itu|habis itu|sesudah itu|lalu|kemudian|terus|then|after that)\\b)\\s*")
    private val sensitive = Regex("(kirim|bayar|hapus|beli|transfer|send|delete|pay|buy|posting|upload|checkout|konfirmasi)")
    private val toggleVerb = Regex("(nyalakan|hidupkan|aktifkan|matikan|nonaktifkan|turn on|turn off|enable|disable)")
    private fun has(p: String, t: String) = Regex(p).containsMatchIn(t)

    fun parse(raw: String): List<Cmd> =
        raw.lowercase().replace(Regex("^(hey |hai |halo )?atlas[, ]*"), "")
            .split(splitter).map { it.trim() }.filter { it.isNotEmpty() }.map(::one)

    fun isYes(t: String): Boolean {
        val s = t.lowercase()
        return has("\\b(ya|iya|yes|ok|oke|boleh|lanjut|kirim|setuju)\\b", s) && !has("\\b(tidak|jangan|no|batal|enggak|nggak|gak)\\b", s)
    }

    private fun one(t: String): Cmd {
        if (has("^(diam|stop|berhenti|cukup|selesai|sudah cukup|be quiet|shut up)", t)) return Cmd.Stop
        if (has("(pesan whatsapp terbaru|baca(kan)? pesan|read my messages)", t)) return Cmd.ReadWa
        Regex("^(?:balas|reply)(?: pesan)?(?: dengan| with)?\\s+(.+)$").find(t)?.let { return Cmd.Reply(it.groupValues[1]) }
        Regex("^(?:chat|wa|whatsapp|kirim pesan ke|pesan ke|message)\\s+(?:ke\\s+)?(.+?)(?:\\s+(?:dan\\s+)?(?:bilang|katakan|tulis|bahwa|saying|that)\\s+(.+))?$")
            .find(t)?.let { return Cmd.Chat(it.groupValues[1], it.groupValues[2].ifEmpty { null }) }
        Regex("^(?:telepon|telpon|call|hubungi|panggil)\\s+(?:nomor\\s+)?(.+)$").find(t)?.let { return Cmd.Call(it.groupValues[1]) }
        if ("story" in t && has("^(upload|unggah|buat|posting)", t))
            return Cmd.InstaStory(Regex("caption\\s+(.+)$").find(t)?.groupValues?.get(1))
        Regex("^(?:buka|bukain|open|jalankan|launch)\\s+(?:aplikasi\\s+|app\\s+)?(.+)$").find(t)?.let { return Cmd.OpenApp(it.groupValues[1]) }

        // Kontrol layar / aplikasi apa pun
        if (has("^(kembali|back|mundur|tekan kembali|tekan back)$", t)) return Cmd.Back
        if (has("^(ke )?(home|beranda|layar utama|halaman utama)$", t)) return Cmd.Home
        Regex("^(?:scroll|gulir|geser)\\s+(?:ke\\s+)?(bawah|atas|down|up)$").find(t)?.let { return Cmd.Scroll(it.groupValues[1] == "bawah" || it.groupValues[1] == "down") }
        if (has("(baca layar|read (the )?screen|apa yang ada di layar)", t)) return Cmd.ReadScreen
        Regex("^(?:ketik|type)\\s+(.+)$").find(t)?.let { return Cmd.TypeText(it.groupValues[1]) }
        Regex("^(?:klik|ketuk|tekan|pencet|tap|click)\\s+(?:tombol\\s+)?(.+)$").find(t)?.let { return Cmd.Click(it.groupValues[1], sensitive.containsMatchIn(it.groupValues[1])) }

        // Perangkat
        if ("volume" in t && has("(besar|naik|keras|tambah|kecil|turun|kurang|pelan|up|down|lower|louder)", t))
            return Cmd.Volume(!has("(kecil|turun|kurang|pelan|down|lower)", t))
        if (has("(kecerahan|brightness)", t)) return Cmd.Brightness(!has("(kurang|turun|kecil|redup|rendah|dim|down)", t))
        if (has("^(kunci|lock)\\b|matikan layar|screen off", t)) return Cmd.Lock
        if (has("(screenshot|tangkapan layar)", t)) return Cmd.Screenshot
        if (has("^(kamera|camera|ambil foto|take a photo)", t)) return Cmd.Camera
        if (toggleVerb.containsMatchIn(t)) {
            if (has("(wifi|wi-fi)", t)) return Cmd.Wifi
            if ("bluetooth" in t) return Cmd.Bluetooth
            if (has("(mode pesawat|airplane)", t)) return Cmd.Airplane
        }
        if (has("^((lagu|musik) )?(berikutnya|selanjutnya)$|^next( song)?$", t)) return Cmd.Media(KeyEvent.KEYCODE_MEDIA_NEXT)
        if (has("^((lagu|musik) )?sebelumnya$|^previous( song)?$", t)) return Cmd.Media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        if (has("(putar musik|lanjutkan musik|play music|mainkan musik|jeda musik|pause music|^jeda$|^pause$)", t)) return Cmd.Media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        Regex("(?:ingatkan|remind).*?(\\d+)\\s*(menit|jam|minute|hour)").find(t)?.let {
            val n = it.groupValues[1].toInt(); val h = it.groupValues[2].startsWith("j") || it.groupValues[2].startsWith("h")
            return Cmd.Timer(n * if (h) 3600 else 60, t)
        }
        if (has("(jam berapa|what time|tanggal berapa|hari apa|what day)", t)) return Cmd.Time
        Regex("^kirim pesan\\s+(.+)$").find(t)?.let { return Cmd.SendLast(it.groupValues[1]) }
        return Cmd.Unknown(t)
    }
}
