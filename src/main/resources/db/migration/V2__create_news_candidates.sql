CREATE TABLE news_candidate (
    id UUID PRIMARY KEY,
    canonical_url VARCHAR(2000) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    source_domain VARCHAR(255) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT news_candidate_canonical_url_uk UNIQUE (canonical_url)
);

CREATE INDEX news_candidate_status_idx ON news_candidate (status);
CREATE INDEX news_candidate_published_at_idx ON news_candidate (published_at DESC);
