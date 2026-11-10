package id.atlas.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object AtlasState {
    val status = MutableStateFlow("Nonaktif")
    val running = MutableStateFlow(false)
    val rms = MutableStateFlow(0f)
    val history = MutableStateFlow<List<String>>(emptyList())
    fun log(s: String) = history.update { (listOf(s) + it).take(50) }
}

/** Hanya di memori, tidak disimpan ke disk. Diisi hanya jika user mengaktifkan akses notifikasi. */
object NotifStore {
    private val items = ArrayDeque<Pair<String, String>>()
    var lastSender: String? = null
    @Synchronized fun add(sender: String, text: String) {
        items.addFirst(sender to text); if (items.size > 5) items.removeLast(); lastSender = sender
    }
    @Synchronized fun latest(n: Int) = items.take(n)
}

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!sbn.packageName.startsWith("com.whatsapp")) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (title == "WhatsApp") return
        NotifStore.add(title, text)
    }
}
