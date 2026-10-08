package dev.diego.expanda.gitsync

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlConnectionGet(private val timeoutMs: Int = 15_000) : HttpGet {
    override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GitSyncException("Could not open a connection to the git host.", cause = e)
        } catch (e: RuntimeException) {
            throw GitSyncException("Could not open a connection to the git host.", cause = e)
        }
        try {
            connection.requestMethod = "GET"
            // Never follow redirects: the Authorization header must only ever go to the original host.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val status = connection.responseCode
            // 304 is the expected answer to a conditional request; any other 3xx is a redirect we refuse.
            if (status in 300..399 && status != 304) throw httpError("Git host", status, null, true, "access is forbidden")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it, maxBytes) }.orEmpty()
            return HttpResult(status, body, rateLimitRemaining(connection), connection.getHeaderField("ETag"))
        } catch (e: IOException) {
            // Message intentionally generic: exception text could echo request details.
            throw GitSyncException("Network error: ${e.javaClass.simpleName}. Check your connection.", cause = e)
        } catch (e: RuntimeException) {
            // Same rule: bad header values, platform bugs etc. must not leak their text into the UI or prefs.
            throw GitSyncException("Unexpected connection error (${e.javaClass.simpleName}).", cause = e)
        } finally {
            connection.disconnect()
        }
    }

    /** `x-ratelimit-remaining` (GitHub), or the `r=` part of the standard `RateLimit` header (Forgejo). */
    private fun rateLimitRemaining(connection: HttpURLConnection): String? =
        connection.getHeaderField("x-ratelimit-remaining")
            ?: connection.getHeaderField("ratelimit")?.let { Regex("""\br=(\d+)""").find(it)?.groupValues?.get(1) }

    internal companion object {
        /** Reads at most [maxBytes]; a longer body is an error rather than a truncated result. */
        fun readCapped(stream: InputStream, maxBytes: Int): String {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (out.size() + n > maxBytes) {
                    throw GitSyncException("Response is larger than the ${maxBytes / 1024} KiB limit.")
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
