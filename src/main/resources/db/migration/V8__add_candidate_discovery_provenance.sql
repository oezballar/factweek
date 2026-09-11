ALTER TABLE news_candidate
    ADD COLUMN publisher VARCHAR(255),
    ADD COLUMN language VARCHAR(32),
    ADD COLUMN discovery_provider VARCHAR(32);

UPDATE news_candidate
SET publisher = source_domain,
    discovery_provider = 'GDELT';

ALTER TABLE news_candidate
    ALTER COLUMN publisher SET NOT NULL,
    ALTER COLUMN discovery_provider SET NOT NULL;

CREATE INDEX news_candidate_discovery_provider_idx ON news_candidate (discovery_provider);
