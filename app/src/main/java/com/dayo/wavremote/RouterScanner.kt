package com.dayo.wavremote

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class ProbeState { WAVLINK, OTHER_ROUTER, UNREACHABLE }

data class ProbeResult(
    val host: String,
    val state: ProbeState,
    val title: String? = null,
    val server: String? = null,
    val error: String? = null,
) {
    val url: String get() = "http://$host/"
}

object RouterScanner {
    val defaultHosts = listOf(
        "192.168.10.1", "192.168.20.1", "wavlogin.link", "192.168.0.1", "192.168.1.1",
    )

    suspend fun scan(network: Network?, gateway: String?, extraHost: String?): List<ProbeResult> {
        val hosts = buildList {
            extraHost?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            gateway?.let(::add)
            addAll(defaultHosts)
        }.distinct()

        val results = coroutineScope {
            hosts.map { host -> async(Dispatchers.IO) { probe(network, host) } }.awaitAll()
        }
        return results.sortedBy { it.state.ordinal }
    }

    private suspend fun probe(network: Network?, host: String): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host/")
            val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = true
            try {
                conn.responseCode // forces the request
                val server = conn.getHeaderField("Server")
                val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
                val html = stream?.use { s ->
                    val buf = s.readNBytesCompat(200_000)
                    String(buf, Charsets.UTF_8)
                } ?: ""
                val looksWavlink = html.contains("wavlink", ignoreCase = true) ||
                    (server?.contains("wavlink", ignoreCase = true) == true)
                ProbeResult(
                    host = host,
                    state = if (looksWavlink) ProbeState.WAVLINK else ProbeState.OTHER_ROUTER,
                    title = titleOf(html),
                    server = server,
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            ProbeResult(host, ProbeState.UNREACHABLE, error = "Timed out")
        } catch (e: UnknownHostException) {
            ProbeResult(host, ProbeState.UNREACHABLE, error = "Name not found")
        } catch (e: Exception) {
            ProbeResult(host, ProbeState.UNREACHABLE, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun titleOf(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = read(buf, 0, minOf(buf.size, max - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
