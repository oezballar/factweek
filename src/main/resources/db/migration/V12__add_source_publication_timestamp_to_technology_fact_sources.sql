ALTER TABLE technology_fact_source
    ADD COLUMN source_published_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX technology_fact_source_fact_published_at_idx
    ON technology_fact_source (fact_id, source_published_at);
