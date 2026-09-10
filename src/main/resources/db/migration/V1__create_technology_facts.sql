CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE technology_fact (
    id UUID PRIMARY KEY,
    statement VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    readiness VARCHAR(64) NOT NULL,
    evidence_level VARCHAR(64) NOT NULL,
    occurred_on DATE NOT NULL
);

CREATE INDEX technology_fact_occurred_on_idx ON technology_fact (occurred_on DESC);
CREATE INDEX technology_fact_category_idx ON technology_fact (category);

CREATE TABLE technology_fact_entity (
    fact_id UUID NOT NULL REFERENCES technology_fact(id) ON DELETE CASCADE,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64) NOT NULL
);

CREATE TABLE technology_fact_source (
    fact_id UUID NOT NULL REFERENCES technology_fact(id) ON DELETE CASCADE,
    source_url VARCHAR(2000) NOT NULL,
    source_publisher VARCHAR(255) NOT NULL,
    source_type VARCHAR(64) NOT NULL
);
