ALTER TABLE news_candidate ADD COLUMN source_type VARCHAR(32);
UPDATE news_candidate SET source_type = 'NEWS_REPORT';
ALTER TABLE news_candidate ALTER COLUMN source_type SET NOT NULL;
