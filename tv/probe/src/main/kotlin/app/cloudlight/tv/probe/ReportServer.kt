package app.cloudlight.tv.probe

import java.net.ServerSocket
import java.net.SocketException

/**
 * Serves the current report as plain text on the local network, so it can be copied from a PC
 * browser at http://<box address>:PORT without adb or a USB stick.
 */
class ReportServer(private val content: () -> String) {
    private var socket: ServerSocket? = null

    fun start() {
        val server = try {
            ServerSocket(PORT)
        } catch (t: Throwable) {
            return
        }
        socket = server
        Thread({
            while (!server.isClosed) {
                try {
                    server.accept().use { client ->
                        client.soTimeout = 2000
                        // Read the request line and headers; any path returns the report.
                        val reader = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = content().toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray(Charsets.US_ASCII))
                            write(body)
                            flush()
                        }
                    }
                } catch (e: SocketException) {
                    if (server.isClosed) break
                } catch (t: Throwable) {
                    // Keep serving after a bad request.
                }
            }
        }, "report-server").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        try {
            socket?.close()
        } catch (t: Throwable) {
        }
        socket = null
    }

    companion object {
        const val PORT = 8686
    }
}
