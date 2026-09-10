package dev.factweek.ingestion.internal

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class GdeltDocClientTest {
    @Test
    fun `sends the GDELT DOC time window and article-list parameters`() {
        val requestedQuery = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/v2/doc/doc") { exchange ->
            requestedQuery.set(exchange.requestURI.rawQuery)
            val body = """{"articles":[{"title":"Candidate","url":"https://example.org/article"}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val client = GdeltDocClient(
                RestClient.builder(),
                "http://localhost:${server.address.port}",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
            )
            val from = Instant.parse("2026-09-01T01:02:03Z")
            val to = Instant.parse("2026-09-02T04:05:06Z")

            val candidates = client.findTechnologyCandidates("quantum computing", from, to, 42)

            assertEquals(1, candidates.size)
            assertEquals(
                mapOf(
                    "query" to "quantum computing",
                    "mode" to "artlist",
                    "format" to "json",
                    "sort" to "datedesc",
                    "maxrecords" to "42",
                    "startdatetime" to "20260901010203",
                    "enddatetime" to "20260902040506",
                ),
                parseQuery(requestedQuery.get()),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `keeps only articles with a usable HTTP URL and title`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/v2/doc/doc") { exchange ->
            val body = """{"articles":[
                {"title":" Valid candidate ","url":"https://example.org/article"},
                {"title":" ","url":"https://example.org/blank-title"},
                {"title":"Relative","url":"/relative"},
                {"title":"No host","url":"https:/missing-host"},
                {"title":"Unsupported scheme","url":"ftp://example.org/file"},
                {"title":"Malformed URL","url":"http://[bad"}
            ]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val client = GdeltDocClient(
                RestClient.builder(),
                "http://localhost:${server.address.port}",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
            )

            val candidates = client.findTechnologyCandidates(
                "technology",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                25,
            )

            assertEquals(1, candidates.size)
            assertEquals("Valid candidate", candidates.single().title)
            assertEquals("https://example.org/article", candidates.single().url.toString())
        } finally {
            server.stop(0)
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split("&")
        .associate {
            val (key, value) = it.split("=", limit = 2)
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
}
