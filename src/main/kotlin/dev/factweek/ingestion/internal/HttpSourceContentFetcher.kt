package dev.factweek.ingestion.internal

import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.GZIPInputStream

@Component
internal class HttpSourceContentFetcher(
    private val urlValidator: SourceUrlValidator,
    @Value("\${factweek.source-content.connect-timeout:5s}") connectTimeout: Duration,
    @Value("\${factweek.source-content.request-timeout:15s}") private val requestTimeout: Duration,
    @Value("\${factweek.source-content.maximum-download-size:2097152}") private val maximumDownloadSize: Int,
    @Value("\${factweek.source-content.maximum-stored-text-length:100000}") private val maximumStoredTextLength: Int,
    @Value("\${factweek.source-content.maximum-redirects:5}") private val maximumRedirects: Int,
    @Value("\${factweek.source-content.user-agent:Factweek/0.1 (+https://github.com/factweek/factweek)}") private val userAgent: String,
) : SourceContentFetcher {
    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun fetch(sourceUrl: URI): SourceContentFetchResult {
        var currentUrl = sourceUrl
        repeat(maximumRedirects + 1) { redirectCount ->
            urlValidator.validate(currentUrl)
            val response = send(currentUrl)
            if (response.statusCode() in REDIRECTS) {
                response.body().close()
                if (redirectCount == maximumRedirects) throw SourceContentFetchException(SourceContentFailureReason.TOO_MANY_REDIRECTS)
                val location = response.headers().firstValue("Location").orElseThrow {
                    SourceContentFetchException(SourceContentFailureReason.HTTP_ERROR)
                }
                currentUrl = currentUrl.resolve(location)
                return@repeat
            }
            if (response.statusCode() !in 200..299) throw SourceContentFetchException(SourceContentFailureReason.HTTP_ERROR)

            val mediaType = response.headers().firstValue("Content-Type").orElse("")
                .substringBefore(';').trim().lowercase()
            if (mediaType !in setOf("text/html", "application/xhtml+xml")) {
                throw SourceContentFetchException(SourceContentFailureReason.UNSUPPORTED_MEDIA_TYPE)
            }
            val contentLength = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
            if (contentLength != null && contentLength > maximumDownloadSize) {
                throw SourceContentFetchException(SourceContentFailureReason.CONTENT_TOO_LARGE)
            }
            val text = extractText(readBody(response))
            if (text.length < MINIMUM_USABLE_TEXT_LENGTH) throw SourceContentFetchException(SourceContentFailureReason.EMPTY_CONTENT)
            val storedText = text.take(maximumStoredTextLength)
            return SourceContentFetchResult(currentUrl, mediaType, response.statusCode(), storedText, sha256(storedText))
        }
        throw SourceContentFetchException(SourceContentFailureReason.TOO_MANY_REDIRECTS)
    }

    private fun send(url: URI): HttpResponse<InputStream> = try {
        client.send(
            HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html, application/xhtml+xml")
                .header("Accept-Encoding", "identity")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
    } catch (exception: java.net.http.HttpTimeoutException) {
        throw SourceContentFetchException(SourceContentFailureReason.TIMEOUT)
    } catch (exception: java.net.http.HttpConnectTimeoutException) {
        throw SourceContentFetchException(SourceContentFailureReason.TIMEOUT)
    } catch (exception: java.io.IOException) {
        throw SourceContentFetchException(SourceContentFailureReason.NETWORK_ERROR)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw SourceContentFetchException(SourceContentFailureReason.NETWORK_ERROR)
    }

    private fun readBody(response: HttpResponse<InputStream>): String {
        val input = response.body().let {
            if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)) GZIPInputStream(it) else it
        }
        val bytes = input.use { readLimited(it) }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > maximumDownloadSize) throw SourceContentFetchException(SourceContentFailureReason.CONTENT_TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun extractText(html: String): String {
        val document = Jsoup.parse(html)
        document.select("script, style, noscript, nav, header, footer, aside, form").remove()
        val candidates = listOfNotNull(document.selectFirst("article"), document.selectFirst("main"), document.body())
        return candidates.asSequence()
            .map { normalize(it.text()) }
            .firstOrNull { it.length >= MINIMUM_USABLE_TEXT_LENGTH }
            ?: ""
    }

    private fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val MINIMUM_USABLE_TEXT_LENGTH = 40
    }
}
