CREATE TABLE candidate (
    candidate_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    candidate_no VARCHAR(60) NOT NULL,
    display_name_ciphertext TEXT NOT NULL,
    consent_status VARCHAR(20) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    external_candidate_id VARCHAR(256) NOT NULL,
    external_version VARCHAR(128),
    connector_id UUID NOT NULL,
    source_application_ref_json TEXT,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_candidate_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_candidate_tenant_id UNIQUE (tenant_id, candidate_id),
    CONSTRAINT uq_candidate_no UNIQUE (tenant_id, candidate_no),
    CONSTRAINT uq_candidate_external UNIQUE (tenant_id, source_system, external_candidate_id),
    CONSTRAINT ck_candidate_consent CHECK (consent_status IN ('UNKNOWN', 'GRANTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_candidate_source_type CHECK (source_type IN ('ATS_APPLICATION', 'TALENT_POOL', 'MANUAL_IMPORT'))
);

CREATE INDEX ix_candidate_tenant_connector
    ON candidate (tenant_id, connector_id, updated_at);

CREATE TABLE resume_version (
    resume_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    source_version VARCHAR(128) NOT NULL,
    normalized_input_ciphertext TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    normalizer_kind VARCHAR(80) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_resume_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_resume_candidate FOREIGN KEY (tenant_id, candidate_id)
        REFERENCES candidate (tenant_id, candidate_id),
    CONSTRAINT uq_resume_tenant_id UNIQUE (tenant_id, resume_version_id),
    CONSTRAINT uq_resume_candidate_version UNIQUE (tenant_id, candidate_id, version_no),
    CONSTRAINT uq_resume_source_version UNIQUE (tenant_id, candidate_id, source_version),
    CONSTRAINT ck_resume_normalizer_kind CHECK (normalizer_kind IN ('DETERMINISTIC_NORMALIZER'))
);

CREATE INDEX ix_resume_tenant_candidate_updated
    ON resume_version (tenant_id, candidate_id, source_updated_at);

CREATE TABLE candidate_input_command (
    candidate_input_command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_ciphertext TEXT NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_candidate_input_command_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_candidate_input_command_key UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE task_candidate (
    task_candidate_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    selection_status VARCHAR(32) NOT NULL,
    current_match_result_id UUID,
    candidate_list_version_id UUID,
    source_type VARCHAR(32) NOT NULL,
    source_application_ref_json TEXT,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_task_candidate_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_task_candidate_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_task_candidate_candidate FOREIGN KEY (tenant_id, candidate_id)
        REFERENCES candidate (tenant_id, candidate_id),
    CONSTRAINT uq_task_candidate_tenant_id UNIQUE (tenant_id, task_candidate_id),
    CONSTRAINT uq_task_candidate_task_person UNIQUE (tenant_id, recruitment_task_id, candidate_id),
    CONSTRAINT ck_task_candidate_status CHECK (
        status IN ('DISCOVERED', 'UNDER_REVIEW', 'SELECTED', 'INTERVIEWING', 'EVALUATED', 'CLOSED')
    ),
    CONSTRAINT ck_task_candidate_selection CHECK (
        selection_status IN ('UNREVIEWED', 'SHORTLISTED', 'CONFIRMED', 'EXCLUDED')
    )
);

CREATE INDEX ix_task_candidate_task_status
    ON task_candidate (tenant_id, recruitment_task_id, status, updated_at);

CREATE TABLE match_run (
    match_run_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    position_plan_version_id UUID NOT NULL,
    scorecard_version_id UUID NOT NULL,
    match_run_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    generator_kind VARCHAR(80) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_match_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_match_run_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_match_run_plan FOREIGN KEY (tenant_id, position_plan_version_id)
        REFERENCES position_plan_version (tenant_id, position_plan_version_id),
    CONSTRAINT uq_match_run_tenant_id UNIQUE (tenant_id, match_run_id),
    CONSTRAINT uq_match_run_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_match_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_match_run_generator_kind CHECK (generator_kind IN ('DETERMINISTIC_RULES'))
);

CREATE INDEX ix_match_run_tenant_task_created
    ON match_run (tenant_id, recruitment_task_id, created_at);

CREATE TABLE match_result (
    match_result_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    match_run_id UUID NOT NULL,
    task_candidate_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    resume_version_id UUID NOT NULL,
    result_ciphertext TEXT NOT NULL,
    total_score DECIMAL(7, 4) NOT NULL CHECK (total_score >= 0 AND total_score <= 100),
    result_rank INTEGER NOT NULL CHECK (result_rank > 0),
    generator_kind VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_match_result_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_match_result_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES match_run (tenant_id, match_run_id),
    CONSTRAINT fk_match_result_task_candidate FOREIGN KEY (tenant_id, task_candidate_id)
        REFERENCES task_candidate (tenant_id, task_candidate_id),
    CONSTRAINT fk_match_result_candidate FOREIGN KEY (tenant_id, candidate_id)
        REFERENCES candidate (tenant_id, candidate_id),
    CONSTRAINT fk_match_result_resume FOREIGN KEY (tenant_id, resume_version_id)
        REFERENCES resume_version (tenant_id, resume_version_id),
    CONSTRAINT uq_match_result_tenant_id UNIQUE (tenant_id, match_result_id),
    CONSTRAINT uq_match_result_run_candidate UNIQUE (tenant_id, match_run_id, task_candidate_id),
    CONSTRAINT uq_match_result_run_rank UNIQUE (tenant_id, match_run_id, result_rank),
    CONSTRAINT ck_match_result_generator_kind CHECK (generator_kind IN ('DETERMINISTIC_RULES'))
);

CREATE INDEX ix_match_result_run_score
    ON match_result (tenant_id, match_run_id, total_score, result_rank);

ALTER TABLE task_candidate
    ADD CONSTRAINT fk_task_candidate_current_result
    FOREIGN KEY (tenant_id, current_match_result_id)
    REFERENCES match_result (tenant_id, match_result_id);
