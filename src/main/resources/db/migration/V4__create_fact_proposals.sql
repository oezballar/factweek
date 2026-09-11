CREATE TABLE fact_proposal (
    id UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES source_document(candidate_id),
    statement VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    occurred_on DATE,
    evidence_text VARCHAR(2000) NOT NULL,
    evidence_level VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    extraction_model VARCHAR(255) NOT NULL,
    extraction_schema_version VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rejection_reason VARCHAR(1000),
    CONSTRAINT fact_proposal_status_ck CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT fact_proposal_statement_uk UNIQUE (source_document_id, statement, extraction_schema_version)
);

CREATE TABLE fact_proposal_entity (
    proposal_id UUID NOT NULL REFERENCES fact_proposal(id) ON DELETE CASCADE,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64) NOT NULL
);

CREATE INDEX fact_proposal_status_idx ON fact_proposal (status);
CREATE INDEX fact_proposal_source_document_idx ON fact_proposal (source_document_id);
CREATE INDEX fact_proposal_category_idx ON fact_proposal (category);
