package com.example.dreamtv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import kotlinx.coroutines.delay

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRowDefaults as Material3TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextField
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.Handler
import android.widget.Toast
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.abs
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import org.vosk.Model
import org.vosk.Recognizer
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import androidx.compose.ui.res.painterResource



// --- Vosk Offline Speech Recognition ---
object VoskManager {
    private var model: Model? = null
    private var audioRecord: AudioRecord? = null
    private var recognizerThread: Thread? = null
    @Volatile private var isListening = false
    @Volatile private var isModelReady = false
    @Volatile private var isInitializing = false

    fun isReady() = isModelReady

    fun init(context: Context, onReady: () -> Unit, onError: (String) -> Unit) {
        if (isModelReady || isInitializing) {
            AppLogger.log("Vosk: already ready or initializing")
            return
        }
        isInitializing = true
        
        Thread {
            try {
                AppLogger.log("Vosk: loading model from assets/model-ru...")
                val modelPath = "${context.filesDir.absolutePath}/vosk-model-ru"
                val modelDir = java.io.File(modelPath)
                
                // Copy from assets if not exists
                if (!modelDir.exists()) {
                    AppLogger.log("Vosk: copying model from assets...")
                    copyAssetsFolder(context, "model-ru", modelDir)
                }
                
                AppLogger.log("Vosk: model path: $modelPath")
                val m = Model(modelPath)
                model = m
                isModelReady = true
                isInitializing = false
                AppLogger.log("Vosk model loaded OK")
                
                Handler(Looper.getMainLooper()).post { onReady() }
            } catch (e: Exception) {
                isInitializing = false
                val errorMsg = "Vosk model load error: ${e.message}"
                AppLogger.log(errorMsg)
                Handler(Looper.getMainLooper()).post { onError(errorMsg) }
            }
        }.start()
    }
    
    private fun copyAssetsFolder(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: emptyArray()

        if (files.isEmpty()) {
            // It's a file, not a directory
            val parent = destDir.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            assetManager.open(assetPath).use { input ->
                destDir.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            for (file in files) {
                copyAssetsFolder(context, "$assetPath/$file", File(destDir, file))
            }
        }
    }

    fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val m = model ?: run { onError("Модель не готова"); return }
        if (isListening) return

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onError("Не удалось открыть микрофон")
            return
        }
        audioRecord = rec
        rec.startRecording()
        isListening = true

        val mainHandler = Handler(Looper.getMainLooper())
        recognizerThread = Thread {
            try {
                Recognizer(m, sampleRate.toFloat()).use { recognizer ->
                    val buffer = ShortArray(bufferSize / 2)
                    while (isListening) {
                        val read = rec.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            if (recognizer.acceptWaveForm(buffer, read)) {
                                val text = parseVoskJson(recognizer.result, "text")
                                if (text.isNotBlank()) {
                                    mainHandler.post { onResult(text) }
                                }
                            } else {
                                val partial = parseVoskJson(recognizer.partialResult, "partial")
                                if (partial.isNotBlank()) {
                                    mainHandler.post { onPartial(partial) }
                                }
                            }
                        }
                    }
                    val final = parseVoskJson(recognizer.finalResult, "text")
                    if (final.isNotBlank()) {
                        mainHandler.post { onResult(final) }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("Vosk recognition error: ${e.message}")
                mainHandler.post { onError("Ошибка распознавания: ${e.message}") }
            } finally {
                rec.stop()
                rec.release()
                audioRecord = null
            }
        }.also { it.start() }
    }

    fun stopListening() {
        isListening = false
        recognizerThread?.join(2000)
        recognizerThread = null
    }

    fun release() {
        stopListening()
        model?.close()
        model = null
        isModelReady = false
    }

    private fun parseVoskJson(json: String, key: String): String = try {
        JSONObject(json).optString(key, "")
    } catch (e: Exception) { "" }
}

// --- Preferences Helper ---
object PreferencesManager {
    private const val PREF_NAME = "dreamtv_prefs"
    private const val KEY_LAST_CHANNEL = "last_channel_index"
    private const val KEY_CURRENT_PLAYLIST = "current_playlist_url"
    private const val KEY_CUSTOM_PLAYLISTS = "custom_playlists" // Name|Url||Name|Url
    private const val KEY_VIDEO_QUALITY = "video_quality" // 0=Max, 1=High, 2=Medium, 3=Low
    private const val KEY_AUTO_START = "auto_start_on_boot"
    private const val KEY_RESTORE_CHANNEL = "restore_last_channel"
    private const val KEY_LOGS_ENABLED = "logs_enabled"
    private const val KEY_PLAYER_TYPE = "player_type" // 0=Ijk, 1=System, 2=Exo, 3=VLC
    private const val KEY_WEB_REMOTE_ENABLED = "web_remote_enabled"

    val PRESETS = listOf(
                "Default" to "https://dreampartners.online/dev/apps/iptv/main.m3u8",
                "Backup" to "https://m3u.su/dzmo"
            )

    fun isLogsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_LOGS_ENABLED, false)

    fun setLogsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOGS_ENABLED, enabled).apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_START, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun isRestoreChannelEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_RESTORE_CHANNEL, true) // Default true

    fun setRestoreChannelEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESTORE_CHANNEL, enabled).apply()
    }

    fun isWebRemoteEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_WEB_REMOTE_ENABLED, false)

    fun setWebRemoteEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_WEB_REMOTE_ENABLED, enabled).apply()
    }

    fun getLastChannel(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_LAST_CHANNEL, -1)

    fun saveLastChannel(context: Context, index: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_LAST_CHANNEL, index).apply()
    }

    fun getCurrentPlaylist(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_PLAYLIST, PRESETS[0].second) ?: PRESETS[0].second
    }

    fun saveCurrentPlaylist(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CURRENT_PLAYLIST, url).apply()
    }
    
    fun getCustomPlaylists(context: Context): List<Pair<String, String>> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_CUSTOM_PLAYLISTS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        val parts = raw.split("||")
        return parts.mapNotNull { 
            val p = it.split("|")
            if (p.size == 2) p[0] to p[1] else null
        }
    }
    
    fun addCustomPlaylist(context: Context, name: String, url: String) {
        val current = getCustomPlaylists(context).toMutableList()
        // Remove if url exists to update name
        current.removeAll { it.second == url }
        current.add(name to url)
        saveCustomPlaylists(context, current)
    }

    fun removeCustomPlaylist(context: Context, url: String) {
        val current = getCustomPlaylists(context).toMutableList()
        current.removeAll { it.second == url }
        saveCustomPlaylists(context, current)
    }

    private fun saveCustomPlaylists(context: Context, list: List<Pair<String, String>>) {
        val joined = list.joinToString("||") { "${it.first}|${it.second}" }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CUSTOM_PLAYLISTS, joined).apply()
    }

    fun getVideoQuality(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_VIDEO_QUALITY, 0)

    fun saveVideoQuality(context: Context, quality: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_VIDEO_QUALITY, quality).apply()
    }

    fun getPlayerType(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_PLAYER_TYPE, 0)

    fun setPlayerType(context: Context, type: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_PLAYER_TYPE, type).apply()
    }
}

// --- Logging Helper ---
object AppLogger {
    private val _logs = ArrayDeque<String>(50)
    val logs: List<String> get() = synchronized(_logs) { _logs.toList() }
    var isVisible by mutableStateOf(false) // Default hidden

    fun init(context: Context) {
        isVisible = PreferencesManager.isLogsEnabled(context)
    }

    fun log(message: String) {
        Log.d("DreamTV", message)
        if (!isVisible) return // Skip list update if not shown
        synchronized(_logs) {
            if (_logs.size >= 50) _logs.removeFirst()
            _logs.addLast(message)
        }
    }

    fun toggleVisibility() {
        isVisible = !isVisible
    }
}

object GlobalErrorHandler {
    var lastError: String? = null

    fun saveCrashLog(context: Context, error: String) {
        try {
            val file = java.io.File(context.filesDir, "crash_log.txt")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val entry = "[$timestamp]\n$error\n\n---\n\n"
            // Prepend so newest is on top; keep max 8KB
            val existing = if (file.exists()) file.readText() else ""
            val combined = entry + existing
            file.writeText(if (combined.length > 8192) combined.substring(0, 8192) else combined)
        } catch (_: Exception) {}
    }

    fun readCrashLog(context: Context): String {
        return try {
            val file = java.io.File(context.filesDir, "crash_log.txt")
            if (file.exists()) file.readText() else "Журнал пуст"
        } catch (_: Exception) { "Ошибка чтения журнала" }
    }

    fun clearCrashLog(context: Context) {
        try { java.io.File(context.filesDir, "crash_log.txt").delete() } catch (_: Exception) {}
    }
}

// In-memory channel cache to avoid reloading on back-press
object ChannelCache {
    var url: String = ""
    var channels: List<Channel> = emptyList()
}

// Singleton HTTP client — не создаётся заново при каждом запросе
private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

// --- Draggable Logs Overlay ---
@Composable
fun DraggableLogsOverlay() {
    var offsetX by remember { mutableFloatStateOf(100f) }
    var offsetY by remember { mutableFloatStateOf(100f) }
    val logSnapshot = remember { mutableStateOf(AppLogger.logs) }

    // Refresh logs every second only (not on every recompose)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            logSnapshot.value = AppLogger.logs
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .width(400.dp)
            .height(300.dp)
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = "LOGS (drag to move)",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logSnapshot.value) { log ->
                    Text(text = log, color = Color.Green, fontSize = 12.sp)
                }
            }
        }
    }
}

@androidx.compose.runtime.Stable
data class Channel(val name: String, val url: String, val logo: String? = null, val userAgent: String? = null, val originalIndex: Int = 0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.init(this)

        // Load IjkPlayer native libraries
        try {
            IjkMediaPlayer.loadLibrariesOnce(null)
            IjkMediaPlayer.native_profileBegin("libijkplayer.so")
            AppLogger.log("IjkPlayer libs loaded")
        } catch (e: Throwable) {
            AppLogger.log("Error loading IjkPlayer libs: ${e.message}")
        }
        
        // --- Global Exception Handler ---
        val appContext = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val errorMsg = "FATAL CRASH: ${throwable.message}\n${throwable.stackTraceToString()}"
            Log.e("DreamTV", errorMsg)
            GlobalErrorHandler.lastError = errorMsg
            GlobalErrorHandler.saveCrashLog(appContext, errorMsg)

            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)!!.apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        AppLogger.log("MainActivity: onCreate started")

        // Request RECORD_AUDIO permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }

        // Init Vosk in background after a short delay so app starts fast
        Handler(Looper.getMainLooper()).postDelayed({
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                VoskManager.init(
                    this,
                    onReady = { AppLogger.log("Vosk ready") },
                    onError = { AppLogger.log("Vosk init error: $it") }
                )
            }
        }, 5000) // Delay Vosk init to not compete with app startup

        // Start Web Remote Service
        try {
            startService(Intent(this, WebRemoteService::class.java))
            AppLogger.log("WebRemoteService started")
        } catch (e: Exception) {
            AppLogger.log("Error starting WebRemoteService: ${e.message}")
        }

        if (GlobalErrorHandler.lastError != null) {
            setContent {
                 MaterialTheme {
                     Box(
                         modifier = Modifier.fillMaxSize().background(Color.Red)
                     ) {
                         Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                             Text("APP CRASHED!", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                             Spacer(modifier = Modifier.height(16.dp))
                             Text(GlobalErrorHandler.lastError ?: "", color = Color.White)
                             Spacer(modifier = Modifier.height(16.dp))
                             Button(onClick = { 
                                 GlobalErrorHandler.lastError = null
                                 recreate() 
                             }) {
                                 Text("Restart App")
                             }
                         }
                     }
                 }
            }
            return
        }
        
        try {
            setContent {
                MaterialTheme {
                    // Use simple Box instead of Surface to avoid TV-specific theme issues
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    ) {
                        DreamTvApp()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("CRASH in setContent: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VoskManager.release()
    }
}

@Composable
fun DreamTvApp() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var currentChannelIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPlaylistUrl by remember { mutableStateOf(PreferencesManager.getCurrentPlaylist(context)) }
    var hasAutoPlayed by remember { mutableStateOf(false) } 
    
    // Digit Input State
    var digitBuffer by remember { mutableStateOf("") }
    var showDigitOverlay by remember { mutableStateOf(false) }

    // Handle Digit Commit
    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isNotEmpty()) {
            showDigitOverlay = true
            delay(1500) // Wait 1.5 seconds
            
            // Try to switch channel
            val channelNum = digitBuffer.toIntOrNull()
            if (channelNum != null) {
                if (channelNum == 0) {
                    // Settings is now a tab in MainScreen, so we might want to handle this differently
                    // or just ignore 0 if we are in MainScreen since it has UI.
                    // But if we are in Player, we might want to exit to Settings.
                    if (isPlaying) {
                        isPlaying = false
                        // We could pass a state to MainScreen to open Settings tab, 
                        // but for now let's just go back to MainScreen.
                    }
                    AppLogger.log("Zero pressed")
                } else if (channelNum == 9999) {
                    AppLogger.toggleVisibility()
                    AppLogger.log("Toggled logs visibility")
                } else if (channelNum > 0 && channelNum <= channels.size) {
                    currentChannelIndex = channelNum - 1
                    isPlaying = true
                    AppLogger.log("Switched to channel $channelNum via digit input")
                } else {
                    AppLogger.log("Channel $channelNum out of range (1-${channels.size})")
                }
            }
            digitBuffer = ""
            showDigitOverlay = false
        } else {
            showDigitOverlay = false
        }
    }

    val onDigitKey: (Int) -> Boolean = { keyCode ->
        val digit = when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
            else -> null
        }
        
        if (digit != null) {
            digitBuffer += digit
            true
        } else {
            false
        }
    }

    // Setup WebRemoteService callbacks
    LaunchedEffect(Unit) {
        WebRemoteService.getStatusCallback = {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val volumePercent = if (maxVolume > 0) (currentVolume.toFloat() / maxVolume * 100).toInt() else 0

            PlayerStatus(
                currentChannel = if (currentChannelIndex != -1 && currentChannelIndex < channels.size) channels[currentChannelIndex].name else "",
                currentChannelNumber = if (currentChannelIndex != -1) currentChannelIndex + 1 else -1,
                isPlaying = isPlaying,
                volume = volumePercent,
                playerType = when (PreferencesManager.getPlayerType(context)) {
                    0 -> "ijkplayer"
                    1 -> "system"
                    3 -> "vlc"
                    else -> "unknown"
                },
                currentPlaylist = PreferencesManager.getCurrentPlaylist(context)
            )
        }

        WebRemoteService.getChannelsCallback = {
            channels.mapIndexed { idx, ch ->
                ChannelInfo(
                    id = idx,
                    name = ch.name,
                    url = ch.url,
                    logoUrl = ch.logo ?: ""
                )
            }
        }

        WebRemoteService.commandCallback = { cmd ->
            val instrumentation = android.app.Instrumentation()
            fun sendKey(keyCode: Int) {
                Thread {
                    try {
                        instrumentation.sendKeyDownUpSync(keyCode)
                    } catch (e: Exception) {
                        AppLogger.log("Error sending key $keyCode: ${e.message}")
                    }
                }.start()
            }

            when (cmd.command) {
                "switchChannel" -> {
                    if (cmd.value != null && cmd.value!! in 0 until channels.size) {
                        currentChannelIndex = cmd.value!!
                        isPlaying = true
                    }
                }
                "play" -> isPlaying = true
                "pause" -> isPlaying = false
                "next" -> {
                    if (channels.isNotEmpty()) {
                        currentChannelIndex = (currentChannelIndex + 1) % channels.size
                    }
                }
                "prev" -> {
                    if (channels.isNotEmpty()) {
                        currentChannelIndex = if (currentChannelIndex > 0) currentChannelIndex - 1 else channels.size - 1
                    }
                }
                "setVolume" -> {
                    if (cmd.value != null) {
                         try {
                             val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                             val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                             val targetVolume = (cmd.value!!.toFloat() / 100f * maxVolume).toInt()
                             // Use FLAG_SHOW_UI to show system volume bar
                             audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, android.media.AudioManager.FLAG_SHOW_UI)
                         } catch (e: Exception) {
                             AppLogger.log("Error setting volume: ${e.message}")
                         }
                    }
                }
                "up" -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
                "down" -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
                "left" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
                "right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                "ok" -> sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
                "back" -> sendKey(KeyEvent.KEYCODE_BACK)
                "menu" -> sendKey(KeyEvent.KEYCODE_MENU)
                "home" -> sendKey(KeyEvent.KEYCODE_HOME)
                
                // Numpad support
                "1" -> sendKey(KeyEvent.KEYCODE_1)
                "2" -> sendKey(KeyEvent.KEYCODE_2)
                "3" -> sendKey(KeyEvent.KEYCODE_3)
                "4" -> sendKey(KeyEvent.KEYCODE_4)
                "5" -> sendKey(KeyEvent.KEYCODE_5)
                "6" -> sendKey(KeyEvent.KEYCODE_6)
                "7" -> sendKey(KeyEvent.KEYCODE_7)
                "8" -> sendKey(KeyEvent.KEYCODE_8)
                "9" -> sendKey(KeyEvent.KEYCODE_9)
                "0" -> sendKey(KeyEvent.KEYCODE_0)
            }
        }
    }

    // Restore last channel when channels are loaded
    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && !isPlaying && !hasAutoPlayed) {
             if (PreferencesManager.isRestoreChannelEnabled(context)) {
                 val lastIndex = PreferencesManager.getLastChannel(context)
                 if (lastIndex >= 0 && lastIndex < channels.size) {
                     currentChannelIndex = lastIndex
                     isPlaying = true
                     hasAutoPlayed = true
                     AppLogger.log("Restoring last channel index: $lastIndex")
                 }
             }
        }
    }

    // Save last channel (debounced — write to prefs only once per channel change)
    LaunchedEffect(currentChannelIndex) {
        if (currentChannelIndex != -1) {
            withContext(Dispatchers.IO) {
                PreferencesManager.saveLastChannel(context, currentChannelIndex)
            }
        }
    }

    // Fetch Playlist — with in-memory cache
    LaunchedEffect(currentPlaylistUrl) {
        // Serve from cache instantly if same URL
        if (ChannelCache.url == currentPlaylistUrl && ChannelCache.channels.isNotEmpty()) {
            channels = ChannelCache.channels
            isLoading = false
            AppLogger.log("Loaded ${channels.size} channels from cache")
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        channels = emptyList()
        AppLogger.log("Fetching playlist: $currentPlaylistUrl")
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(currentPlaylistUrl)
                    .build()

                AppLogger.log("Executing network request...")
                sharedHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Network Error: ${response.code} ${response.message}")
                    }

                    val body = response.body?.string() ?: ""
                    if (body.isEmpty()) {
                        throw IOException("Playlist is empty")
                    }

                    val parsedChannels = parseM3u(body)
                    AppLogger.log("Parsed ${parsedChannels.size} channels")

                    withContext(Dispatchers.Main) {
                        if (parsedChannels.isEmpty()) {
                            errorMessage = "No channels found in playlist."
                        } else {
                            channels = parsedChannels
                            ChannelCache.url = currentPlaylistUrl
                            ChannelCache.channels = parsedChannels
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("Error loading playlist: ${e.message}")
                withContext(Dispatchers.Main) {
                    // If cache exists for this URL — show it even on error
                    if (ChannelCache.url == currentPlaylistUrl && ChannelCache.channels.isNotEmpty()) {
                        channels = ChannelCache.channels
                        isLoading = false
                    } else {
                        errorMessage = "Error: ${e.message}"
                        isLoading = false
                    }
                }
            }
        }
    }

    // Back Handler to exit player to menu
    BackHandler(enabled = isPlaying) {
        isPlaying = false
        // Reset current channel to avoid auto-play loop if needed, 
        // but user might want to resume. 
        // If we want to return to menu, just isPlaying = false is enough.
        // However, we should ensure hasAutoPlayed is true so it doesn't auto-launch again immediately.
        hasAutoPlayed = true 
    }

    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(DreamWhite)) {
            if (isPlaying && currentChannelIndex != -1) {
                VideoPlayerScreen(
                    channel = channels[currentChannelIndex],
                    onBack = {
                        isPlaying = false
                        hasAutoPlayed = true
                    },
                    onNextChannel = {
                        if (channels.isNotEmpty()) {
                            currentChannelIndex = (currentChannelIndex + 1) % channels.size
                        }
                    },
                    onPrevChannel = {
                        if (channels.isNotEmpty()) {
                            currentChannelIndex = if (currentChannelIndex - 1 < 0) channels.lastIndex else currentChannelIndex - 1
                        }
                    },
                    onDigitInput = onDigitKey
                )
            } else {
                MainScreen(
                    channels = channels,
                    currentPlaylistUrl = currentPlaylistUrl,
                    onChannelSelected = { index ->
                        currentChannelIndex = index
                        isPlaying = true
                    },
                    onPlaylistSelected = { newUrl ->
                        if (newUrl != currentPlaylistUrl) {
                            currentPlaylistUrl = newUrl
                            PreferencesManager.saveCurrentPlaylist(context, newUrl)
                            isPlaying = false
                            currentChannelIndex = -1
                        }
                    },
                    onDigitInput = onDigitKey,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
            
            // Digit Overlay
            if (showDigitOverlay) {
                 Box(
                     modifier = Modifier
                         .align(Alignment.TopStart)
                         .padding(32.dp)
                         .background(DreamBlack.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                         .padding(16.dp)
                 ) {
                     Text(
                         text = "Channel: $digitBuffer-",
                         style = MaterialTheme.typography.headlineLarge,
                         color = Color.White
                     )
                 }
            }
            
            // Logs Overlay (Draggable)
            if (AppLogger.isVisible || errorMessage != null) {
                DraggableLogsOverlay()
            }
        }
    }
}


// --- Modern White UI Theme ---
val DreamWhite = Color(0xFFF5F5F5)
val DreamBlack = Color(0xFF121212)
val DreamAccent = Color(0xFF333333) // Dark Grey for focus
val DreamFocus = Color(0xFF000000)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DreamWhite),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "dreamTV",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 80.sp
            ),
            color = DreamBlack
        )
    }
    LaunchedEffect(Unit) {
        delay(800)
        onTimeout()
    }
}

@Composable
fun MainScreen(
    channels: List<Channel>,
    currentPlaylistUrl: String,
    onChannelSelected: (Int) -> Unit,
    onPlaylistSelected: (String) -> Unit,
    onDigitInput: (Int) -> Boolean,
    isLoading: Boolean,
    errorMessage: String?
) {
    val context = LocalContext.current
    val presets = PreferencesManager.PRESETS
    var customPlaylists by remember { mutableStateOf(PreferencesManager.getCustomPlaylists(context)) }
    
    // Tabs: Playlists (as tabs) + Settings
    // Dynamic tabs based on presets and custom playlists
    val tabs = remember(customPlaylists, currentPlaylistUrl) {
        val list = mutableListOf<String>()
        // 1. Always show Main (Default)
        list.add("ГЛАВНАЯ")
        
        // 2. Add Custom Playlists
        customPlaylists.forEach { list.add(it.first.uppercase()) }
        
        // 3. Special Case: If current URL is a Preset but NOT Default (e.g. Backup), show it as a tab
        // We need to find if currentPlaylistUrl matches any preset other than Default
        // Presets[0] is Default.
        val activePreset = presets.drop(1).find { it.second == currentPlaylistUrl }
        if (activePreset != null) {
            list.add(activePreset.first.uppercase())
        }

        list.add("ПЛЕЙЛИСТЫ") // Manager
        list.add("НАСТРОЙКИ")
        list
    }
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Sync selected tab with current playlist URL
    LaunchedEffect(currentPlaylistUrl, customPlaylists, tabs) {
        if (currentPlaylistUrl == presets[0].second) {
            selectedTabIndex = 0 // Main
        } else {
             // Check custom playlists
            val customIndex = customPlaylists.indexOfFirst { it.second == currentPlaylistUrl }
            if (customIndex != -1) {
                selectedTabIndex = 1 + customIndex
            } else {
                // Check if it's a hidden preset (e.g. Backup)
                // If it is, it should be in the tabs list now (added dynamically)
                val activePreset = presets.drop(1).find { it.second == currentPlaylistUrl }
                if (activePreset != null) {
                    // It's after custom playlists
                    selectedTabIndex = 1 + customPlaylists.size
                } else {
                     // Fallback (Manager or something)
                     selectedTabIndex = 0
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DreamWhite)) {
        // --- Xbox Blade Style Header ---
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = DreamWhite,
            contentColor = DreamBlack,
            edgePadding = 32.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    Material3TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = DreamBlack,
                        height = 4.dp
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                androidx.compose.material3.Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        // Handle Tab Click
                        if (index == 0) {
                            onPlaylistSelected(presets[0].second)
                        } else if (index <= customPlaylists.size) {
                            // Custom Playlist clicked
                            onPlaylistSelected(customPlaylists[index - 1].second)
                        } else if (title == "ПЛЕЙЛИСТЫ") {
                             // Do nothing, content changes automatically
                        } else if (title == "НАСТРОЙКИ") {
                             // Do nothing
                        } else {
                             // Must be the dynamic preset (Backup)
                             // Find it
                             val preset = presets.find { it.first.uppercase() == title }
                             if (preset != null) {
                                 onPlaylistSelected(preset.second)
                             }
                        }
                        // Manager and Settings don't change playlist
                    },
                    text = {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) DreamBlack else Color.Gray
                        )
                    }
                )
            }
        }

        // --- Content Area ---
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Determine content based on tab index
            if (selectedTabIndex == tabs.lastIndex) {
                 // Settings
                 NewSettingsScreen(context)
            } else if (selectedTabIndex == tabs.lastIndex - 1) {
                 // Playlists Manager
                 PlaylistsScreen(
                    currentUrl = currentPlaylistUrl,
                    presets = presets,
                    customPlaylists = customPlaylists,
                    onPlaylistSelected = {
                        onPlaylistSelected(it)
                        // Tab will update via LaunchedEffect
                    },
                    onCustomPlaylistAdded = { name, url ->
                         PreferencesManager.addCustomPlaylist(context, name, url)
                         customPlaylists = PreferencesManager.getCustomPlaylists(context)
                    },
                    onCustomPlaylistRemoved = { url ->
                         PreferencesManager.removeCustomPlaylist(context, url)
                         customPlaylists = PreferencesManager.getCustomPlaylists(context)
                    }
                )
            } else {
                // Channel Grid (Home or Custom Playlist)
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Загрузка каналов...", color = DreamBlack, style = MaterialTheme.typography.headlineMedium)
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ошибка: $errorMessage", color = Color.Red, style = MaterialTheme.typography.headlineMedium)
                    }
                } else {
                    ChannelGrid(channels, onChannelSelected, onDigitInput)
                }
            }
        }
    }
}

@Composable
fun ChannelGrid(
    channels: List<Channel>,
    onChannelSelected: (Int) -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    var partialQuery by remember { mutableStateOf("") } // live partial from Vosk
    var isRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    var micIsFocused by remember { mutableStateOf(false) }
    var keyboardVisible by remember { mutableStateOf(false) }

    // Stop recording when composable leaves screen
    DisposableEffect(Unit) {
        onDispose { if (isRecording) VoskManager.stopListening() }
    }

    fun toggleVosk() {
        if (isRecording) {
            VoskManager.stopListening()
            isRecording = false
            partialQuery = ""
            searchQuery = ""
            AppLogger.log("Vosk: stopped manually")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Нет разрешения на микрофон", Toast.LENGTH_SHORT).show()
            AppLogger.log("Vosk: no permission")
            return
        }
        if (!VoskManager.isReady()) {
            Toast.makeText(context, "Модель загружается, подождите...", Toast.LENGTH_SHORT).show()
            AppLogger.log("Vosk: model not ready")
            return
        }
        isRecording = true
        partialQuery = ""
        searchQuery = ""
        AppLogger.log("Vosk: starting listening...")
        VoskManager.startListening(
            onPartial = { partial ->
                partialQuery = partial
                AppLogger.log("Vosk partial: $partial")
            },
            onResult = { result ->
                if (result.isNotBlank()) {
                    searchQuery = result
                    AppLogger.log("Vosk result: $result")
                }
                isRecording = false
                partialQuery = ""
            },
            onError = { err ->
                AppLogger.log("Vosk error: $err")
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                isRecording = false
                partialQuery = ""
            }
        )
    }

    val displayQuery = if (isRecording && partialQuery.isNotBlank()) partialQuery else searchQuery

    val filteredChannels = remember(searchQuery, channels) {
        if (searchQuery.isBlank()) channels
        else {
            val q = searchQuery.lowercase()
            channels.filter { it.name.lowercase().contains(q) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = displayQuery,
                onValueChange = { if (!isRecording) searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            if (keyboardVisible) keyboardController?.show()
                            else keyboardController?.hide()
                        } else {
                            keyboardVisible = false
                            keyboardController?.hide()
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Enter, Key.DirectionCenter -> {
                                    keyboardVisible = true
                                    keyboardController?.show()
                                    true
                                }
                                Key.DirectionRight -> {
                                    micFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    focusManager.moveFocus(FocusDirection.Down)
                                    true
                                }
                                Key.DirectionUp -> {
                                    focusManager.moveFocus(FocusDirection.Up)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                placeholder = { Text(if (isRecording) "Говорите..." else "Поиск каналов...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            // Кнопка микрофона — отдельная, фокусируется D-pad
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(48.dp)
                    .focusRequester(micFocusRequester)
                    .onFocusChanged { micIsFocused = it.isFocused }
                    .focusable()
                    .background(
                        when {
                            isRecording -> Color.Red
                            micIsFocused -> DreamBlack
                            else -> Color.LightGray
                        },
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { toggleVosk() }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                        ) {
                            toggleVosk()
                            true
                        } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                            searchFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Голосовой поиск",
                    tint = if (isRecording || micIsFocused) Color.White else DreamBlack
                )
            }
        }

        if (filteredChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "Нет каналов" else "Не найдено: \"$searchQuery\"",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(filteredChannels, key = { _, ch -> ch.url }) { _, channel ->
                    val originalIndex = channel.originalIndex
                    ChannelCard(
                        index = originalIndex,
                        channel = channel,
                        onClick = { onChannelSelected(originalIndex) },
                        onDigitInput = onDigitInput
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    index: Int,
    channel: Channel,
    onClick: () -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Pre-build ImageRequest outside of recomposition hot path
    val imageRequest = remember(channel.logo) {
        if (channel.logo != null) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .crossfade(false)
                .size(120, 68) // Limit decode size — saves RAM and CPU on ARM v7
                .memoryCacheKey(channel.logo)
                .diskCacheKey(channel.logo)
                .build()
        } else null
    }

    Box(
        modifier = Modifier
            .background(if (isFocused) DreamBlack else Color.White, shape = RoundedCornerShape(8.dp))
            .border(2.dp, if (isFocused) DreamBlack else Color.LightGray, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(12.dp)
            .aspectRatio(16f / 9f)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onDigitInput(event.nativeKeyEvent.keyCode)
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Text(
                text = "${index + 1}. ${channel.name}",
                color = if (isFocused) Color.White else DreamBlack,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
    }
}

@Composable
fun PlaylistsScreen(
    currentUrl: String,
    presets: List<Pair<String, String>>,
    customPlaylists: List<Pair<String, String>>,
    onPlaylistSelected: (String) -> Unit,
    onCustomPlaylistAdded: (String, String) -> Unit,
    onCustomPlaylistRemoved: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("https://") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Стандартные плейлисты", style = MaterialTheme.typography.titleLarge, color = DreamBlack) }
        
        items(presets) { (name, url) ->
            PlaylistCard(name, url, url == currentUrl) { onPlaylistSelected(url) }
        }

        if (customPlaylists.isNotEmpty()) {
            item { Text("Мои плейлисты", style = MaterialTheme.typography.titleLarge, color = DreamBlack) }
            items(customPlaylists) { (name, url) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistCard(name, url, url == currentUrl) { onPlaylistSelected(url) }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCustomPlaylistRemoved(url) },
                        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.Red)
                    ) { Text("X") }
                }
            }
        }

        item {
            Text("Добавить плейлист", style = MaterialTheme.typography.titleLarge, color = DreamBlack)
            Column(modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(16.dp)) {
                TextField(value = newName, onValueChange = { newName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Ссылка") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                            onCustomPlaylistAdded(newName, newUrl)
                            newName = ""
                            newUrl = "https://"
                        }
                    },
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = DreamBlack)
                ) {
                    Text("Добавить", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(name: String, url: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isSelected || isFocused) Modifier.background(DreamBlack)
                else Modifier.background(Color.White)
            ),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (isSelected || isFocused) Color.White else DreamBlack
        )
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun NewSettingsScreen(context: Context) {
    var autoStart by remember { mutableStateOf(PreferencesManager.isAutoStartEnabled(context)) }
    var restoreChannel by remember { mutableStateOf(PreferencesManager.isRestoreChannelEnabled(context)) }
    var logsEnabled by remember { mutableStateOf(PreferencesManager.isLogsEnabled(context)) }
    var webRemoteEnabled by remember { mutableStateOf(PreferencesManager.isWebRemoteEnabled(context)) }
    var videoQuality by remember { mutableIntStateOf(PreferencesManager.getVideoQuality(context)) }
    var playerType by remember { mutableIntStateOf(PreferencesManager.getPlayerType(context)) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var qrCodeData by remember { mutableStateOf<QRCodeResponse?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Настройки приложения", style = MaterialTheme.typography.headlineMedium, color = DreamBlack) }

        item {
            SettingSwitchItem(
                title = "Включить логирование",
                description = "Отображать технические логи на экране (для отладки)",
                checked = logsEnabled,
                onCheckedChange = {
                    logsEnabled = it
                    PreferencesManager.setLogsEnabled(context, it)
                    AppLogger.isVisible = it
                }
            )
        }

        item {
            SettingSwitchItem(
                title = "Веб-пульт 📱",
                description = "Управлять ТВ со смартфона в одной сети",
                checked = webRemoteEnabled,
                onCheckedChange = {
                    webRemoteEnabled = it
                    PreferencesManager.setWebRemoteEnabled(context, it)
                }
            )
        }

        if (webRemoteEnabled) {
            item {
                Button(
                    onClick = {
                        // Fetch QR code async
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val request = Request.Builder()
                                    .url("http://127.0.0.1:8080/api/qrcode")
                                    .build()
                                val response = sharedHttpClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val jsonStr = response.body?.string() ?: ""
                                    val data = Json.decodeFromString<QRCodeResponse>(jsonStr)
                                    withContext(Dispatchers.Main) {
                                        qrCodeData = data
                                        showQRDialog = true
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.log("QR code fetch error: ${e.message}")
                            }
                        }
                    },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = DreamBlack,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📲 Показать QR-код пульта")
                }
            }
        }

        item {
            SettingSwitchItem(
                title = "Автозапуск при включении",
                description = "Запускать dreamTV автоматически при старте устройства",
                checked = autoStart,
                onCheckedChange = {
                    autoStart = it
                    PreferencesManager.setAutoStartEnabled(context, it)
                }
            )
        }

        item {
            SettingSwitchItem(
                title = "Запомнить последний канал",
                description = "Автоматически включать последний канал при запуске",
                checked = restoreChannel,
                onCheckedChange = {
                    restoreChannel = it
                    PreferencesManager.setRestoreChannelEnabled(context, it)
                }
            )
        }
        
        item {
             Text("Качество видео", style = MaterialTheme.typography.titleMedium, color = DreamBlack)
             val qualityText = when(videoQuality) {
                0 -> "Максимальное"
                1 -> "Высокое"
                2 -> "Среднее"
                3 -> "Низкое"
                else -> "Авто"
            }
            Button(
                onClick = {
                    videoQuality = (videoQuality + 1) % 4
                    PreferencesManager.saveVideoQuality(context, videoQuality)
                },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = DreamBlack,
                    contentColor = Color.White
                )
            ) {
                Text("Качество: $qualityText")
            }
        }

        item {
            Text("Плеер (Движок)", style = MaterialTheme.typography.titleMedium, color = DreamBlack)
            Text(
                text = "Если приложение вылетает или нет звука, попробуйте другие движки",
                style = MaterialTheme.typography.bodySmall, 
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            playerType = 0
                            PreferencesManager.setPlayerType(context, 0)
                        },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (playerType == 0) DreamBlack else Color.LightGray,
                            contentColor = if (playerType == 0) Color.White else DreamBlack
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("IjkPlayer")
                    }
                    
                    Button(
                        onClick = { 
                            playerType = 1
                            PreferencesManager.setPlayerType(context, 1)
                        },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (playerType == 1) DreamBlack else Color.LightGray,
                            contentColor = if (playerType == 1) Color.White else DreamBlack
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Системный")
                    }
                }

                Button(
                    onClick = { 
                        playerType = 3
                        PreferencesManager.setPlayerType(context, 3)
                    },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = if (playerType == 3) DreamBlack else Color.LightGray,
                        contentColor = if (playerType == 3) Color.White else DreamBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("VLC (Мощный)")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showAboutDialog = true },
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = DreamBlack, contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) { Text("О приложении") }

                Button(
                    onClick = { showCrashLogDialog = true },
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFF7B0000), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) { Text("📋 Журнал") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showQRDialog && qrCodeData != null) {
        val qrData = qrCodeData
        androidx.compose.ui.window.Dialog(onDismissRequest = { showQRDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📲 Веб-пульт",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = DreamBlack
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code Image
                    if (qrData != null) {
                        val decodedBytes = android.util.Base64.decode(qrData.qrCodeBase64, android.util.Base64.DEFAULT)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // URL
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.LightGray, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Локальный адрес:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        SelectionContainer {
                            Text(
                                text = qrData?.localUrl ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = DreamBlack
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Откройте ссылку на смартфоне в одной сети",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showQRDialog = false },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = DreamBlack,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAboutDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "DreamTV",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = DreamBlack
                    )
                    Text(
                        text = "Версия: ${getAppVersion(context)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Это приложение — проект энтузиаста. Оно появилось, потому что многие аналоги либо платные, либо перегружены рекламой, а IPTV часто слишком сложен в настройке. Я решил сделать удобную и простую альтернативу.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamBlack,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Конечно, этот путь труднее, но круче! Изначально я сделал это приложение для отца, чтобы ему было намного проще пользоваться ТВ-приставкой.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamBlack,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Разработчик: dreamcatcher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DreamBlack
                    )
                    Text(
                        text = "dreampartners.online/dev",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Blue,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAboutDialog = false },
                        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = DreamBlack)
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }

    // Crash Log Dialog
    if (showCrashLogDialog) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        var logText by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            logText = withContext(Dispatchers.IO) { GlobalErrorHandler.readCrashLog(context) }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCrashLogDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("📋 Журнал ошибок", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                SelectionContainer {
                                    Text(
                                        text = logText,
                                        color = if (logText == "Журнал пуст") Color.Gray else Color(0xFFFF6B6B),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val clip = android.content.ClipData.newPlainText("DreamTV Log", logText)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                            },
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFF2979FF), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("📋 Копировать") }

                        Button(
                            onClick = {
                                GlobalErrorHandler.clearCrashLog(context)
                                logText = "Журнал пуст"
                            },
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFF7B0000), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("🗑 Очистить") }

                        Button(
                            onClick = { showCrashLogDialog = false },
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.DarkGray, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("Закрыть") }
                    }
                }
            }
        }
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

@Composable
fun SettingSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFocused) Color.LightGray else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DreamBlack, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = null) // Handled by Row click
    }
}


private fun configureIjkPlayer(player: IjkMediaPlayer) {
    player.apply {
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
        
        // CRITICAL: Set User-Agent to match Chrome/ExoPlayer to avoid "Wink" territory block
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
    }
}


@Composable
fun VideoPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    val context = LocalContext.current
    val playerType = remember { PreferencesManager.getPlayerType(context) }
    
    when (playerType) {
        0 -> IjkPlayerScreen(channel, onBack, onNextChannel, onPrevChannel, onDigitInput)
        1 -> SystemPlayerScreen(channel, onBack, onNextChannel, onPrevChannel, onDigitInput)
        3 -> VlcPlayerScreen(channel, onBack, onNextChannel, onPrevChannel, onDigitInput)
        else -> IjkPlayerScreen(channel, onBack, onNextChannel, onPrevChannel, onDigitInput)
    }
}

@Composable
fun VlcPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(true) }
    var showTechInfo by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Auto-hide overlay
    LaunchedEffect(lastInteractionTime) {
        showOverlay = true
        delay(4000)
        showOverlay = false
    }

    val libVlc = remember { LibVLC(context, ArrayList<String>().apply {
        add("--aout=opensles")
        add("--audio-time-stretch")
        add("-vvv") // Verbosity
    }) }
    
    val mediaPlayer = remember { VlcMediaPlayer(libVlc) }
    
    LaunchedEffect(channel) {
        lastInteractionTime = System.currentTimeMillis()
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        
        val media = Media(libVlc, Uri.parse(channel.url))
        val userAgent = channel.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        media.addOption(":http-user-agent=$userAgent")
        media.addOption(":network-caching=1500")
        
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            val mp = mediaPlayer
            val vlc = libVlc
            Thread {
                try {
                    mp.stop()
                    mp.release()
                    vlc.release()
                } catch (e: Exception) {
                    Log.e("DreamTV", "VLC release error: ${e.message}")
                }
            }.start()
        }
    }
    
    // Gesture State
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        lastInteractionTime = System.currentTimeMillis()
                        if (abs(offsetX) > abs(offsetY)) {
                            if (offsetX > 50) onBack()
                            else if (offsetX < -50) showTechInfo = true
                        } else {
                            if (offsetY > 50) onPrevChannel()
                            else if (offsetY < -50) onNextChannel()
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                    // Attach VLC to Surface
                    val vout = mediaPlayer.vlcVout
                    vout.setVideoView(this)
                    vout.attachViews()
                    
                    // Fix resizing issue
                    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                             val width = right - left
                             val height = bottom - top
                             vout.setWindowSize(width, height)
                        }
                    }
                    
                    isFocusable = true
                    isFocusableInTouchMode = true
                    keepScreenOn = true
                    requestFocus()
                    
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            lastInteractionTime = System.currentTimeMillis()
                            if (onDigitInput(keyCode)) return@setOnKeyListener true

                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { onNextChannel(); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { onPrevChannel(); true }
                                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                    AppLogger.log("VLC: Back/Escape pressed, returning to menu")
                                    onBack()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    AppLogger.log("VLC: DPAD_RIGHT pressed, returning to menu")
                                    onBack()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_INFO -> {
                                    showTechInfo = !showTechInfo
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                 // Clean up if needed on view release, but handled in DisposableEffect
                 mediaPlayer.vlcVout.detachViews()
            }
        )
        
        if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(channel.name, color = Color.White, style = MaterialTheme.typography.headlineLarge)
            }
        }
        
        if (showTechInfo) {
             Box(
                 modifier = Modifier.align(Alignment.CenterStart).padding(16.dp).background(Color.Black.copy(alpha = 0.8f)).padding(16.dp)
             ) {
                 Column {
                     Text("Technical Info", color = Color.Yellow, fontWeight = FontWeight.Bold)
                     Text("URL: ${channel.url}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                     Text("Player: VLC (LibVLC)", color = Color.Magenta)
                     Text("Engine: 3.6.0", color = Color.White)
                 }
             }
        }
    }
}

@Composable
fun SystemPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    var showOverlay by remember { mutableStateOf(true) }
    var showTechInfo by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(true) }
    
    // Auto-hide overlay
    LaunchedEffect(lastInteractionTime) {
        showOverlay = true
        delay(4000)
        showOverlay = false
    }

    // Gesture State
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        lastInteractionTime = System.currentTimeMillis()
                        if (abs(offsetX) > abs(offsetY)) {
                            // Horizontal Swipe
                            if (offsetX > 50) {
                                // Swipe Right -> Open Menu
                                onBack()
                            } else if (offsetX < -50) {
                                // Swipe Left -> Show Tech Info
                                showTechInfo = true
                            }
                        } else {
                            // Vertical Swipe
                            if (offsetY > 50) {
                                // Swipe Down -> Prev Channel
                                onPrevChannel()
                            } else if (offsetY < -50) {
                                // Swipe Up -> Next Channel
                                onNextChannel()
                            }
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        // Use key to force recreation of VideoView when channel changes
        key(channel.url) {
             AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        setOnPreparedListener { mp ->
                            mp.start()
                            isPlaying = true
                        }
                        
                        setOnCompletionListener { start() }
                        
                        setOnErrorListener { _, what, extra ->
                            AppLogger.log("VideoView Error: $what, $extra")
                            true 
                        }
                        
                        isFocusable = true
                        isFocusableInTouchMode = true
                        keepScreenOn = true
                        requestFocus()
                        
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                lastInteractionTime = System.currentTimeMillis()
                                if (onDigitInput(keyCode)) return@setOnKeyListener true

                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> { onNextChannel(); true }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> { onPrevChannel(); true }
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                        AppLogger.log("SystemPlayer: Back/Escape pressed, returning to menu")
                                        onBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        AppLogger.log("SystemPlayer: DPAD_RIGHT pressed, returning to menu")
                                        onBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        if (isPlaying) {
                                            pause()
                                            isPlaying = false
                                        } else {
                                            start()
                                            isPlaying = true
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_INFO -> {
                                        showTechInfo = !showTechInfo
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        
                        // Set URI with headers
                        val userAgent = channel.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        val headers = mapOf("User-Agent" to userAgent)
                        setVideoURI(Uri.parse(channel.url), headers)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
        
        if (showTechInfo) {
             Box(
                 modifier = Modifier
                     .align(Alignment.CenterStart)
                     .padding(start = 0.dp)
                     .background(Color.Black.copy(alpha = 0.8f))
                     .padding(16.dp)
             ) {
                 Column {
                     Text("Technical Info", color = Color.Yellow, fontWeight = FontWeight.Bold)
                     Text("URL: ${channel.url}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                     Text("Player: System VideoView (Native)", color = Color.Green)
                     Text("Fallback Mode", color = Color.Gray)
                 }
             }
        }
    }
}

@Composable
fun IjkPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onDigitInput: (Int) -> Boolean
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(true) }
    var showTechInfo by remember { mutableStateOf(false) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // IjkMediaPlayer Setup
    val isReleased = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val ijkMediaPlayer = remember {
        IjkMediaPlayer().apply {
            // Standard Options
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
        }
    }

    // Auto-hide overlay
    LaunchedEffect(lastInteractionTime) {
        showOverlay = true
        delay(4000)
        showOverlay = false
    }

    // Prepare MediaSource (options set once in remember block above)
    LaunchedEffect(channel) {
        if (isReleased.get()) return@LaunchedEffect
        lastInteractionTime = System.currentTimeMillis()
        try {
            if (ijkMediaPlayer.isPlaying) ijkMediaPlayer.stop()
            ijkMediaPlayer.reset()

            val userAgent = channel.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent)

            ijkMediaPlayer.dataSource = channel.url
            surfaceHolder?.let { ijkMediaPlayer.setDisplay(it) }
            ijkMediaPlayer.prepareAsync()
        } catch (e: Exception) {
            AppLogger.log("IjkPlayer prepare error: ${e.message}")
        }
    }

    // Lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (isReleased.get()) return@LifecycleEventObserver
            try {
                if (event == Lifecycle.Event.ON_PAUSE) {
                    if (ijkMediaPlayer.isPlaying) ijkMediaPlayer.pause()
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    if (!ijkMediaPlayer.isPlaying) ijkMediaPlayer.start()
                }
            } catch (e: Exception) {
                AppLogger.log("IjkPlayer lifecycle error: ${e.message}")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isReleased.compareAndSet(false, true)) {
                // Release on background thread — never block the UI thread on player teardown
                val playerRef = ijkMediaPlayer
                Thread {
                    try {
                        playerRef.setDisplay(null)
                        playerRef.stop()
                        playerRef.release()
                    } catch (e: Exception) {
                        Log.e("DreamTV", "IjkPlayer release error: ${e.message}")
                    }
                }.start()
            }
        }
    }
    
    // Gesture State
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        lastInteractionTime = System.currentTimeMillis()
                        if (abs(offsetX) > abs(offsetY)) {
                            // Horizontal Swipe
                            if (offsetX > 50) {
                                // Swipe Right -> Open Menu
                                onBack()
                            } else if (offsetX < -50) {
                                // Swipe Left -> Show Tech Info
                                showTechInfo = true
                            }
                        } else {
                            // Vertical Swipe
                            if (offsetY > 50) {
                                // Swipe Down -> Prev Channel
                                onPrevChannel()
                            } else if (offsetY < -50) {
                                // Swipe Up -> Next Channel
                                onNextChannel()
                            }
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        // SurfaceView for IjkPlayer
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            if (isReleased.get()) return
                            surfaceHolder = h
                            try { ijkMediaPlayer.setDisplay(h) } catch (e: Exception) { AppLogger.log("setDisplay error: ${e.message}") }
                        }

                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            surfaceHolder = null
                            if (isReleased.get()) return
                            try { ijkMediaPlayer.setDisplay(null) } catch (e: Exception) { AppLogger.log("setDisplay null error: ${e.message}") }
                        }
                    })
                    
                    // Input Handling
                    isFocusable = true
                    isFocusableInTouchMode = true
                    keepScreenOn = true 
                    requestFocus()
                    
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                             // Reset overlay timer on key press
                             lastInteractionTime = System.currentTimeMillis()

                             if (onDigitInput(keyCode)) return@setOnKeyListener true
                             when (keyCode) {
                                 KeyEvent.KEYCODE_DPAD_UP -> { onNextChannel(); true }
                                 KeyEvent.KEYCODE_DPAD_DOWN -> { onPrevChannel(); true }
                                 KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                     AppLogger.log("IjkPlayer: Back/Escape pressed, returning to menu")
                                     onBack()
                                     true
                                 }
                                 KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                     AppLogger.log("IjkPlayer: DPAD_RIGHT pressed, returning to menu")
                                     onBack()
                                     true
                                 }
                                 KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                     if (ijkMediaPlayer.isPlaying) ijkMediaPlayer.pause() else ijkMediaPlayer.start()
                                     true
                                 }
                                 KeyEvent.KEYCODE_DPAD_LEFT -> {
                                     showTechInfo = !showTechInfo
                                     true
                                 }
                                 KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_INFO -> {
                                     showTechInfo = !showTechInfo
                                     true
                                 }
                                 else -> false
                             }
                        } else false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { 
                it.requestFocus()
            }
        )

        if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
        
        if (showTechInfo) {
             Box(
                 modifier = Modifier
                     .align(Alignment.CenterStart)
                     .padding(start = 0.dp)
                     .background(Color.Black.copy(alpha = 0.8f))
                     .padding(16.dp)
             ) {
                 Column {
                     Text("Technical Info", color = Color.Yellow, fontWeight = FontWeight.Bold)
                     Text("URL: ${channel.url}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                     
                     // Player Info
                     Text("Player: IjkPlayer (Optimized)", color = Color.Cyan)
                     Text("Decoder: MediaCodec (Hardware)", color = Color.White)
                 }
             }
        }
    }
}



// Unused ExoPlayer imports kept to avoid breaking other file parts if referenced, 
// but VideoPlayerScreen is fully replaced.
/*
@Composable
fun OldVideoPlayerScreen(...) { ... }
*/

private val LOGO_REGEX = "tvg-logo=\"([^\"]*)\"".toRegex()

fun parseM3u(content: String): List<Channel> {
    val channels = mutableListOf<Channel>()
    try {
        val lines = content.lines()
        var currentName: String? = null
        var currentLogo: String? = null
        var currentUserAgent: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF")) {
                val match = LOGO_REGEX.find(trimmed)
                if (match != null) {
                    currentLogo = match.groupValues[1].trim()
                }
                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1) {
                    currentName = trimmed.substring(commaIndex + 1).trim()
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:http-user-agent=")) {
                currentUserAgent = trimmed.removePrefix("#EXTVLCOPT:http-user-agent=").trim()
            } else if (!trimmed.startsWith("#")) {
                if (currentName != null) {
                    channels.add(Channel(currentName, trimmed, currentLogo, currentUserAgent, channels.size))
                    currentName = null
                    currentLogo = null
                    currentUserAgent = null
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.log("Error parsing M3U: ${e.message}")
    }
    return channels
}
