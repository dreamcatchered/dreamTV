package com.example.dreamtv

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import fi.iki.elonen.NanoHTTPD

@Serializable
data class PlayerStatus(
    val currentChannel: String = "",
    val currentChannelNumber: Int = -1,
    val isPlaying: Boolean = false,
    val volume: Int = 50,
    val playerType: String = "ijkplayer",
    val currentPlaylist: String = ""
)

@Serializable
data class RemoteCommand(
    val command: String,
    val value: Int? = null
)

@Serializable
data class ChannelInfo(
    val id: Int,
    val name: String,
    val url: String,
    val logoUrl: String = ""
)

@Serializable
data class QRCodeResponse(
    val qrCodeBase64: String,
    val localUrl: String,
    val deviceName: String
)

class WebRemoteService : Service() {
    private val binder = LocalBinder()
    private var server: RemoteServer? = null

    companion object {
        const val PORT = 8080
        const val TAG = "WebRemoteService"
        var playerStatusCallback: ((PlayerStatus) -> Unit)? = null
        var commandCallback: ((RemoteCommand) -> Unit)? = null
        var getChannelsCallback: (() -> List<ChannelInfo>)? = null
        var getStatusCallback: (() -> PlayerStatus)? = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): WebRemoteService = this@WebRemoteService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting WebRemoteService")
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (server != null && server?.isAlive == true) return

        try {
            val localIp = getLocalIpAddress()
            server = RemoteServer(PORT)
            server?.start()
            Log.d(TAG, "WebRemoteService started on http://$localIp:$PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    private inner class RemoteServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            try {
                // Handle preflight OPTIONS request for CORS
                if (method == Method.OPTIONS) {
                    val response = newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "")
                    addCorsHeaders(response)
                    return response
                }

                if (uri == "/api/status" && method == Method.GET) {
                    val rawStatus = getStatusCallback?.invoke() ?: PlayerStatus()
                    // Sanitize strings to avoid JSON breaking
                    val status = rawStatus.copy(
                        currentChannel = sanitizeString(rawStatus.currentChannel),
                        currentPlaylist = sanitizeString(rawStatus.currentPlaylist)
                    )
                    
                    val json = try {
                        Json.encodeToString(status)
                    } catch (e: Exception) {
                        // Fallback manual JSON if serialization fails
                        """{"currentChannel":"","currentChannelNumber":-1,"isPlaying":false,"volume":50,"playerType":"error","currentPlaylist":""}"""
                    }
                    val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                    addCorsHeaders(response)
                    return response
                }

                if (uri == "/api/channels" && method == Method.GET) {
                    val channels = getChannelsCallback?.invoke() ?: emptyList()
                    val jsonArray = channels.map { ch ->
                        """{"id":${ch.id},"name":"${escapeJson(ch.name)}","url":"${escapeJson(ch.url)}","logoUrl":"${escapeJson(ch.logoUrl)}"}"""
                    }.joinToString(",", "[", "]")
                    val response = newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray)
                    addCorsHeaders(response)
                    return response
                }

                if (uri.startsWith("/api/channel/") && method == Method.POST) {
                    val id = uri.substringAfterLast("/").toIntOrNull()
                    if (id != null) {
                        commandCallback?.invoke(RemoteCommand("switchChannel", id))
                        val response = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
                        addCorsHeaders(response)
                        return response
                    } else {
                        val response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"invalid_id"}""")
                        addCorsHeaders(response)
                        return response
                    }
                }

                if (uri == "/api/command" && method == Method.POST) {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val body = map["postData"] ?: "{}"
                    
                    return try {
                        val cmd = Json.decodeFromString<RemoteCommand>(body)
                        commandCallback?.invoke(cmd)
                        val response = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
                        addCorsHeaders(response)
                        response
                    } catch (e: Exception) {
                        val safeMsg = escapeJson(e.message ?: "Unknown error")
                        val response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"$safeMsg"}""")
                        addCorsHeaders(response)
                        response
                    }
                }

                if (uri == "/api/qrcode" && method == Method.GET) {
                     val localIp = getLocalIpAddress()
                     val url = "http://$localIp:$PORT"
                     val qrBitmap = generateQRCode(url, 512)
                     val base64 = bitmapToBase64(qrBitmap)
                     val responseData = QRCodeResponse(
                         qrCodeBase64 = base64,
                         localUrl = url,
                         deviceName = android.os.Build.MODEL
                     )
                     val json = Json.encodeToString(responseData)
                     val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                     addCorsHeaders(response)
                     return response
                }

                if (uri == "/" || uri == "/index.html") {
                    val html = getWebInterfaceHTML()
                    val response = newFixedLengthResponse(Response.Status.OK, "text/html", html)
                    return response
                }

                return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing request", e)
                val safeMsg = escapeJson(e.message ?: "Internal Server Error")
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"error":"$safeMsg"}""")
            }
        }
    }
    
    private fun addCorsHeaders(response: NanoHTTPD.Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
    }

    private fun sanitizeString(str: String): String {
        return str.filter { it.code >= 32 && it.code != 127 } // Remove control characters
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .filter { it.code >= 32 } // Remove other control chars
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return "127.0.0.1"
    }

    private fun generateQRCode(text: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun getWebInterfaceHTML(): String {
        return """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>DreamTV Remote</title>
    
    <!-- Лучшие библиотеки для стилизации и иконок -->
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap" rel="stylesheet">
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <script>
        tailwind.config = {
            theme: {
                extend: {
                    fontFamily: { sans:['Inter', 'sans-serif'] },
                    colors: {
                        apple: {
                            bg: '#f5f5f7',
                            surface: '#ffffff',
                            text: '#1d1d1f',
                            subtext: '#86868b',
                            border: '#d2d2d7'
                        }
                    },
                    boxShadow: {
                        'apple': '0 4px 24px rgba(0, 0, 0, 0.04)',
                        'apple-pressed': '0 2px 10px rgba(0, 0, 0, 0.02)',
                        'dpad': '0 10px 40px rgba(0, 0, 0, 0.06)'
                    }
                }
            }
        }
    </script>

    <style>
        body {
            background-color: #f5f5f7;
            color: #1d1d1f;
            -webkit-tap-highlight-color: transparent;
            user-select: none;
        }

        /* Плавные анимации кнопок */
        .btn-press {
            transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .btn-press:active {
            transform: scale(0.92);
            background-color: #e8e8ed;
        }

        /* D-Pad (Кольцо управления) */
        .dpad-container {
            position: relative;
            width: 260px;
            height: 260px;
            border-radius: 50%;
            background: #ffffff;
            box-shadow: 0 10px 40px rgba(0,0,0,0.06), inset 0 0 0 1px rgba(0,0,0,0.03);
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            grid-template-rows: 1fr 1fr 1fr;
            grid-template-areas: 
                ". up ."
                "left ok right"
                ". down .";
            overflow: hidden;
        }

        .dpad-btn {
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            color: #86868b;
            transition: all 0.2s;
        }
        .dpad-btn:active {
            background: rgba(0,0,0,0.05);
            color: #1d1d1f;
        }
        .dpad-up { grid-area: up; }
        .dpad-down { grid-area: down; }
        .dpad-left { grid-area: left; }
        .dpad-right { grid-area: right; }
        
        .dpad-ok {
            grid-area: ok;
            border-radius: 50%;
            background: #f5f5f7;
            margin: 10px;
            box-shadow: inset 0 2px 4px rgba(0,0,0,0.02);
        }
        .dpad-ok:active {
            background: #e8e8ed;
            transform: scale(0.95);
        }

        /* Эффект Apple Intelligence (переливающийся градиент) */
        .ai-glow {
            position: relative;
        }
        .ai-glow::before {
            content: '';
            position: absolute;
            inset: -2px;
            border-radius: inherit;
            background: linear-gradient(90deg, #ff8a00, #e52e71, #9b2def, #2b86c5);
            background-size: 200% 200%;
            animation: gradientMove 3s ease infinite;
            z-index: -1;
            opacity: 0;
            transition: opacity 0.3s;
        }
        .ai-glow:focus-within::before {
            opacity: 0.5;
        }
        @keyframes gradientMove {
            0% { background-position: 0% 50%; }
            50% { background-position: 100% 50%; }
            100% { background-position: 0% 50%; }
        }

        /* Убираем скроллбар */
        ::-webkit-scrollbar { width: 0; height: 0; }
        
        /* Всплывающее уведомление (Toast) */
        #toast {
            transform: translateY(-100%);
            opacity: 0;
            transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }
        #toast.show {
            transform: translateY(0);
            opacity: 1;
        }
    </style>
</head>
<body class="min-h-screen flex justify-center pb-10 md:pb-0">

    <!-- Уведомление о действии (Toast) -->
    <div id="toast" class="fixed top-6 left-1/2 -translate-x-1/2 z-50 bg-white/80 backdrop-blur-xl px-6 py-3 rounded-full shadow-apple border border-apple-border/30 flex items-center gap-3">
        <i class="ph ph-info text-xl text-apple-text"></i>
        <span id="toast-text" class="font-medium text-sm"></span>
    </div>

    <!-- Главный контейнер -->
    <div class="w-full max-w-5xl p-4 md:p-8 grid grid-cols-1 lg:grid-cols-12 gap-6 md:gap-10">

        <!-- Левая колонка: Пульт управления (Mobile: Top, Desktop: Left 5 cols) -->
        <div class="lg:col-span-5 flex flex-col items-center gap-8">
            
            <!-- Заголовок -->
            <div class="w-full flex justify-between items-center px-2 pt-4">
                <div>
                    <h1 class="text-2xl font-semibold tracking-tight">DreamTV</h1>
                    <p class="text-xs text-apple-subtext font-medium uppercase tracking-wider mt-1" id="player-state">Готов к работе</p>
                </div>
                <div class="flex flex-col items-end">
                     <p class="text-xs text-apple-subtext font-medium uppercase tracking-wider mt-1" id="current-channel">Нет канала</p>
                </div>
            </div>

            <!-- Кольцо управления (D-Pad) -->
            <div class="dpad-container mt-4">
                <button class="dpad-btn dpad-up" onclick="sendCmd('up')"><i class="ph ph-caret-up"></i></button>
                <button class="dpad-btn dpad-left" onclick="sendCmd('left')"><i class="ph ph-caret-left"></i></button>
                <button class="dpad-btn dpad-ok btn-press font-semibold text-sm" onclick="sendCmd('ok')">OK</button>
                <button class="dpad-btn dpad-right" onclick="sendCmd('right')"><i class="ph ph-caret-right"></i></button>
                <button class="dpad-btn dpad-down" onclick="sendCmd('down')"><i class="ph ph-caret-down"></i></button>
            </div>

            <!-- Быстрые команды -->
            <div class="grid grid-cols-3 gap-4 w-full max-w-[260px]">
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="sendCmd('back')">
                    <i class="ph ph-arrow-u-up-left text-2xl"></i>
                </button>
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="sendCmd('home')">
                    <i class="ph ph-house text-2xl"></i>
                </button>
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="sendCmd('play')">
                    <i class="ph ph-play-pause text-2xl"></i>
                </button>
                
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="changeVolume(-5)">
                    <i class="ph ph-speaker-low text-xl"></i>
                    <i class="ph ph-minus text-sm"></i>
                </button>
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="sendCmd('menu')">
                    <i class="ph ph-list text-2xl text-apple-subtext"></i>
                </button>
                <button class="btn-press aspect-square bg-apple-surface rounded-2xl shadow-apple flex flex-col items-center justify-center gap-1 border border-apple-border/30" onclick="changeVolume(5)">
                    <i class="ph ph-speaker-high text-xl"></i>
                    <i class="ph ph-plus text-sm"></i>
                </button>
            </div>
        </div>

        <!-- Правая колонка: Цифры, Поиск и Каналы (Mobile: Bottom, Desktop: Right 7 cols) -->
        <div class="lg:col-span-7 flex flex-col gap-6 mt-6 lg:mt-0">
            
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <!-- Numpad (Ввод цифр) -->
                <div class="bg-apple-surface p-5 rounded-3xl shadow-apple border border-apple-border/30 h-fit">
                    <h2 class="text-sm font-semibold text-apple-subtext uppercase tracking-wider mb-4 px-1">Цифры</h2>
                    <div class="grid grid-cols-3 gap-3">
                        <!-- Цифры 1-9 -->
                        <script>
                            for(let i=1; i<=9; i++) {
                                document.write(`<button class="btn-press py-3 text-xl font-medium bg-apple-bg rounded-xl" onclick="sendCmd('${'$'}{i}')">${'$'}{i}</button>`);
                            }
                        </script>
                        <button class="btn-press py-3 text-xl font-medium bg-apple-bg rounded-xl" onclick="sendCmd('back')"><i class="ph ph-backspace m-auto"></i></button>
                        <button class="btn-press py-3 text-xl font-medium bg-apple-bg rounded-xl" onclick="sendCmd('0')">0</button>
                        <button class="btn-press py-3 text-xl font-medium bg-apple-bg rounded-xl" onclick="sendCmd('ok')"><i class="ph ph-arrow-bend-down-left m-auto"></i></button>
                    </div>
                </div>

                <!-- Правая колонка внутри грида: Поиск + Список каналов -->
                <div class="flex flex-col gap-6">
                    <!-- Умный поиск (Apple Intelligence Style) -->
                    <div class="ai-glow rounded-2xl bg-white">
                        <div class="relative flex items-center bg-apple-surface rounded-2xl shadow-apple border border-apple-border/30 overflow-hidden">
                            <i class="ph ph-sparkle text-xl ml-4 text-purple-500"></i>
                            <input type="text" id="searchInput" placeholder="Поиск каналов или шоу..." 
                                   class="w-full py-4 px-3 bg-transparent outline-none font-medium placeholder-apple-subtext"
                                   onkeyup="filterChannels()">
                            <button class="mr-4 text-apple-subtext hover:text-apple-text transition-colors" onclick="document.getElementById('searchInput').value=''; filterChannels();">
                                <i class="ph ph-x-circle text-xl"></i>
                            </button>
                        </div>
                    </div>

                    <!-- Список каналов -->
                    <div class="bg-apple-surface p-5 rounded-3xl shadow-apple border border-apple-border/30 flex flex-col h-[320px]">
                        <h2 class="text-sm font-semibold text-apple-subtext uppercase tracking-wider mb-4 px-1">Каналы</h2>
                        <div id="channelList" class="flex-1 overflow-y-auto pr-2 space-y-2">
                            <div class="text-center text-apple-subtext py-6 text-sm">Загрузка...</div>
                        </div>
                    </div>
                </div>
            </div>

        </div>
    </div>

    <!-- Логика приложения -->
    <script>
        const API_URL = window.location.origin;
        let allChannels = [];
        let currentVolume = 50;

        // Render Channels
        const channelList = document.getElementById('channelList');
        
        function renderChannels(list) {
            channelList.innerHTML = '';
            if(list.length === 0) {
                channelList.innerHTML = `<div class="text-center text-apple-subtext py-6 text-sm">Ничего не найдено</div>`;
                return;
            }
            list.forEach(ch => {
                const el = document.createElement('button');
                el.className = 'btn-press w-full flex items-center gap-4 p-2 rounded-2xl hover:bg-apple-bg transition-colors text-left';
                el.onclick = () => switchChannel(ch.id);
                
                // Logo or Placeholder
                const logoHtml = ch.logoUrl 
                    ? `<img src="${'$'}{ch.logoUrl}" class="w-8 h-8 object-contain" onerror="this.style.display='none'">`
                    : `<div class="w-8 h-8 rounded-lg bg-gray-200 flex items-center justify-center text-xs font-bold text-gray-500">${'$'}{ch.name.substring(0,2)}</div>`;

                el.innerHTML = `
                    <div class="w-12 h-12 rounded-xl flex items-center justify-center font-bold text-lg shadow-sm border border-apple-border/20 bg-white">
                        ${'$'}{logoHtml}
                    </div>
                    <div class="flex-1 overflow-hidden">
                        <div class="font-semibold text-apple-text text-sm truncate">${'$'}{ch.name}</div>
                        <div class="text-xs text-apple-subtext">CH ${'$'}{ch.id + 1}</div>
                    </div>
                    <i class="ph ph-caret-right text-apple-subtext mr-2"></i>
                `;
                channelList.appendChild(el);
            });
        }

        // Filter Channels
        function filterChannels() {
            const query = document.getElementById('searchInput').value.toLowerCase();
            const filtered = allChannels.filter(ch => 
                ch.name.toLowerCase().includes(query) || 
                (ch.id + 1).toString().includes(query)
            );
            renderChannels(filtered);
        }

        // Fetch Data
        async function loadChannels() {
            try {
                const response = await fetch(`${'$'}{API_URL}/api/channels`);
                if (response.ok) {
                    allChannels = await response.json();
                    renderChannels(allChannels);
                }
            } catch (e) {
                console.error('Error loading channels:', e);
                channelList.innerHTML = `<div class="text-center text-apple-subtext py-6 text-sm text-red-500">Ошибка загрузки</div>`;
            }
        }

        async function updateStatus() {
            try {
                const response = await fetch(`${'$'}{API_URL}/api/status`);
                if (response.ok) {
                    const status = await response.json();
                    const chName = status.currentChannel || 'Нет сигнала';
                    document.getElementById('current-channel').textContent = 
                        status.currentChannelNumber > 0 ? `${'$'}{status.currentChannelNumber}. ${'$'}{chName}` : chName;
                    
                    document.getElementById('player-state').textContent = 
                        status.isPlaying ? 'Воспроизведение' : 'Пауза';
                        
                    currentVolume = status.volume;
                }
            } catch (e) { }
        }

        // Actions
        function switchChannel(id) {
            fetch(`${'$'}{API_URL}/api/channel/${'$'}{id}`, { method: 'POST' })
                .then(() => {
                    showToast('Переключение...');
                    updateStatus();
                });
        }

        function sendCmd(cmd) {
            // Haptic
            if (navigator.vibrate) navigator.vibrate(40);
            
            showToast('Команда: ' + cmd);

            fetch(`${'$'}{API_URL}/api/command`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ command: cmd })
            }).then(() => updateStatus());
        }
        
        function changeVolume(delta) {
            let newVol = currentVolume + delta;
            if (newVol < 0) newVol = 0;
            if (newVol > 100) newVol = 100;
            currentVolume = newVol; // Optimistic update
            
            showToast(`Громкость: ${'$'}{newVol}%`);
            
            fetch(`${'$'}{API_URL}/api/command`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ command: 'setVolume', value: newVol })
            });
        }

        // Toast Notification
        let toastTimeout;
        function showToast(text) {
            const toast = document.getElementById('toast');
            const toastText = document.getElementById('toast-text');
            
            toastText.textContent = text;
            toast.classList.add('show');

            clearTimeout(toastTimeout);
            toastTimeout = setTimeout(() => {
                toast.classList.remove('show');
            }, 1500);
        }

        // Init
        loadChannels();
        updateStatus();
        setInterval(updateStatus, 3000);
    </script>
</body>
</html>
        """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}