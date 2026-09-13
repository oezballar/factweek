package dev.factweek.technology.internal

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
import java.util.UUID

@Testcontainers
class V10ReviewedEvidenceMigrationTest {
    @Test
    fun `migrates proposal evidence and enforces review state combinations`() {
        migrate("9")
        val proposed = UUID.randomUUID(); val rejected = UUID.randomUUID(); val accepted = UUID.randomUUID(); val fact = UUID.randomUUID()
        postgres.createConnection("").use { c ->
            c.createStatement().use { s ->
                s.executeUpdate("insert into news_candidate (id,canonical_url,title,source_domain,fetched_at,status,publisher,discovery_provider,source_type) values ('00000000-0000-0000-0000-000000000001','https://example.org/','candidate','example.org','2026-01-01T00:00:00Z','DISCOVERED','Example','MANUAL','NEWS_REPORT')")
                s.executeUpdate("insert into source_document (candidate_id,source_url,status,last_attempt_at,attempt_count) values ('00000000-0000-0000-0000-000000000001','https://example.org/','FAILED','2026-01-01T00:00:00Z',1)")
                s.executeUpdate("insert into technology_fact (id, statement, category, event_type, readiness, evidence_level, occurred_on) values ('$fact','fact','AI_AND_SOFTWARE','TECHNOLOGY_DEPLOYED','PRODUCTION_USE','PRIMARY_CONFIRMED','2026-01-01')")
                insertProposal(s, proposed, "PROPOSED", "REPORTED", null, null, null)
                insertProposal(s, rejected, "REJECTED", "DOCUMENTED", null, "2026-01-02T00:00:00Z", "Rejected")
                insertProposal(s, accepted, "ACCEPTED", "DOCUMENTED", fact, "2026-01-02T00:00:00Z", null)
            }
            assertEquals(true, column(c, "evidence_level")); assertEquals(false, column(c, "suggested_evidence_level")); assertEquals(false, column(c, "reviewed_evidence_level"))
        }
        migrate(null)
        postgres.createConnection("").use { c ->
            assertFalse(column(c, "evidence_level")); assertEquals(true, column(c, "suggested_evidence_level")); assertEquals(true, column(c, "reviewed_evidence_level"))
            c.createStatement().executeQuery("select id, suggested_evidence_level, reviewed_evidence_level, technology_fact_id from fact_proposal order by id").use { rs ->
                val values = mutableMapOf<UUID, List<String?>>()
                while (rs.next()) values[rs.getObject(1, UUID::class.java)] = listOf(rs.getString(2), rs.getString(3), rs.getString(4))
                assertEquals(listOf("REPORTED", null, null), values[proposed])
                assertEquals(listOf("DOCUMENTED", null, null), values[rejected])
                assertEquals(listOf("DOCUMENTED", "PRIMARY_CONFIRMED", fact.toString()), values[accepted])
            }
        }
        invalidStates(proposed, rejected, accepted, fact).forEach { sql -> assertThrows(SQLException::class.java) { postgres.createConnection("").use { it.createStatement().executeUpdate(sql) } } }
    }

    private fun insertProposal(s: java.sql.Statement, id: UUID, status: String, evidence: String, fact: UUID?, reviewed: String?, rejection: String?) {
        val factValue = fact?.let { "'$it'" } ?: "null"; val reviewedValue = reviewed?.let { "'$it'" } ?: "null"; val rejectionValue = rejection?.let { "'$it'" } ?: "null"
        s.executeUpdate("insert into fact_proposal (id,source_document_id,statement,category,evidence_text,evidence_level,status,extraction_model,extraction_schema_version,created_at,rejection_reason,technology_fact_id,reviewed_at,version) values ('$id',(select id from news_candidate limit 1),'$status proposal','AI_AND_SOFTWARE','evidence','$evidence','$status','model','v1','2026-01-01T00:00:00Z',$rejectionValue,$factValue,$reviewedValue,0)")
    }
    private fun column(c: java.sql.Connection, name: String): Boolean = c.prepareStatement("select count(*) from information_schema.columns where table_name='fact_proposal' and column_name=?").use { s -> s.setString(1,name); s.executeQuery().use { it.next(); it.getInt(1) == 1 } }
    private fun migrate(target: String?) { Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).locations("classpath:db/migration").apply { if(target != null) target(target) }.load().migrate() }
    private fun invalidStates(proposed: UUID, rejected: UUID, accepted: UUID, fact: UUID): List<String> = listOf(
        "update fact_proposal set reviewed_evidence_level='PRIMARY_CONFIRMED' where id='$proposed'", "update fact_proposal set technology_fact_id='$fact' where id='$proposed'", "update fact_proposal set reviewed_at=now() where id='$proposed'", "update fact_proposal set rejection_reason='x' where id='$proposed'",
        "update fact_proposal set reviewed_evidence_level=null where id='$accepted'", "update fact_proposal set technology_fact_id=null where id='$accepted'", "update fact_proposal set reviewed_at=null where id='$accepted'", "update fact_proposal set rejection_reason='x' where id='$accepted'",
        "update fact_proposal set rejection_reason=null where id='$rejected'", "update fact_proposal set reviewed_at=null where id='$rejected'", "update fact_proposal set reviewed_evidence_level='PRIMARY_CONFIRMED' where id='$rejected'", "update fact_proposal set technology_fact_id='$fact' where id='$rejected'"
    )
    companion object { @Container @JvmStatic val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres")) }
}
