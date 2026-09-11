ALTER TABLE fact_proposal_extraction_attempt
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN failure_reason VARCHAR(64);

UPDATE fact_proposal_extraction_attempt
SET status = 'COMPLETED',
    claimed_at = created_at,
    completed_at = created_at;

ALTER TABLE fact_proposal_extraction_attempt
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN claimed_at SET NOT NULL,
    ADD CONSTRAINT fact_proposal_extraction_attempt_status_ck
        CHECK (status IN ('CLAIMED', 'COMPLETED', 'FAILED'));

CREATE INDEX fact_proposal_extraction_attempt_claim_idx
    ON fact_proposal_extraction_attempt (extraction_schema_version, status, claimed_at);
