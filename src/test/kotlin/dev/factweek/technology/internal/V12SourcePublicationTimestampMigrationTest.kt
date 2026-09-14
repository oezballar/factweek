package dev.factweek.technology.internal

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@Testcontainers
class V12SourcePublicationTimestampMigrationTest {
    @Test
    fun `adds an optional source publication timestamp without changing existing sources`() {
        migrate("11")
        val factId = UUID.randomUUID()
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """insert into technology_fact
                       (id, statement, category, event_type, readiness, evidence_level, occurred_on)
                       values ('$factId', 'fact', 'AI_AND_SOFTWARE', 'TECHNOLOGY_DEPLOYED', 'PRODUCTION_USE', 'PRIMARY_CONFIRMED', null)""",
                )
                statement.executeUpdate(
                    """insert into technology_fact_source (fact_id, source_url, source_publisher, source_type)
                       values ('$factId', 'https://example.org/source', 'Example', 'PAPER')""",
                )
            }
            assertFalse(columnExists(connection))
        }

        migrate(null)

        postgres.createConnection("").use { connection ->
            connection.prepareStatement(
                "select source_published_at from technology_fact_source where fact_id = ?",
            ).use { statement ->
                statement.setObject(1, factId)
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals(null, row.getObject(1))
                }
            }
            connection.prepareStatement(
                """select is_nullable, column_default from information_schema.columns
                   where table_name = 'technology_fact_source' and column_name = 'source_published_at'""",
            ).use { statement ->
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals("YES", row.getString(1))
                    assertEquals(null, row.getString(2))
                }
            }
        }
    }

    private fun columnExists(connection: java.sql.Connection): Boolean = connection.prepareStatement(
        "select count(*) from information_schema.columns where table_name = 'technology_fact_source' and column_name = 'source_published_at'",
    ).use { statement ->
        statement.executeQuery().use { row -> row.next(); row.getInt(1) == 1 }
    }

    private fun migrate(target: String?) {
        Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration").apply { if (target != null) target(target) }.load().migrate()
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
