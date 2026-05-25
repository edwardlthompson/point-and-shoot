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
 * Sprint **CC.3** — loopback HTTP control for desktop tethering (`adb reverse tcp:28765 tcp:28765`).
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
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker =
            thread(name = "PNS.Tether", isDaemon = true) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(
                        InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
                        4,
                    )
                    serverSocket = ss
                    Log.i(TAG, "listening port=$port")
                    while (running.get()) {
                        val client = runCatching { ss.accept() }.getOrNull() ?: break
                        runCatching { handleClient(client) }
                            .onFailure { e -> Log.w(TAG, "client error: ${e.message}") }
                        runCatching { client.close() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "server stopped: ${e.message}")
                } finally {
                    runCatching { serverSocket?.close() }
                    serverSocket = null
                    running.set(false)
                }
            }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        worker?.join(500)
        worker = null
        Log.i(TAG, "stopped")
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
    }
}
