package dev.pointandshoot

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Sprint **IP.2** — LAN HTTP index + read-only file pull for desktop workflows.
 *
 *   GET  /status   → JSON (port, file count, collaborative client count)
 *   GET  /files    → JSON array of recent P&S DCIM items
 *   GET  /file?id= → raw bytes (Content-Type from MediaStore)
 *
 * Default port **28766** (tether control stays on **28765** loopback).
 */
class LanMediaTransferServer(
    private val context: Context,
    private val preferredPort: Int = DEFAULT_PORT,
) {
    @Volatile
    var boundPort: Int = preferredPort
        private set
    data class FileEntry(
        val id: Long,
        val uri: Uri,
        val name: String,
        val mime: String?,
        val size: Long,
    )

    @Volatile
    var fileProvider: suspend () -> List<FileEntry> = { emptyList() }

    private val running = AtomicBoolean(false)
    private val activeClients = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    fun isListening(): Boolean {
        val ss = serverSocket
        return running.get() && ss != null && !ss.isClosed
    }

    fun start() {
        synchronized(StartLock) {
            if (isListening()) return
            stopUnlocked()
            if (!running.compareAndSet(false, true)) return
            worker =
            thread(name = "PNS.LanTransfer", isDaemon = true) {
                try {
                    val ss = ServerSocket()
                    var bound = false
                    for (candidate in portCandidates(preferredPort)) {
                        runCatching {
                            ss.reuseAddress = true
                            ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), candidate), 8)
                            boundPort = candidate
                            bound = true
                        }.onFailure {
                            Log.w(TAG, "bind port=$candidate failed: ${it.message}")
                        }
                        if (bound) break
                    }
                    if (!bound) {
                        runCatching {
                            ss.reuseAddress = true
                            ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 8)
                            boundPort = ss.localPort
                            bound = true
                        }.onFailure {
                            Log.w(TAG, "bind ephemeral failed: ${it.message}")
                        }
                    }
                    if (!bound) {
                        Log.w(TAG, "stopped: no port available")
                        running.set(false)
                        return@thread
                    }
                    serverSocket = ss
                    Log.i(TAG, "listening port=$boundPort")
                    PnsAdbLog.i(context, "connectivity lanServer listening port=$boundPort")
                    while (running.get()) {
                        val client = runCatching { ss.accept() }.getOrNull() ?: break
                        activeClients.incrementAndGet()
                        CollaborativeCapture.onClientConnected(activeClients.get())
                        runCatching { handleClient(client) }
                            .onFailure { e -> Log.w(TAG, "client: ${e.message}") }
                        runCatching { client.close() }
                        val left = activeClients.decrementAndGet()
                        CollaborativeCapture.onClientDisconnected(left)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stopped: ${e.message}")
                } finally {
                    runCatching { serverSocket?.close() }
                    serverSocket = null
                    running.set(false)
                }
            }
        }
    }

    fun stop() {
        synchronized(StartLock) {
            stopUnlocked()
        }
    }

    private fun stopUnlocked() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.join(2_000)
        worker = null
        Log.i(TAG, "stopped")
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 8_000
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
                val files = kotlinx.coroutines.runBlocking { fileProvider() }
                val body =
                    """{"ok":true,"port":$boundPort,"files":${files.size},"clients":${activeClients.get()},"collaborative":${CollaborativeCapture.enabled}}"""
                writeHttp(writer, 200, body, "application/json")
            }
            method == "GET" && path == "/files" -> {
                val files = kotlinx.coroutines.runBlocking { fileProvider() }
                val items =
                    files.joinToString(",") { f ->
                        """{"id":${f.id},"name":"${escapeJson(f.name)}","size":${f.size},"mime":"${f.mime ?: ""}"}"""
                    }
                writeHttp(writer, 200, """{"ok":true,"items":[$items]}""", "application/json")
            }
            method == "GET" && path == "/file" -> {
                val id = query["id"]?.toLongOrNull()
                val files = kotlinx.coroutines.runBlocking { fileProvider() }
                val entry = files.firstOrNull { it.id == id }
                if (entry == null) {
                    writeHttp(writer, 404, """{"ok":false}""", "application/json")
                    return
                }
                streamFile(socket, entry, writer)
            }
            else -> writeHttp(writer, 404, """{"ok":false}""", "application/json")
        }
    }

    private fun streamFile(socket: Socket, entry: FileEntry, writer: PrintWriter) {
        val mime = entry.mime ?: "application/octet-stream"
        val bytes =
            context.contentResolver.openInputStream(entry.uri)?.use { it.readBytes() }
                ?: return writeHttp(writer, 500, """{"ok":false}""", "application/json")
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        out.write("Content-Type: $mime\r\n".toByteArray())
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray())
        out.write("Connection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
        Log.i(TAG, "GET /file id=${entry.id} bytes=${bytes.size}")
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

    private fun writeHttp(writer: PrintWriter, code: Int, body: String, contentType: String) {
        val status = if (code == 200) "OK" else "Not Found"
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun portCandidates(primary: Int): IntArray =
        intArrayOf(primary, 28767, 28768, 28769, 38866, 48866)

    companion object {
        const val TAG = "PNS.LanTransfer"
        const val DEFAULT_PORT = 28766
        private val StartLock = Any()
    }
}
