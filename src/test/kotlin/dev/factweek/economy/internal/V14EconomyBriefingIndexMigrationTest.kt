package dev.factweek.economy.internal

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@Testcontainers
class V14EconomyBriefingIndexMigrationTest {
    @Test
    fun `adds the economy source index without changing V13 data or schema`() {
        migrate("13")
        val factId = UUID.randomUUID()

        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """insert into economy_fact
                       (id, statement, category, event_type, evidence_level, occurred_on)
                       values ('$factId', 'fact', 'PRICES_AND_INFLATION', 'INDICATOR_VALUE_REPORTED', 'PRIMARY_CONFIRMED', null)""",
                )
                statement.executeUpdate(
                    """insert into economy_fact_source
                       (fact_id, source_url, source_publisher, source_type, source_published_at)
                       values ('$factId', 'https://example.org/source', 'Example', 'PRIMARY_DOCUMENT', '2026-09-10T08:00:00Z')""",
                )
            }
        }

        migrate(null)

        postgres.createConnection("").use { connection ->
            connection.prepareStatement(
                "select statement, occurred_on from economy_fact where id = ?",
            ).use { statement ->
                statement.setObject(1, factId)
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals("fact", row.getString("statement"))
                    assertEquals(null, row.getObject("occurred_on"))
                }
            }
            connection.prepareStatement(
                "select source_published_at from economy_fact_source where fact_id = ?",
            ).use { statement ->
                statement.setObject(1, factId)
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals("2026-09-10T08:00:00Z", row.getObject(1, java.time.OffsetDateTime::class.java).toInstant().toString())
                }
            }
            connection.prepareStatement(
                """select array_agg(attribute.attname order by array_position(index_definition.indkey, attribute.attnum))
                   from pg_index index_definition
                   join pg_class index_relation on index_relation.oid = index_definition.indexrelid
                   join pg_class table_relation on table_relation.oid = index_definition.indrelid
                   join pg_attribute attribute on attribute.attrelid = table_relation.oid
                     and attribute.attnum = any(index_definition.indkey)
                   where table_relation.relname = 'economy_fact_source'
                     and index_relation.relname = 'economy_fact_source_fact_published_at_idx'""",
            ).use { statement ->
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals(listOf("fact_id", "source_published_at"), row.getArray(1).array.let { (it as Array<*>).toList() })
                }
            }
            connection.prepareStatement(
                """select count(*), max(column_default)
                   from information_schema.columns
                   where table_name = 'economy_fact_source'""",
            ).use { statement ->
                statement.executeQuery().use { row ->
                    row.next()
                    assertEquals(5, row.getInt(1))
                    assertEquals(null, row.getString(2))
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
