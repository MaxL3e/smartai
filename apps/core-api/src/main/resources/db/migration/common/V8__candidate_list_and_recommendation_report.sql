CREATE TABLE candidate_list_preview (
    candidate_list_preview_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    match_run_id UUID NOT NULL,
    preview_ciphertext TEXT NOT NULL,
    input_hash CHAR(64) NOT NULL,
    task_version BIGINT NOT NULL CHECK (task_version > 0),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_candidate_list_preview_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_candidate_list_preview_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_candidate_list_preview_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES match_run (tenant_id, match_run_id),
    CONSTRAINT uq_candidate_list_preview_tenant_id UNIQUE (tenant_id, candidate_list_preview_id)
);

CREATE INDEX ix_candidate_list_preview_task_created
    ON candidate_list_preview (tenant_id, recruitment_task_id, created_at);

CREATE TABLE candidate_list_version (
    candidate_list_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    candidate_list_preview_id UUID NOT NULL,
    match_run_id UUID NOT NULL,
    checkpoint_id UUID NOT NULL,
    list_ciphertext TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_by_user_id UUID NOT NULL,
    confirmed_by_display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_candidate_list_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_candidate_list_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_candidate_list_preview FOREIGN KEY (tenant_id, candidate_list_preview_id)
        REFERENCES candidate_list_preview (tenant_id, candidate_list_preview_id),
    CONSTRAINT fk_candidate_list_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES match_run (tenant_id, match_run_id),
    CONSTRAINT fk_candidate_list_checkpoint FOREIGN KEY (tenant_id, checkpoint_id)
        REFERENCES human_checkpoint (tenant_id, human_checkpoint_id),
    CONSTRAINT uq_candidate_list_tenant_id UNIQUE (tenant_id, candidate_list_version_id),
    CONSTRAINT uq_candidate_list_task_version UNIQUE (tenant_id, recruitment_task_id, version_no),
    CONSTRAINT uq_candidate_list_checkpoint UNIQUE (tenant_id, checkpoint_id)
);

CREATE INDEX ix_candidate_list_task_version
    ON candidate_list_version (tenant_id, recruitment_task_id, version_no);

ALTER TABLE task_candidate
    ADD CONSTRAINT fk_task_candidate_list_version
    FOREIGN KEY (tenant_id, candidate_list_version_id)
    REFERENCES candidate_list_version (tenant_id, candidate_list_version_id);

CREATE TABLE recommendation_report_version (
    recommendation_report_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    candidate_list_version_id UUID NOT NULL,
    position_plan_version_id UUID NOT NULL,
    scorecard_version_id UUID NOT NULL,
    match_run_id UUID NOT NULL,
    report_ciphertext TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_by_user_id UUID NOT NULL,
    generated_by_display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_recommendation_report_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_recommendation_report_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_recommendation_report_list FOREIGN KEY (tenant_id, candidate_list_version_id)
        REFERENCES candidate_list_version (tenant_id, candidate_list_version_id),
    CONSTRAINT fk_recommendation_report_plan FOREIGN KEY (tenant_id, position_plan_version_id)
        REFERENCES position_plan_version (tenant_id, position_plan_version_id),
    CONSTRAINT fk_recommendation_report_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES match_run (tenant_id, match_run_id),
    CONSTRAINT uq_recommendation_report_tenant_id UNIQUE (tenant_id, recommendation_report_version_id),
    CONSTRAINT uq_recommendation_report_task_version UNIQUE (tenant_id, recruitment_task_id, version_no),
    CONSTRAINT uq_recommendation_report_list UNIQUE (tenant_id, candidate_list_version_id)
);

CREATE INDEX ix_recommendation_report_task_version
    ON recommendation_report_version (tenant_id, recruitment_task_id, version_no);
