package app.bheem.downloader

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

interface HttpReply : AutoCloseable {
    val status: Int
    val body: InputStream

    fun header(name: String): String?
}

interface HttpTransport {
    fun open(url: String, from: Long): HttpReply
}

class UrlTransport(
    private val allowHttp: Boolean = false,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 15_000,
    private val trace: (String) -> Unit = {},
) : HttpTransport {
    override fun open(url: String, from: Long): HttpReply {
        var current = URI(url).toURL()
        if (!schemeAllowed(current.protocol, allowHttp)) throw IOException("downloads must use https")
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
            val status = connection.responseCode
            trace("${current.host}${current.path} range=${if (from > 0) from.toString() else "none"} -> $status")
            if (status in REDIRECTS) {
                val location = connection.getHeaderField("Location") ?: throw IOException("a redirect without a Location header")
                connection.disconnect()
                val next = URL(current, location)
                if (!redirectAllowed(current.toString(), next.toString(), allowHttp)) throw IOException("a redirect to a non-https address was refused")
                current = next
                return@repeat
            }
            return Reply(connection, status)
        }
        throw IOException("too many redirects")
    }

    private class Reply(private val connection: HttpURLConnection, override val status: Int) : HttpReply {
        override val body: InputStream by lazy {
            if (status >= 400) connection.errorStream ?: ByteArrayInputStream(ByteArray(0)) else connection.inputStream
        }

        override fun header(name: String): String? = connection.getHeaderField(name)

        override fun close() = connection.disconnect()
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        private fun schemeAllowed(scheme: String, allowHttp: Boolean) = scheme == "https" || (allowHttp && scheme == "http")

        fun redirectAllowed(from: String, to: String, allowHttp: Boolean): Boolean {
            val fromScheme = from.substringBefore("://")
            val toScheme = to.substringBefore("://")
            return toScheme == "https" || (allowHttp && toScheme == "http" && fromScheme == "http")
        }
    }
}
