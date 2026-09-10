package dev.factweek.ingestion.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.GdeltCandidates
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter

@Component
internal class GdeltDocClient(builder: RestClient.Builder) : GdeltCandidates {
    private val client = builder.baseUrl("https://api.gdeltproject.org").build()

    override fun findTechnologyCandidates(query: String, maximum: Int): List<GdeltCandidate> {
        require(query.isNotBlank())
        require(maximum in 1..250)

        val uri = UriComponentsBuilder.fromPath("/api/v2/doc/doc")
            .queryParam("query", query)
            .queryParam("mode", "artlist")
            .queryParam("format", "json")
            .queryParam("timespan", "1week")
            .queryParam("sort", "datedesc")
            .queryParam("maxrecords", maximum)
            .build()
            .encode()
            .toUriString()

        val response = client.get().uri(uri).retrieve().body(GdeltResponse::class.java)
        return response?.articles.orEmpty().mapNotNull { article ->
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
    }
}

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
