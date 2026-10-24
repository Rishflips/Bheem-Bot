package app.bheem.downloader

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TestServer(private val content: ByteArray) : AutoCloseable {
    data class Seen(val path: String, val range: String?)

    val seen = CopyOnWriteArrayList<Seen>()
    var ignoreRange = false
    var redirectStartTo: String? = null
    var dropFirstResponseAfter: Int? = null
    var corruptFirstResponse = false
    var corruptAlways = false
    var failFirst: Int = 0
    var failWith: Int = 503
    var statusForAll: Int? = null
    var slowChunkMillis = 0L
    var advertisedLength: Long? = null

    private val served = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { handle(it) }
        start()
    }

    val base: String get() = "http://127.0.0.1:${server.address.port}"

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        val range = ex.requestHeaders.getFirst("Range")
        seen += Seen(path, range)
        try {
            val nth = served.incrementAndGet()
            statusForAll?.let {
                ex.sendResponseHeaders(it, -1)
                return
            }
            if (path == "/start" && redirectStartTo != null) {
                ex.responseHeaders.add("Location", base + redirectStartTo)
                ex.sendResponseHeaders(302, -1)
                return
            }
            if (nth <= failFirst) {
                ex.sendResponseHeaders(failWith, -1)
                return
            }
            val from = if (!ignoreRange && range != null) range.removePrefix("bytes=").removeSuffix("-").toLong() else 0L
            if (from >= content.size) {
                ex.responseHeaders.add("Content-Range", "bytes */${content.size}")
                ex.sendResponseHeaders(416, -1)
                return
            }
            val body = content.copyOfRange(from.toInt(), content.size)
            val sent = if ((corruptFirstResponse && nth == 1) || corruptAlways) {
                body.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            } else {
                body
            }
            val declared = advertisedLength ?: sent.size.toLong()
            if (from > 0) {
                ex.responseHeaders.add("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
                ex.sendResponseHeaders(206, declared)
            } else {
                ex.sendResponseHeaders(200, declared)
            }
            val limit = dropFirstResponseAfter?.takeIf { nth == 1 }
            ex.responseBody.use { out ->
                var offset = 0
                while (offset < sent.size) {
                    val n = minOf(8192, sent.size - offset)
                    if (limit != null && offset + n > limit) {
                        out.write(sent, offset, limit - offset)
                        out.flush()
                        ex.close()
                        return
                    }
                    out.write(sent, offset, n)
                    offset += n
                    if (slowChunkMillis > 0) {
                        out.flush()
                        Thread.sleep(slowChunkMillis)
                    }
                }
            }
        } catch (_: IOException) {
        } finally {
            ex.close()
        }
    }

    override fun close() = server.stop(0)
}
