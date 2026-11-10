package id.atlas.ai

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Actions(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val needAcc = "Aktifkan layanan Aksesibilitas ATLAS dulu di pengaturan."

    private fun acc(f: (AtlasAccessibilityService) -> String): String = AtlasAccessibilityService.inst?.let(f) ?: needAcc
    private fun go(i: Intent): Boolean = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun describe(c: Cmd) = when (c) {
        is Cmd.Chat -> "Kirim pesan WhatsApp ke ${c.contact}: ${c.text}."
        is Cmd.Call -> "Telepon ${c.contact}."
        is Cmd.Click -> "Ketuk ${c.text}."
        is Cmd.InstaStory -> "Siapkan foto terbaru untuk story Instagram."
        else -> "Jalankan perintah ini."
    }

    suspend fun execute(c: Cmd): String = when (c) {
        is Cmd.OpenApp -> open(c.name.trim())
        is Cmd.Chat -> chat(c)
        is Cmd.Call -> call(c.contact)
        is Cmd.ReadWa -> NotifStore.latest(3).let { l ->
            if (l.isEmpty()) "Belum ada pesan WhatsApp baru, atau akses notifikasi belum diaktifkan."
            else l.joinToString(". ") { "Dari ${it.first}: ${it.second}" }
        }
        is Cmd.InstaStory -> story(c.caption)
        is Cmd.Volume -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (c.up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Oke." }
        is Cmd.Brightness -> brightness(c.up)
        is Cmd.Lock -> AtlasAccessibilityService.inst?.let { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); "Layar dikunci." } ?: needAcc
        is Cmd.Screenshot -> AtlasAccessibilityService.inst?.let { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); "Screenshot diambil." } ?: needAcc
        is Cmd.Camera -> if (go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Kamera terbuka. Android tidak mengizinkan aku memotret diam-diam, jadi tekan tombol rana ya." else "Gagal membuka kamera."
        is Cmd.Wifi -> { go(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); "Android melarang aplikasi menyalakan Wi-Fi langsung. Aku buka panelnya, tinggal ketuk." }
        is Cmd.Bluetooth -> { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "Aku buka pengaturan Bluetooth, silakan nyalakan." }
        is Cmd.Airplane -> { go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); "Mode pesawat tidak bisa diubah aplikasi biasa. Aku buka pengaturannya." }
        is Cmd.Media -> { audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, c.key)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, c.key)); "Oke." }
        is Cmd.Timer -> { go(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, c.seconds).putExtra(AlarmClock.EXTRA_MESSAGE, c.label).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "Pengingat dipasang." }
        is Cmd.Time -> "Sekarang ${SimpleDateFormat("EEEE, d MMMM yyyy, 'jam' HH:mm", Locale("id", "ID")).format(Date())}."
        is Cmd.Click -> acc { if (it.clickText(c.text)) "Sudah kuketuk ${c.text}." else "Tulisan ${c.text} tidak kutemukan di layar." }
        is Cmd.TypeText -> acc { if (it.typeText(c.text)) "Sudah diketik." else "Tidak ada kolom teks yang aktif. Ketuk kolomnya dulu." }
        is Cmd.Scroll -> acc { if (it.scroll(c.down)) "Oke." else "Tidak ada yang bisa di-scroll." }
        is Cmd.Back -> acc { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); "Kembali." }
        is Cmd.Home -> acc { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); "Ke layar utama." }
        is Cmd.ReadScreen -> acc { it.readScreen().ifEmpty { "Tidak ada teks yang bisa kubaca di layar ini." } }
        else -> "Perintah belum didukung."
    }

    private fun open(n: String): String {
        when (n) {
            "kamera", "camera" -> return if (go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Membuka kamera." else "Gagal membuka kamera."
            "galeri", "gallery" -> return if (go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))) "Membuka galeri." else "Galeri tidak ditemukan."
            "pengaturan", "setelan", "settings", "setting" -> { go(Intent(Settings.ACTION_SETTINGS)); return "Membuka pengaturan." }
        }
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: return "Aplikasi $n tidak ketemu."
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Tidak bisa membuka $n."
        return if (go(i)) "Membuka ${hit.loadLabel(pm)}." else "Gagal membuka $n. Pastikan izin tampil di atas aplikasi lain sudah aktif."
    }

    private fun number(name: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER), "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }
    private fun intl(n: String): String { val d = n.filter { it.isDigit() }; return if (d.startsWith("0")) "62" + d.drop(1) else d } // asumsi Indonesia

    private suspend fun chat(c: Cmd.Chat): String {
        val num = number(c.contact) ?: return "Kontak ${c.contact} tidak ditemukan, atau izin kontak belum diberikan."
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: return "WhatsApp tidak terpasang."
        val url = "https://wa.me/${intl(num)}" + (c.text?.let { "?text=${Uri.encode(it)}" } ?: "")
        if (!go(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) return "Gagal membuka WhatsApp."
        if (c.text == null) return "Chat dengan ${c.contact} terbuka."
        return if (AtlasAccessibilityService.clickSend()) "Pesan terkirim ke ${c.contact}."
        else "Pesan sudah diketik di chat ${c.contact}, tapi tombol kirim belum bisa kutekan. Tekan kirim sendiri ya."
    }

    private fun call(name: String): String {
        val num = number(name) ?: return "Kontak $name tidak ditemukan, atau izin kontak belum diberikan."
        val uri = Uri.parse("tel:${Uri.encode(num)}")
        return if (granted(Manifest.permission.CALL_PHONE)) { go(Intent(Intent.ACTION_CALL, uri)); "Menelepon $name." }
        else { go(Intent(Intent.ACTION_DIAL, uri)); "Izin telepon belum ada, aku buka dialer saja." }
    }

    private fun brightness(up: Boolean): String {
        if (!Settings.System.canWrite(ctx)) { go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))); return "Aku butuh izin ubah pengaturan sistem dulu." }
        val cr = ctx.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (cur + if (up) 50 else -50).coerceIn(10, 255))
        return "Kecerahan ${if (up) "dinaikkan" else "diturunkan"}."
    }

    private fun story(caption: String?): String {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.query(base, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
            ?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(base, c.getLong(0)) else null }
            ?: return "Tidak ada foto, atau izin galeri belum diberikan."
        val i = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, caption ?: "").setPackage("com.instagram.android").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (go(i)) "Foto terbaru sudah kusiapkan di Instagram. Pilih Cerita Anda lalu tekan Bagikan." else "Instagram tidak terpasang."
    }
}
