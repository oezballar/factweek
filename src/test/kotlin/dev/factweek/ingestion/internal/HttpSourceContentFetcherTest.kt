package dev.factweek.ingestion.internal

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration

class HttpSourceContentFetcherTest {
    @Test
    fun `extracts normalized article text and checksum`() = withServer { server ->
        server.createContext("/article") { exchange ->
            html(exchange, """<html><body><nav>navigation</nav><article>  Useful   article text with enough content for extraction. <script>ignored()</script></article><footer>footer</footer></body></html>""")
        }
        server.start()
        val result = fetcher().fetch(url(server, "/article"))
        assertEquals("Useful article text with enough content for extraction.", result.extractedText)
        assertEquals("41339ed3f1f5a70c30c1943ddb70802794821bd8b1919ac1341e0dcc5171e033", result.contentSha256)
    }

    @Test
    fun `uses main then body when article is not useful`() = withServer { server ->
        server.createContext("/main") { exchange -> html(exchange, "<html><body><article>short</article><main>Main content with enough meaningful words to be selected.</main></body></html>") }
        server.createContext("/body") { exchange -> html(exchange, "<html><body>Body fallback contains enough meaningful text for extraction.</body></html>") }
        server.start()
        assertEquals("Main content with enough meaningful words to be selected.", fetcher().fetch(url(server, "/main")).extractedText)
        assertEquals("Body fallback contains enough meaningful text for extraction.", fetcher().fetch(url(server, "/body")).extractedText)
    }

    @Test
    fun `classifies unsupported large empty and timeout responses`() = withServer { server ->
        server.createContext("/json") { exchange -> respond(exchange, 200, "application/json", "{}") }
        server.createContext("/large") { exchange -> respond(exchange, 200, "text/html", "x".repeat(200)) }
        server.createContext("/empty") { exchange -> html(exchange, "<html><body><main>tiny</main></body></html>") }
        server.createContext("/slow") { exchange -> Thread.sleep(300); html(exchange, "<html><body><main>This response is deliberately slow but otherwise useful.</main></body></html>") }
        server.start()
        assertReason(SourceContentFailureReason.UNSUPPORTED_MEDIA_TYPE) { fetcher().fetch(url(server, "/json")) }
        assertReason(SourceContentFailureReason.CONTENT_TOO_LARGE) { fetcher(maximumDownloadSize = 100).fetch(url(server, "/large")) }
        assertReason(SourceContentFailureReason.EMPTY_CONTENT) { fetcher().fetch(url(server, "/empty")) }
        assertReason(SourceContentFailureReason.TIMEOUT) { fetcher(requestTimeout = Duration.ofMillis(50)).fetch(url(server, "/slow")) }
    }

    @Test
    fun `follows relative redirects and rejects unsafe redirect targets`() = withServer { server ->
        server.createContext("/start") { exchange -> exchange.responseHeaders.add("Location", "/target"); exchange.sendResponseHeaders(302, -1); exchange.close() }
        server.createContext("/target") { exchange -> html(exchange, "<html><body><main>Redirect target with enough useful source content.</main></body></html>") }
        server.createContext("/unsafe") { exchange -> exchange.responseHeaders.add("Location", "/blocked"); exchange.sendResponseHeaders(302, -1); exchange.close() }
        server.start()
        assertEquals("Redirect target with enough useful source content.", fetcher().fetch(url(server, "/start")).extractedText)
        assertReason(SourceContentFailureReason.UNSAFE_URL) {
            fetcher(validator = RejectingValidator("/blocked")).fetch(url(server, "/unsafe"))
        }
    }

    @Test
    fun `limits redirect chains and rejects non public addresses`() = withServer { server ->
        server.createContext("/one") { exchange -> exchange.responseHeaders.add("Location", "/two"); exchange.sendResponseHeaders(302, -1); exchange.close() }
        server.createContext("/two") { exchange -> exchange.responseHeaders.add("Location", "/one"); exchange.sendResponseHeaders(302, -1); exchange.close() }
        server.start()
        assertReason(SourceContentFailureReason.TOO_MANY_REDIRECTS) { fetcher(maximumRedirects = 1).fetch(url(server, "/one")) }
        val validator = PublicSourceUrlValidator()
        listOf("http://localhost", "http://127.0.0.1", "http://10.0.0.1", "http://192.168.1.1", "http://[::1]", "http://[fc00::1]").forEach {
            assertReason(SourceContentFailureReason.UNSAFE_URL) { validator.validate(URI(it)) }
        }
    }

    private fun fetcher(
        validator: SourceUrlValidator = AllowLocalValidator,
        maximumDownloadSize: Int = 2 * 1024 * 1024,
        requestTimeout: Duration = Duration.ofSeconds(2),
        maximumRedirects: Int = 5,
    ) = HttpSourceContentFetcher(validator, Duration.ofSeconds(1), requestTimeout, maximumDownloadSize, 100_000, maximumRedirects, "Factweek-test")

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    private fun url(server: HttpServer, path: String): URI {
        return URI("http://localhost:${server.address.port}$path")
    }

    private fun html(exchange: com.sun.net.httpserver.HttpExchange, body: String) = respond(exchange, 200, "text/html; charset=utf-8", body)

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, type: String, body: String) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }

    private fun assertReason(reason: SourceContentFailureReason, action: () -> Unit) {
        assertEquals(reason, assertThrows<SourceContentFetchException>(action).reason)
    }

    private object AllowLocalValidator : SourceUrlValidator { override fun validate(url: URI) = Unit }
    private class RejectingValidator(private val path: String) : SourceUrlValidator {
        override fun validate(url: URI) { if (url.path == path) throw SourceContentFetchException(SourceContentFailureReason.UNSAFE_URL) }
    }
}
