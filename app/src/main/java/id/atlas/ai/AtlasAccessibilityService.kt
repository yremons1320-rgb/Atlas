package id.atlas.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AtlasAccessibilityService : AccessibilityService() {
    companion object {
        var inst: AtlasAccessibilityService? = null
        /** Menunggu WhatsApp terbuka lalu menekan tombol Kirim. Hanya dipanggil SETELAH user menyetujui. */
        suspend fun clickSend(): Boolean {
            repeat(12) {
                delay(800)
                val s = inst ?: return false
                val root = s.rootInActiveWindow
                if (root?.packageName?.startsWith("com.whatsapp") == true) {
                    val btn = s.find(root, listOf("send", "kirim"))
                    if (btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun walk(n: AccessibilityNodeInfo?, f: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (f(n)) return n
        for (i in 0 until n.childCount) walk(n.getChild(i), f)?.let { return it }
        return null
    }
    private fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        return c
    }
    private fun label(n: AccessibilityNodeInfo) = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).map { it.lowercase() }

    /** Ketuk elemen berdasarkan tulisan/deskripsinya di aplikasi apa pun (selama aplikasinya mengekspos UI ke Aksesibilitas). */
    fun clickText(t: String): Boolean {
        val q = t.lowercase().trim()
        val root = rootInActiveWindow ?: return false
        val hit = walk(root) { n -> label(n).any { it == q } } ?: walk(root) { n -> label(n).any { it.contains(q) } } ?: return false
        return clickableAncestor(hit)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }
    /** Mengisi kolom teks yang sedang fokus. Kolom password sengaja ditolak. */
    fun typeText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: walk(root) { it.isEditable } ?: return false
        if (f.isPassword) return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
    fun scroll(down: Boolean): Boolean {
        val n = walk(rootInActiveWindow) { it.isScrollable } ?: return false
        return n.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
    /** Hanya dipanggil saat user bilang "baca layar". Tidak disimpan. Kolom password dilewati. */
    fun readScreen(): String {
        val sb = StringBuilder()
        fun go(n: AccessibilityNodeInfo?) {
            if (n == null || n.isPassword || sb.length > 600) return
            n.text?.let { sb.append(it).append(". ") }
            for (i in 0 until n.childCount) go(n.getChild(i))
        }
        go(rootInActiveWindow)
        return sb.toString().trim()
    }

    private fun find(n: AccessibilityNodeInfo?, labels: List<String>): AccessibilityNodeInfo? {
        if (n == null) return null
        val d = n.contentDescription?.toString()?.lowercase()
        if (d != null && d in labels) {
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null) return c
        }
        for (i in 0 until n.childCount) find(n.getChild(i), labels)?.let { return it }
        return null
    }
}
