package dev.pointandshoot

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Sprint **CC.3** / **15.37** — HTTP control for desktop tethering.
 *
 * Loopback (**127.0.0.1**) for `adb reverse tcp:28765 tcp:28765`.
 * Optional LAN bind (**0.0.0.0**) when [start] is called with `lanBind=true` (Wi‑Fi / Wi‑Fi Direct peers).
 *
 *   GET  /status  → JSON snapshot
 *   POST /capture → fire still callback
 *   POST /flash?mode=auto|torch|off → set preview flash (rear only)
 */
class TetheredCaptureServer(
    private val port: Int = DEFAULT_PORT,
) {
    data class StatusSnapshot(
        val canCaptureStill: Boolean,
        val primaryPhoto: Boolean,
        val cameraId: String?,
        val fps: Int,
        val flashMode: String,
    )

    @Volatile
    var onCapture: (() -> Unit)? = null

    @Volatile
    var statusProvider: (() -> StatusSnapshot)? = null

    @Volatile
    var onFlashMode: ((PreviewFlashMode) -> Unit)? = null

    private val running = AtomicBoolean(false)
    @Volatile
    var lanBound: Boolean = false
        private set
    private var loopbackSocket: ServerSocket? = null
    private var lanSocket: ServerSocket? = null
    private var loopbackWorker: Thread? = null
    private var lanWorker: Thread? = null

    fun start(lanBind: Boolean = false) {
        synchronized(StartLock) {
            if (running.get()) {
                if (lanBind && !lanBound) {
                    startLanListener()
                }
                return
            }
            if (!running.compareAndSet(false, true)) return
            lanBound = false
            loopbackWorker =
                thread(name = "PNS.TetherLoop", isDaemon = true) {
                    acceptLoop(bindHost = LOOPBACK_HOST, onBound = { loopbackSocket = it })
                }
            if (lanBind) {
                startLanListener()
            }
        }
    }

    private fun startLanListener() {
        if (lanBound) return
        lanWorker =
            thread(name = "PNS.TetherLan", isDaemon = true) {
                acceptLoop(bindHost = LAN_HOST, onBound = { ss ->
                    lanSocket = ss
                    lanBound = true
                    Log.i(TAG, "wifiDirectBound=true port=$port")
                })
            }
    }

    fun stop() {
        synchronized(StartLock) {
            running.set(false)
            runCatching { loopbackSocket?.close() }
            runCatching { lanSocket?.close() }
            loopbackSocket = null
            lanSocket = null
            lanBound = false
            loopbackWorker?.join(500)
            lanWorker?.join(500)
            loopbackWorker = null
            lanWorker = null
            Log.i(TAG, "stopped")
        }
    }

    private fun acceptLoop(bindHost: String, onBound: (ServerSocket) -> Unit) {
        var ss: ServerSocket? = null
        try {
            ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName(bindHost), port), 8)
            onBound(ss)
            Log.i(TAG, "listening host=$bindHost port=$port")
            while (running.get()) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching { handleClient(client) }
                    .onFailure { e -> Log.w(TAG, "client error: ${e.message}") }
                runCatching { client.close() }
            }
        } catch (e: Exception) {
            if (bindHost == LAN_HOST) {
                Log.w(TAG, "lan bind failed: ${e.message}")
            } else {
                Log.w(TAG, "loopback bind failed: ${e.message}")
            }
        } finally {
            runCatching { ss?.close() }
            if (bindHost == LAN_HOST) {
                lanBound = false
                lanSocket = null
            } else {
                loopbackSocket = null
            }
            if (bindHost == LOOPBACK_HOST) {
                running.set(false)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val pathAndQuery = parts[1]
        val path = pathAndQuery.substringBefore('?')
        val query = parseQuery(pathAndQuery.substringAfter('?', ""))
        drainHeaders(reader)

        val writer = PrintWriter(socket.getOutputStream(), true)
        when {
            method == "GET" && path == "/status" -> {
                val snap = statusProvider?.invoke()
                val body =
                    if (snap == null) {
                        """{"ok":false}"""
                    } else {
                        """{"ok":true,"canCaptureStill":${snap.canCaptureStill},"primaryPhoto":${snap.primaryPhoto},"cameraId":"${snap.cameraId ?: ""}","fps":${snap.fps},"flashMode":"${snap.flashMode}"}"""
                    }
                writeHttp(writer, 200, body)
                Log.i(TAG, "GET /status")
            }
            method == "POST" && path == "/capture" -> {
                onCapture?.invoke()
                writeHttp(writer, 200, """{"ok":true,"action":"capture"}""")
                Log.i(TAG, "POST /capture")
                Log.i(TAG, "tether capture fired")
            }
            method == "POST" && path == "/flash" -> {
                val mode =
                    when (query["mode"]?.lowercase()) {
                        "off" -> PreviewFlashMode.Off
                        "on" -> PreviewFlashMode.On
                        "torch" -> PreviewFlashMode.Torch
                        else -> PreviewFlashMode.Auto
                    }
                onFlashMode?.invoke(mode)
                writeHttp(writer, 200, """{"ok":true,"flashMode":"${mode.name}"}""")
                Log.i(TAG, "POST /flash mode=${mode.name}")
            }
            else -> writeHttp(writer, 404, """{"ok":false,"error":"not_found"}""")
        }
    }

    private fun drainHeaders(reader: BufferedReader) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
        }.toMap()
    }

    private fun writeHttp(writer: PrintWriter, code: Int, body: String) {
        val status = if (code == 200) "OK" else "Not Found"
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: application/json\r\n")
        writer.print("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    companion object {
        const val TAG = "PNS.Tether"
        /** Avoid **18765** — `adb reverse tcp:18765` binds that port on-device and breaks bind. */
        const val DEFAULT_PORT = 28765
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val LAN_HOST = "0.0.0.0"
        private val StartLock = Any()
    }
}
