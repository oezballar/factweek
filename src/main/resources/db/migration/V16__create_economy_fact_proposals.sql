CREATE TABLE economy_fact_proposal (
    id UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES source_document(candidate_id),
    statement VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_on DATE,
    geography_kind VARCHAR(16),
    geography_name VARCHAR(255),
    geography_code VARCHAR(32),
    evidence_text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_evidence_level VARCHAR(64),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(1000),
    economy_fact_id UUID UNIQUE REFERENCES economy_fact(id),
    version BIGINT NOT NULL,
    CONSTRAINT economy_fact_proposal_geography_ck CHECK ((geography_kind IS NULL AND geography_name IS NULL AND geography_code IS NULL) OR (geography_kind IS NOT NULL AND geography_name IS NOT NULL)),
    CONSTRAINT economy_fact_proposal_review_ck CHECK (
        (status = 'PROPOSED' AND reviewed_at IS NULL AND reviewed_evidence_level IS NULL AND rejection_reason IS NULL AND economy_fact_id IS NULL) OR
        (status = 'ACCEPTED' AND reviewed_at IS NOT NULL AND reviewed_evidence_level IS NOT NULL AND rejection_reason IS NULL AND economy_fact_id IS NOT NULL) OR
        (status = 'REJECTED' AND reviewed_at IS NOT NULL AND reviewed_evidence_level IS NULL AND rejection_reason IS NOT NULL AND economy_fact_id IS NULL)
    )
);

CREATE TABLE economy_fact_proposal_entity (
    proposal_id UUID NOT NULL REFERENCES economy_fact_proposal(id) ON DELETE CASCADE,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64) NOT NULL
);

CREATE TABLE economy_fact_proposal_measurement (
    proposal_id UUID PRIMARY KEY REFERENCES economy_fact_proposal(id) ON DELETE CASCADE,
    measurement_value NUMERIC NOT NULL,
    measurement_unit VARCHAR(32) NOT NULL,
    release_status VARCHAR(32),
    seasonal_adjustment VARCHAR(32),
    value_basis VARCHAR(32),
    reference_period_from DATE NOT NULL,
    reference_period_to DATE NOT NULL,
    reference_period_granularity VARCHAR(16) NOT NULL,
    CONSTRAINT economy_fact_proposal_measurement_period_ck CHECK (reference_period_from <= reference_period_to),
    CONSTRAINT economy_fact_proposal_measurement_count_ck CHECK (measurement_unit <> 'COUNT' OR measurement_value = trunc(measurement_value)),
    CONSTRAINT economy_fact_proposal_measurement_precision_ck CHECK (abs(measurement_value) < 100000000000000000000::NUMERIC AND min_scale(measurement_value) <= 10)
);
