package dev.factweek.ingestion.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.GdeltCandidates
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClient
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

@Component
internal class GdeltDocClient(
    builder: RestClient.Builder,
    @Value("\${factweek.gdelt.base-url:https://api.gdeltproject.org}") baseUrl: String,
    @Value("\${factweek.gdelt.connect-timeout:5s}") connectTimeout: Duration,
    @Value("\${factweek.gdelt.read-timeout:15s}") readTimeout: Duration,
) : GdeltCandidates {
    private val client = builder
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout)
            setReadTimeout(readTimeout)
        })
        .build()

    override fun findTechnologyCandidates(query: String, from: Instant, to: Instant, maximum: Int): List<GdeltCandidate> {
        require(query.isNotBlank())
        require(from.isBefore(to)) { "from must be before to" }
        require(maximum in 1..250)

        val uri = UriComponentsBuilder.fromPath("/api/v2/doc/doc")
            .queryParam("query", query)
            .queryParam("mode", "artlist")
            .queryParam("format", "json")
            .queryParam("startdatetime", GDELT_REQUEST_DATE.format(from))
            .queryParam("enddatetime", GDELT_REQUEST_DATE.format(to))
            .queryParam("sort", "datedesc")
            .queryParam("maxrecords", maximum)
            .build()
            .encode()
            .toUri()

        val response = try {
            client.get().uri(uri).retrieve().body(GdeltResponse::class.java)
        } catch (exception: RestClientException) {
            logger.warn("gdelt_request_failed query={} from={} to={}", query, from, to, exception)
            throw GdeltRequestException("GDELT request failed", exception)
        }

        if (response == null) {
            logger.warn("gdelt_request_failed query={} from={} to={} reason=empty_response", query, from, to)
            throw GdeltRequestException("GDELT returned an empty response")
        }
        return response.articles.mapNotNull { article ->
            runCatching {
                GdeltCandidate(
                    title = article.title,
                    url = URI(article.url),
                    sourceCountry = article.sourcecountry,
                    language = article.language,
                    discoveredAt = article.seendate?.let { GDELT_DATE.parse(it, Instant::from) },
                )
            }.getOrNull()
        }
    }

    private companion object {
        val GDELT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
        val GDELT_REQUEST_DATE: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)
        val logger = LoggerFactory.getLogger(GdeltDocClient::class.java)
    }
}

internal class GdeltRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GdeltResponse(val articles: List<GdeltArticle> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GdeltArticle(
    val title: String = "",
    val url: String = "",
    val sourcecountry: String? = null,
    val language: String? = null,
    val seendate: String? = null,
)
