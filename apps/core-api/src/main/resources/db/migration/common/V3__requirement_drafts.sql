CREATE TABLE requirement_draft (
    requirement_draft_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    raw_input_ciphertext TEXT NOT NULL,
    fields_json TEXT NOT NULL,
    source_job_ref_json TEXT,
    host_context_hash CHAR(64),
    created_by_user_id UUID NOT NULL,
    created_by_display_name VARCHAR(120) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    converted_task_id UUID,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    create_response_ciphertext TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_requirement_draft_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_requirement_draft_tenant_id UNIQUE (tenant_id, requirement_draft_id),
    CONSTRAINT uq_requirement_draft_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_requirement_draft_status CHECK (status IN ('DRAFT', 'READY', 'CONVERTED', 'ABANDONED', 'EXPIRED')),
    CONSTRAINT ck_requirement_draft_context_hash CHECK (
        host_context_hash IS NULL OR LENGTH(host_context_hash) = 64
    )
);

CREATE INDEX ix_requirement_draft_tenant_status_updated
    ON requirement_draft (tenant_id, status, updated_at);

CREATE TABLE recruitment_task (
    recruitment_task_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_draft_id UUID NOT NULL,
    task_no VARCHAR(40) NOT NULL,
    task_json TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_recruitment_task_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_recruitment_task_source_draft FOREIGN KEY (tenant_id, source_draft_id)
        REFERENCES requirement_draft (tenant_id, requirement_draft_id),
    CONSTRAINT uq_recruitment_task_tenant_id UNIQUE (tenant_id, recruitment_task_id),
    CONSTRAINT uq_recruitment_task_no UNIQUE (tenant_id, task_no),
    CONSTRAINT uq_recruitment_task_source_draft UNIQUE (tenant_id, source_draft_id)
);

CREATE INDEX ix_recruitment_task_tenant_updated
    ON recruitment_task (tenant_id, updated_at);

ALTER TABLE requirement_draft
    ADD CONSTRAINT fk_requirement_draft_converted_task
    FOREIGN KEY (tenant_id, converted_task_id)
    REFERENCES recruitment_task (tenant_id, recruitment_task_id);

CREATE TABLE human_checkpoint (
    human_checkpoint_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    requirement_draft_id UUID NOT NULL,
    checkpoint_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    comment TEXT,
    decided_by_user_id UUID NOT NULL,
    decided_by_display_name VARCHAR(120) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_human_checkpoint_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_human_checkpoint_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_human_checkpoint_draft FOREIGN KEY (tenant_id, requirement_draft_id)
        REFERENCES requirement_draft (tenant_id, requirement_draft_id),
    CONSTRAINT uq_human_checkpoint_tenant_id UNIQUE (tenant_id, human_checkpoint_id),
    CONSTRAINT uq_human_checkpoint_create_task UNIQUE (tenant_id, requirement_draft_id, checkpoint_type)
);

CREATE TABLE requirement_draft_command (
    requirement_draft_command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    requirement_draft_id UUID NOT NULL,
    operation VARCHAR(40) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_ciphertext TEXT NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_requirement_draft_command_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_requirement_draft_command_draft FOREIGN KEY (tenant_id, requirement_draft_id)
        REFERENCES requirement_draft (tenant_id, requirement_draft_id),
    CONSTRAINT uq_requirement_draft_command_key UNIQUE (tenant_id, operation, idempotency_key)
);

CREATE INDEX ix_requirement_draft_command_draft
    ON requirement_draft_command (tenant_id, requirement_draft_id, created_at);
