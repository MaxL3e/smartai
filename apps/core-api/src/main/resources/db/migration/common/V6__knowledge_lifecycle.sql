ALTER TABLE human_checkpoint ALTER COLUMN recruitment_task_id DROP NOT NULL;

CREATE TABLE knowledge_document (
    knowledge_document_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    knowledge_type VARCHAR(40) NOT NULL,
    owner_resource_type VARCHAR(80) NOT NULL,
    owner_resource_id UUID NOT NULL,
    owner_resource_version BIGINT NOT NULL CHECK (owner_resource_version > 0),
    classification VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tags_json TEXT NOT NULL,
    access_policy_id UUID,
    retention_until TIMESTAMP WITH TIME ZONE,
    current_version_id UUID,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_knowledge_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_knowledge_document_tenant_id UNIQUE (tenant_id, knowledge_document_id),
    CONSTRAINT ck_knowledge_document_type CHECK (
        knowledge_type IN ('JOB_KNOWLEDGE', 'TALENT_PROFILE', 'POLICY_PROCESS', 'EVALUATION_STANDARD')
    ),
    CONSTRAINT ck_knowledge_document_classification CHECK (
        classification IN ('INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_knowledge_document_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'DISABLED', 'ARCHIVED')
    )
);

CREATE INDEX ix_knowledge_document_tenant_status_updated
    ON knowledge_document (tenant_id, status, updated_at);

CREATE INDEX ix_knowledge_document_tenant_type_updated
    ON knowledge_document (tenant_id, knowledge_type, updated_at);

CREATE TABLE knowledge_version (
    knowledge_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    knowledge_document_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 104857600),
    sha256 CHAR(64) NOT NULL,
    content_hash CHAR(64),
    content_text TEXT,
    publication_status VARCHAR(20) NOT NULL,
    parse_status VARCHAR(20) NOT NULL,
    index_status VARCHAR(20) NOT NULL,
    parser_version VARCHAR(100),
    effective_from TIMESTAMP WITH TIME ZONE,
    effective_to TIMESTAMP WITH TIME ZONE,
    change_summary VARCHAR(2000),
    approval_checkpoint_id UUID,
    approval_checkpoint_version BIGINT,
    approved_by_user_id UUID,
    approved_by_display_name VARCHAR(120),
    approved_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_knowledge_version_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_knowledge_version_document FOREIGN KEY (tenant_id, knowledge_document_id)
        REFERENCES knowledge_document (tenant_id, knowledge_document_id),
    CONSTRAINT uq_knowledge_version_tenant_id UNIQUE (tenant_id, knowledge_version_id),
    CONSTRAINT uq_knowledge_version_document_no UNIQUE (tenant_id, knowledge_document_id, version_no),
    CONSTRAINT ck_knowledge_version_publication CHECK (
        publication_status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'DISABLED')
    ),
    CONSTRAINT ck_knowledge_version_parse CHECK (
        parse_status IN ('UPLOADED', 'PARSING', 'PARSED', 'PARSE_FAILED')
    ),
    CONSTRAINT ck_knowledge_version_index CHECK (
        index_status IN ('NOT_INDEXED', 'INDEXING', 'INDEXED', 'INDEX_FAILED')
    )
);

ALTER TABLE knowledge_document
    ADD CONSTRAINT fk_knowledge_document_current_version
    FOREIGN KEY (tenant_id, current_version_id)
    REFERENCES knowledge_version (tenant_id, knowledge_version_id);

CREATE INDEX ix_knowledge_version_document_created
    ON knowledge_version (tenant_id, knowledge_document_id, version_no);

CREATE INDEX ix_knowledge_version_retrieval
    ON knowledge_version (tenant_id, publication_status, parse_status, index_status, effective_from, effective_to);

CREATE TABLE knowledge_chunk (
    knowledge_chunk_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    knowledge_version_id UUID NOT NULL,
    chunk_no INTEGER NOT NULL CHECK (chunk_no > 0),
    chunk_text TEXT NOT NULL,
    quote_hash CHAR(64) NOT NULL,
    start_offset INTEGER NOT NULL CHECK (start_offset >= 0),
    end_offset INTEGER NOT NULL CHECK (end_offset >= start_offset),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_knowledge_chunk_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_knowledge_chunk_version FOREIGN KEY (tenant_id, knowledge_version_id)
        REFERENCES knowledge_version (tenant_id, knowledge_version_id),
    CONSTRAINT uq_knowledge_chunk_tenant_id UNIQUE (tenant_id, knowledge_chunk_id),
    CONSTRAINT uq_knowledge_chunk_version_no UNIQUE (tenant_id, knowledge_version_id, chunk_no)
);

CREATE INDEX ix_knowledge_chunk_version
    ON knowledge_chunk (tenant_id, knowledge_version_id, chunk_no);

CREATE TABLE knowledge_upload_session (
    knowledge_upload_session_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    knowledge_document_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 104857600),
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_text TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_version_id UUID,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_knowledge_upload_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_knowledge_upload_document FOREIGN KEY (tenant_id, knowledge_document_id)
        REFERENCES knowledge_document (tenant_id, knowledge_document_id),
    CONSTRAINT fk_knowledge_upload_version FOREIGN KEY (tenant_id, completed_version_id)
        REFERENCES knowledge_version (tenant_id, knowledge_version_id),
    CONSTRAINT uq_knowledge_upload_tenant_id UNIQUE (tenant_id, knowledge_upload_session_id),
    CONSTRAINT uq_knowledge_upload_object_key UNIQUE (tenant_id, object_key),
    CONSTRAINT ck_knowledge_upload_status CHECK (
        status IN ('CREATED', 'UPLOADED', 'COMPLETED', 'EXPIRED', 'FAILED')
    )
);

CREATE INDEX ix_knowledge_upload_expiry
    ON knowledge_upload_session (tenant_id, status, expires_at);

CREATE TABLE knowledge_command (
    knowledge_command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    resource_id UUID,
    operation VARCHAR(80) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    response_type VARCHAR(80) NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_knowledge_command_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_knowledge_command_key UNIQUE (tenant_id, operation, idempotency_key)
);

CREATE INDEX ix_knowledge_command_resource
    ON knowledge_command (tenant_id, resource_id, created_at);
