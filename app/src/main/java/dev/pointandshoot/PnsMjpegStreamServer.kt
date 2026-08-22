@file:Suppress("MagicNumber", "MaxLineLength", "LoopWithTooManyJumpStatements")

package dev.pointandshoot

import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Multipart MJPEG over HTTP — OBS / VLC / browser "UVC-equivalent" webcam.
 * Frames come from [PnsExternalOutput.latestJpeg], never a second camera session.
 */
object PnsMjpegStreamServer {
    const val TAG: String = "PNS.Mjpeg"
    const val DEFAULT_PORT: Int = PnsExternalOutput.MJPEG_PORT
    private const val BOUNDARY: String = "pnsframe"
    private const val MAX_CLIENTS: Int = 3

    @Volatile
    var boundPort: Int = DEFAULT_PORT
        private set

    private val running = AtomicBoolean(false)
    private val clients = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    fun isListening(): Boolean {
        val ss = serverSocket
        return running.get() && ss != null && !ss.isClosed
    }

    fun start(context: Context) {
        if (isListening()) return
        if (!PnsProductPrefs.mjpegWebcamEnabled(context)) return
        if (!running.compareAndSet(false, true)) return
        worker =
            thread(name = "PNS.Mjpeg", isDaemon = true) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DEFAULT_PORT), 4)
                    boundPort = ss.localPort
                    serverSocket = ss
                    Log.i(TAG, "listening port=$boundPort")
                    while (running.get()) {
                        val client = runCatching { ss.accept() }.getOrNull() ?: break
                        if (clients.get() >= MAX_CLIENTS) {
                            runCatching { client.close() }
                            continue
                        }
                        thread(name = "PNS.MjpegClient", isDaemon = true) { handle(client) }
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

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.join(1_500)
        worker = null
        Log.i(TAG, "stopped")
    }

    private fun handle(socket: Socket) {
        clients.incrementAndGet()
        try {
            socket.soTimeout = 0
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = request.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
            val out = socket.getOutputStream()
            when (path) {
                PnsRemoteProtocol.HTTP_SNAPSHOT_PATH, "/snapshot.jpg" -> writeSnapshot(out)
                "/webcam/status", "/webcam/control" -> writeWebcam(out, request)
                "/h264", "/webcam/h264" -> writeH264(out)
                else -> writeMjpeg(out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "client: ${e.message}")
        } finally {
            runCatching { socket.close() }
            clients.decrementAndGet()
        }
    }

    private fun writeSnapshot(out: OutputStream) {
        val jpeg = PnsExternalOutput.latestJpeg
        if (jpeg == null) {
            val body = """{"ok":false,"err":"no frame"}"""
            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
            out.flush()
            return
        }
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        out.write("Content-Type: image/jpeg\r\n".toByteArray())
        out.write("Content-Length: ${jpeg.size}\r\n".toByteArray())
        out.write("Cache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(jpeg)
        out.flush()
    }

    private fun writeMjpeg(out: OutputStream) {
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        out.write("Cache-Control: no-store\r\n".toByteArray())
        out.write("Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n\r\n".toByteArray())
        out.flush()
        var idle = 0
        while (running.get() && idle < 400) {
            val jpeg = PnsExternalOutput.latestJpeg
            if (jpeg == null) {
                Thread.sleep(50)
                idle++
                continue
            }
            idle = 0
            out.write("--$BOUNDARY\r\n".toByteArray())
            out.write("Content-Type: image/jpeg\r\n".toByteArray())
            out.write("Content-Length: ${jpeg.size}\r\n\r\n".toByteArray())
            out.write(jpeg)
            out.write("\r\n".toByteArray())
            out.flush()
            val pace = if (PnsUsbWebcam.active) 16L else 66L
            Thread.sleep(pace)
        }
    }

    private fun writeH264(out: OutputStream) {
        if (!PnsWebcamEncoder.isRunning) {
            val body = """{"ok":false,"err":"encoder off"}"""
            out.write(
                "HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    .toByteArray(),
            )
            out.flush()
            return
        }
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        out.write("Content-Type: video/H264\r\n".toByteArray())
        out.write("Cache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
        PnsWebcamEncoder.writeAnnexB(out)
    }

    private fun writeWebcam(out: OutputStream, request: String) {
        val q =
            request.substringAfter('?', "").substringBefore(' ')
                .split('&')
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
                }
                .toMap()
        val body =
            if (q.isEmpty()) {
                PnsWebcamControls.statusJson()
            } else {
                PnsWebcamControls.applyQuery(q)
            }
        out.write("HTTP/1.1 200 OK\r\n".toByteArray())
        out.write("Content-Type: application/json\r\n".toByteArray())
        out.write("Cache-Control: no-store\r\n".toByteArray())
        out.write("Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body.toByteArray())
        out.flush()
    }
}
