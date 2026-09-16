package dev.factweek.economy.internal

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.util.UUID

@Testcontainers
class V16EconomyFactProposalMigrationTest {
    @Test
    fun `migrates existing data from V15 to V16 without backfill`() {
        migrate("15")
        val candidateId = UUID.randomUUID()
        val factId = UUID.randomUUID()

        postgres.createConnection("").use { connection ->
            insertCandidateAndDocument(connection, candidateId)
            connection.prepareStatement(
                """insert into economy_fact
                    (id, statement, category, event_type, evidence_level, occurred_on)
                    values (?, 'Existing fact', 'PRICES_AND_INFLATION', 'INDICATOR_VALUE_REPORTED', 'PRIMARY_CONFIRMED', null)""",
            ).use { statement ->
                statement.setObject(1, factId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """insert into document_section_classification
                    (source_document_id, classification_version, classified_at)
                    values (?, 'article-section-classification-v1', '2026-09-10T00:00:00Z')""",
            ).use { statement ->
                statement.setObject(1, candidateId)
                statement.executeUpdate()
            }
            connection.commit()
        }

        migrate("16")

        postgres.createConnection("").use { connection ->
            assertEquals(1, count(connection, "select count(*) from source_document where candidate_id = '$candidateId'"))
            assertEquals(1, count(connection, "select count(*) from economy_fact where id = '$factId'"))
            assertEquals(1, count(connection, "select count(*) from document_section_classification where source_document_id = '$candidateId'"))
            assertEquals(0, count(connection, "select count(*) from economy_fact_proposal"))
            assertTrue(tableExists(connection, "economy_fact_proposal"))
            assertTrue(tableExists(connection, "economy_fact_proposal_entity"))
            assertTrue(tableExists(connection, "economy_fact_proposal_measurement"))
            assertTrue(foreignKeyExists(connection, "economy_fact_proposal", "source_document_id", "source_document", "candidate_id"))
            assertTrue(foreignKeyExists(connection, "economy_fact_proposal_entity", "proposal_id", "economy_fact_proposal", "id"))
            assertEquals(0, count(connection, "select count(*) from economy_fact_proposal where source_document_id = '$candidateId'"))
        }
    }

    private fun insertCandidateAndDocument(connection: Connection, id: UUID) {
        connection.autoCommit = false
        connection.prepareStatement(
            """insert into news_candidate
                (id, canonical_url, title, source_domain, published_at, fetched_at, status, publisher, language, discovery_provider, source_type)
                values (?, ?, 'Existing candidate', 'example.org', null, '2026-09-09T00:00:00Z', 'DISCOVERED', 'Example', 'en', 'MANUAL', 'NEWS_REPORT')""",
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "https://example.org/$id")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """insert into source_document
                (candidate_id, source_url, status, media_type, http_status, text_content, content_sha256, fetched_at, last_attempt_at, attempt_count, failure_reason)
                values (?, ?, 'FETCHED', 'text/html', 200, 'Existing article', repeat('a', 64), '2026-09-09T00:00:00Z', '2026-09-09T00:00:00Z', 1, null)""",
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "https://example.org/$id")
            statement.executeUpdate()
        }
    }

    private fun count(connection: Connection, sql: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

    private fun tableExists(connection: Connection, table: String): Boolean = connection.prepareStatement(
        "select exists (select 1 from information_schema.tables where table_name = ?)",
    ).use { statement ->
        statement.setString(1, table)
        statement.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
    }

    private fun foreignKeyExists(connection: Connection, table: String, column: String, targetTable: String, targetColumn: String): Boolean = connection.prepareStatement(
        """select exists (
            select 1 from information_schema.key_column_usage k
            join information_schema.constraint_column_usage c on c.constraint_name = k.constraint_name
            where k.table_name = ? and k.column_name = ? and c.table_name = ? and c.column_name = ?
        )""",
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, column)
        statement.setString(3, targetTable)
        statement.setString(4, targetColumn)
        statement.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
    }

    private fun migrate(target: String) {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .target(target)
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
