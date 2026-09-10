CREATE TABLE source_document (
    candidate_id UUID PRIMARY KEY REFERENCES news_candidate(id) ON DELETE CASCADE,
    source_url VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    media_type VARCHAR(255),
    http_status INTEGER,
    text_content TEXT,
    content_sha256 VARCHAR(64),
    fetched_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL,
    failure_reason VARCHAR(64),
    CONSTRAINT source_document_fetched_content_ck CHECK (
        status <> 'FETCHED' OR (text_content IS NOT NULL AND content_sha256 IS NOT NULL AND fetched_at IS NOT NULL)
    )
);

CREATE INDEX source_document_status_idx ON source_document (status);
