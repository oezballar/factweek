package dev.factweek.ingestion.internal

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

@Testcontainers
class V9CandidateSourceTypeMigrationTest {
    @Test
    fun `migrates V8 candidates conservatively and enforces source type`() {
        migrate(target = "8")
        val gdeltId = UUID.randomUUID()
        val manualId = UUID.randomUUID()
        postgres.createConnection("").use { connection ->
            connection.prepareStatement(
                """insert into news_candidate
                   (id, canonical_url, title, source_domain, published_at, fetched_at, status, publisher, language, discovery_provider)
                   values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ).use { statement ->
                insert(statement, gdeltId, "https://example.org/gdelt", "GDELT title", "example.org", "GDELT")
                statement.addBatch()
                insert(statement, manualId, "https://nasa.gov/result", "NASA title", "nasa.gov", "MANUAL")
                statement.addBatch()
                statement.executeBatch()
            }
        }

        migrate(target = null)

        postgres.createConnection("").use { connection ->
            connection.prepareStatement("select id, canonical_url, title, publisher, language, discovery_provider, source_type from news_candidate order by id").use { statement ->
                statement.executeQuery().use { rows ->
                    val found = mutableMapOf<UUID, List<String?>>()
                    while (rows.next()) found[rows.getObject("id", UUID::class.java)] = listOf(
                        rows.getString("canonical_url"), rows.getString("title"), rows.getString("publisher"),
                        rows.getString("language"), rows.getString("discovery_provider"), rows.getString("source_type"),
                    )
                    assertEquals(listOf("https://example.org/gdelt", "GDELT title", "example.org", "en", "GDELT", "NEWS_REPORT"), found[gdeltId])
                    assertEquals(listOf("https://nasa.gov/result", "NASA title", "nasa.gov", "en", "MANUAL", "NEWS_REPORT"), found[manualId])
                }
            }
            connection.prepareStatement("select is_nullable, column_default from information_schema.columns where table_name = 'news_candidate' and column_name = 'source_type'").use { statement ->
                statement.executeQuery().use { row -> row.next(); assertEquals("NO", row.getString(1)); assertEquals(null, row.getString(2)) }
            }
            assertThrows(SQLException::class.java) {
                connection.prepareStatement("insert into news_candidate (id, canonical_url, title, source_domain, fetched_at, status, publisher, discovery_provider) values (?, 'https://x.example/', 'x', 'x.example', now(), 'DISCOVERED', 'x', 'MANUAL')").use { statement ->
                    statement.setObject(1, UUID.randomUUID()); statement.executeUpdate()
                }
            }
        }
    }

    private fun migrate(target: String?) {
        Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration").apply { if (target != null) target(target) }.load().migrate()
    }

    private fun insert(statement: java.sql.PreparedStatement, id: UUID, url: String, title: String, domain: String, provider: String) {
        statement.setObject(1, id); statement.setString(2, url); statement.setString(3, title); statement.setString(4, domain)
        statement.setTimestamp(5, java.sql.Timestamp.from(Instant.parse("2026-09-10T00:00:00Z"))); statement.setTimestamp(6, java.sql.Timestamp.from(Instant.parse("2026-09-11T00:00:00Z")))
        statement.setString(7, "DISCOVERED"); statement.setString(8, domain); statement.setString(9, "en"); statement.setString(10, provider)
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
