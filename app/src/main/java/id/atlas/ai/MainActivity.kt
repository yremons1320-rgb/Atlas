package id.atlas.ai

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sin

private val Neon = Color(0xFF00E5FF)
private val PERMS = arrayOf(
    Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
    Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Neon, background = Color.Black)) { Screen() } }
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val status by AtlasState.status.collectAsState()
    val running by AtlasState.running.collectAsState()
    val rms by AtlasState.rms.collectAsState()
    val history by AtlasState.history.collectAsState()
    val battery = remember {
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }
    val svc = Intent(ctx, AtlasService::class.java)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) ContextCompat.startForegroundService(ctx, svc)
    }
    fun openSetting(action: String, withPkg: Boolean = false) =
        ctx.startActivity(Intent(action).apply { if (withPkg) data = Uri.parse("package:${ctx.packageName}") })

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("A.T.L.A.S", color = Neon, fontSize = 28.sp, letterSpacing = 8.sp)
        Text(status, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Orb(running, rms, Modifier.size(200.dp))
        Wave(running, rms, Modifier.fillMaxWidth().height(48.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).border(3.dp, Neon, CircleShape)
                    .background(if (running) Neon.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { if (running) ctx.stopService(svc) else launcher.launch(PERMS) },
                contentAlignment = Alignment.Center
            ) { Text(if (running) "ON" else "MIC", color = Neon, fontSize = 18.sp) }
            Button(
                onClick = { ctx.stopService(svc) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
            ) { Text("STOP DARURAT") }
        }
        Text("Baterai $battery%  •  Mode: offline", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
        Text("Riwayat", color = Neon, modifier = Modifier.padding(top = 12.dp).align(Alignment.Start))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(history) { Text(it, color = Color.White, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Aksesibilitas") }
            OutlinedButton(onClick = { openSetting(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Akses Notif") }
            OutlinedButton(onClick = { openSetting(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }) { Text("Overlay") }
            OutlinedButton(onClick = { openSetting(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }) { Text("Ubah Setelan") }
        }
    }
}

@Composable
fun Orb(active: Boolean, level: Float, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val r = size.minDimension / 2
        val boost = if (active) 1f + level.coerceIn(0f, 10f) / 25f else 1f
        drawCircle(Neon.copy(alpha = 0.15f), r * 0.6f * pulse * boost)
        drawCircle(Neon, r * 0.5f, style = Stroke(3f))
        drawCircle(Neon.copy(alpha = 0.5f), r * 0.9f, style = Stroke(1.5f))
        rotate(if (active) rot else 0f) {
            val s = Size(r * 1.5f, r * 1.5f); val tl = Offset(center.x - r * 0.75f, center.y - r * 0.75f)
            drawArc(Neon, 0f, 100f, false, tl, s, style = Stroke(6f, cap = StrokeCap.Round))
            drawArc(Neon, 180f, 100f, false, tl, s, style = Stroke(6f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Wave(active: Boolean, level: Float, modifier: Modifier) {
    val ph by rememberInfiniteTransition(label = "wave")
        .animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "ph")
    Canvas(modifier) {
        val n = 32; val w = size.width / n
        val amp = if (active) 0.15f + level.coerceIn(0f, 10f) / 10f * 0.85f else 0.05f
        for (i in 0 until n) {
            val h = size.height * amp * (0.3f + 0.7f * abs(sin(ph + i * 0.4f)))
            val x = w * i + w / 2
            drawLine(Neon, Offset(x, center.y - h / 2), Offset(x, center.y + h / 2), strokeWidth = w * 0.5f, cap = StrokeCap.Round)
        }
    }
}
