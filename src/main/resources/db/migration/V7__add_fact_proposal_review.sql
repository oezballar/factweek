ALTER TABLE fact_proposal
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN technology_fact_id UUID REFERENCES technology_fact(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fact_proposal
    ADD CONSTRAINT fact_proposal_technology_fact_uk UNIQUE (technology_fact_id);

CREATE INDEX fact_proposal_reviewed_at_idx ON fact_proposal (reviewed_at DESC);
