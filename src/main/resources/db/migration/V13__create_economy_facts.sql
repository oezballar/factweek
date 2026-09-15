CREATE TABLE economy_fact (
    id UUID PRIMARY KEY,
    statement VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    evidence_level VARCHAR(64) NOT NULL,
    occurred_on DATE,
    geography_kind VARCHAR(16),
    geography_name VARCHAR(255),
    geography_code VARCHAR(32),
    CONSTRAINT economy_fact_geography_ck CHECK (
        (geography_kind IS NULL AND geography_name IS NULL AND geography_code IS NULL) OR
        (geography_kind IS NOT NULL AND geography_name IS NOT NULL)
    )
);

CREATE TABLE economy_fact_entity (
    fact_id UUID NOT NULL REFERENCES economy_fact(id) ON DELETE CASCADE,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64) NOT NULL
);

CREATE TABLE economy_fact_source (
    fact_id UUID NOT NULL REFERENCES economy_fact(id) ON DELETE CASCADE,
    source_url VARCHAR(2000) NOT NULL,
    source_publisher VARCHAR(255) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_published_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE economy_fact_measurement (
    fact_id UUID PRIMARY KEY REFERENCES economy_fact(id) ON DELETE CASCADE,
    measurement_value NUMERIC(30, 10) NOT NULL,
    measurement_unit VARCHAR(32) NOT NULL,
    release_status VARCHAR(32),
    seasonal_adjustment VARCHAR(32),
    value_basis VARCHAR(32),
    reference_period_from DATE NOT NULL,
    reference_period_to DATE NOT NULL,
    reference_period_granularity VARCHAR(16) NOT NULL,
    CONSTRAINT economy_fact_measurement_period_ck CHECK (reference_period_from <= reference_period_to),
    CONSTRAINT economy_fact_measurement_count_ck CHECK (
        measurement_unit <> 'COUNT' OR measurement_value = trunc(measurement_value)
    )
);
