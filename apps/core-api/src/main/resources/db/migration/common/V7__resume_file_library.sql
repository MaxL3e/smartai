CREATE TABLE resume_document (
    resume_document_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    external_candidate_id VARCHAR(256) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_file_version_id UUID,
    candidate_id UUID,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_resume_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_resume_document_candidate FOREIGN KEY (tenant_id, candidate_id)
        REFERENCES candidate (tenant_id, candidate_id),
    CONSTRAINT uq_resume_document_tenant_id UNIQUE (tenant_id, resume_document_id),
    CONSTRAINT uq_resume_document_external UNIQUE (tenant_id, source_system, external_candidate_id),
    CONSTRAINT ck_resume_document_status CHECK (status IN ('READY', 'PARSE_FAILED'))
);

CREATE INDEX ix_resume_document_tenant_updated
    ON resume_document (tenant_id, updated_at, resume_document_id);

CREATE TABLE resume_file_version (
    resume_file_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    resume_document_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    file_name VARCHAR(255) NOT NULL,
    declared_mime_type VARCHAR(120) NOT NULL,
    detected_mime_type VARCHAR(120),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    sha256 CHAR(64) NOT NULL,
    raw_base64 TEXT NOT NULL,
    extracted_text TEXT,
    parse_status VARCHAR(20) NOT NULL,
    parser_version VARCHAR(120) NOT NULL,
    failure_code VARCHAR(100),
    retryable BOOLEAN NOT NULL,
    parsed_json TEXT NOT NULL,
    candidate_receipt_json TEXT,
    normalized_resume_version_id UUID,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_resume_file_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_resume_file_document FOREIGN KEY (tenant_id, resume_document_id)
        REFERENCES resume_document (tenant_id, resume_document_id),
    CONSTRAINT fk_resume_file_normalized_version FOREIGN KEY (tenant_id, normalized_resume_version_id)
        REFERENCES resume_version (tenant_id, resume_version_id),
    CONSTRAINT uq_resume_file_tenant_id UNIQUE (tenant_id, resume_file_version_id),
    CONSTRAINT uq_resume_file_document_no UNIQUE (tenant_id, resume_document_id, version_no),
    CONSTRAINT uq_resume_file_document_hash UNIQUE (tenant_id, resume_document_id, sha256),
    CONSTRAINT ck_resume_file_parse_status CHECK (parse_status IN ('PARSED', 'PARSE_FAILED')),
    CONSTRAINT ck_resume_file_parse_outcome CHECK (
        (parse_status = 'PARSED'
            AND extracted_text IS NOT NULL
            AND failure_code IS NULL
            AND retryable = FALSE
            AND candidate_receipt_json IS NOT NULL
            AND normalized_resume_version_id IS NOT NULL)
        OR
        (parse_status = 'PARSE_FAILED'
            AND failure_code IS NOT NULL
            AND candidate_receipt_json IS NULL
            AND normalized_resume_version_id IS NULL)
    )
);

ALTER TABLE resume_document
    ADD CONSTRAINT fk_resume_document_current_file
    FOREIGN KEY (tenant_id, current_file_version_id)
    REFERENCES resume_file_version (tenant_id, resume_file_version_id);

CREATE INDEX ix_resume_file_document_created
    ON resume_file_version (tenant_id, resume_document_id, version_no);

CREATE INDEX ix_resume_file_parse_status
    ON resume_file_version (tenant_id, parse_status, created_at);

CREATE TABLE resume_file_command (
    resume_file_command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    resume_document_id UUID,
    operation VARCHAR(80) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_resume_file_command_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
	CONSTRAINT fk_resume_file_command_document FOREIGN KEY (tenant_id, resume_document_id)
		REFERENCES resume_document (tenant_id, resume_document_id),
    CONSTRAINT uq_resume_file_command_key UNIQUE (tenant_id, operation, idempotency_key)
);

CREATE INDEX ix_resume_file_command_document
    ON resume_file_command (tenant_id, resume_document_id, created_at);
