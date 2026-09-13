ALTER TABLE fact_proposal RENAME COLUMN evidence_level TO suggested_evidence_level;
ALTER TABLE fact_proposal ADD COLUMN reviewed_evidence_level VARCHAR(64);
UPDATE fact_proposal p SET reviewed_evidence_level = f.evidence_level
FROM technology_fact f WHERE p.status = 'ACCEPTED' AND p.technology_fact_id = f.id;
ALTER TABLE fact_proposal ADD CONSTRAINT fact_proposal_review_state_ck CHECK (
 (status = 'PROPOSED' AND reviewed_evidence_level IS NULL AND technology_fact_id IS NULL AND rejection_reason IS NULL AND reviewed_at IS NULL) OR
 (status = 'ACCEPTED' AND reviewed_evidence_level IS NOT NULL AND technology_fact_id IS NOT NULL AND rejection_reason IS NULL AND reviewed_at IS NOT NULL) OR
 (status = 'REJECTED' AND reviewed_evidence_level IS NULL AND technology_fact_id IS NULL AND rejection_reason IS NOT NULL AND reviewed_at IS NOT NULL)
);
