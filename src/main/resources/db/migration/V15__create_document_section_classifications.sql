CREATE TABLE document_section_classification (
    source_document_id UUID NOT NULL REFERENCES source_document(candidate_id),
    classification_version VARCHAR(128) NOT NULL,
    classified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT document_section_classification_pk PRIMARY KEY (source_document_id, classification_version)
);

CREATE TABLE document_section_classification_section (
    source_document_id UUID NOT NULL,
    classification_version VARCHAR(128) NOT NULL,
    section_id VARCHAR(64) NOT NULL,
    CONSTRAINT document_section_classification_section_pk PRIMARY KEY (source_document_id, classification_version, section_id),
    CONSTRAINT document_section_classification_section_fk FOREIGN KEY (source_document_id, classification_version)
        REFERENCES document_section_classification(source_document_id, classification_version) ON DELETE CASCADE
);
