package dev.factweek.processing.internal

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers
class V15DocumentSectionClassificationMigrationTest {
    @Test
    fun `migrates V14 data and creates document classification constraints`() {
        migrate("14")
        val documentId = UUID.randomUUID()
        val technologyFactId = UUID.randomUUID()
        val economyFactId = UUID.randomUUID()
        val now = OffsetDateTime.of(2026, 9, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """insert into news_candidate
                       (id, canonical_url, title, publisher, source_domain, published_at, language, discovery_provider, source_type, fetched_at, status)
                       values ('$documentId', 'https://example.org/$documentId', 'Candidate', 'Example', 'example.org', '$now', 'en', 'MANUAL', 'PRIMARY_DOCUMENT', '$now', 'DISCOVERED')""",
                )
                statement.executeUpdate(
                    """insert into source_document
                       (candidate_id, source_url, status, text_content, content_sha256, fetched_at, last_attempt_at, attempt_count)
                       values ('$documentId', 'https://example.org/$documentId', 'FETCHED', 'Stored article', '${"a".repeat(64)}', '$now', '$now', 1)""",
                )
                statement.executeUpdate(
                    """insert into technology_fact
                       (id, statement, category, event_type, readiness, evidence_level, occurred_on)
                       values ('$technologyFactId', 'Technology fact', 'AI_AND_SOFTWARE', 'TECHNOLOGY_DEPLOYED', 'PRODUCTION_USE', 'PRIMARY_CONFIRMED', '2026-09-15')""",
                )
                statement.executeUpdate(
                    """insert into economy_fact
                       (id, statement, category, event_type, evidence_level, occurred_on)
                       values ('$economyFactId', 'Economy fact', 'PRICES_AND_INFLATION', 'INDICATOR_VALUE_REPORTED', 'PRIMARY_CONFIRMED', null)""",
                )
            }
        }

        migrate(null)

        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select text_content, content_sha256 from source_document where candidate_id = '$documentId'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Stored article", rows.getString("text_content"))
                    assertEquals("a".repeat(64), rows.getString("content_sha256"))
                }
                statement.executeQuery("select statement from technology_fact where id = '$technologyFactId'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Technology fact", rows.getString(1))
                }
                statement.executeQuery("select statement from economy_fact where id = '$economyFactId'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Economy fact", rows.getString(1))
                }
                statement.executeQuery("select to_regclass('document_section_classification'), to_regclass('document_section_classification_section')").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("document_section_classification", rows.getString(1))
                    assertEquals("document_section_classification_section", rows.getString(2))
                }
                statement.executeQuery("select count(*) from document_section_classification").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }

            connection.prepareStatement(
                "insert into document_section_classification (source_document_id, classification_version, classified_at) values (?, 'v1', ?)",
            ).use { statement ->
                statement.setObject(1, documentId)
                statement.setObject(2, now)
                statement.executeUpdate()
            }
            connection.createStatement().use { statement ->
                val duplicate = org.junit.jupiter.api.assertThrows<SQLException> {
                    statement.executeUpdate("insert into document_section_classification (source_document_id, classification_version, classified_at) values ('$documentId', 'v1', '$now')")
                }
                assertEquals("23505", duplicate.sqlState)
                val missingParent = org.junit.jupiter.api.assertThrows<SQLException> {
                    statement.executeUpdate("insert into document_section_classification_section (source_document_id, classification_version, section_id) values ('$documentId', 'missing', 'technology')")
                }
                assertEquals("23503", missingParent.sqlState)
                statement.executeUpdate("insert into document_section_classification_section (source_document_id, classification_version, section_id) values ('$documentId', 'v1', 'technology')")
                statement.executeUpdate("delete from document_section_classification where source_document_id = '$documentId' and classification_version = 'v1'")
                statement.executeQuery("select count(*) from document_section_classification_section where source_document_id = '$documentId'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    private fun migrate(target: String?) {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .apply { if (target != null) target(target) }
            .load()
            .migrate()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
