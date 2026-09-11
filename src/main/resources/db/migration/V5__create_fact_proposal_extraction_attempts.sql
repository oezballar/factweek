CREATE TABLE fact_proposal_extraction_attempt (
    id UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES source_document(candidate_id),
    extraction_model VARCHAR(255) NOT NULL,
    extraction_schema_version VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fact_proposal_extraction_attempt_uk
        UNIQUE (source_document_id, extraction_schema_version)
);

CREATE INDEX fact_proposal_extraction_attempt_schema_idx
    ON fact_proposal_extraction_attempt (extraction_schema_version, source_document_id);
