ALTER TABLE human_checkpoint ALTER COLUMN requirement_draft_id DROP NOT NULL;
ALTER TABLE human_checkpoint ALTER COLUMN decided_by_user_id DROP NOT NULL;
ALTER TABLE human_checkpoint ALTER COLUMN decided_by_display_name DROP NOT NULL;
ALTER TABLE human_checkpoint ALTER COLUMN decided_at DROP NOT NULL;

ALTER TABLE human_checkpoint ADD COLUMN resource_type VARCHAR(80);
ALTER TABLE human_checkpoint ADD COLUMN resource_id UUID;
ALTER TABLE human_checkpoint ADD COLUMN resource_version BIGINT;
ALTER TABLE human_checkpoint ADD COLUMN required_role VARCHAR(80);
ALTER TABLE human_checkpoint ADD COLUMN assignee_user_id UUID;
ALTER TABLE human_checkpoint ADD COLUMN summary VARCHAR(2000);
ALTER TABLE human_checkpoint ADD COLUMN requested_by_user_id UUID;
ALTER TABLE human_checkpoint ADD COLUMN requested_by_display_name VARCHAR(120);
ALTER TABLE human_checkpoint ADD COLUMN requested_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE human_checkpoint ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE human_checkpoint ADD COLUMN decision VARCHAR(20);

ALTER TABLE human_checkpoint ADD CONSTRAINT uq_human_checkpoint_resource_gate
    UNIQUE (tenant_id, resource_id, resource_version, checkpoint_type);

CREATE TABLE agent_run (
    agent_run_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    run_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    workflow_version VARCHAR(80) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    generator_kind VARCHAR(80) NOT NULL,
    result_resource_id UUID,
    failure_code VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_agent_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_agent_run_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT uq_agent_run_tenant_id UNIQUE (tenant_id, agent_run_id),
    CONSTRAINT ck_agent_run_generator_kind CHECK (generator_kind IN ('DETERMINISTIC_DEMO'))
);

CREATE INDEX ix_agent_run_tenant_task_created
    ON agent_run (tenant_id, recruitment_task_id, created_at);

CREATE TABLE position_plan_version (
    position_plan_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    status VARCHAR(32) NOT NULL,
    plan_json TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    based_on_run_id UUID NOT NULL,
    generator_kind VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    approved_by_user_id UUID,
    approved_by_display_name VARCHAR(120),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL,
    CONSTRAINT fk_position_plan_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_position_plan_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT fk_position_plan_run FOREIGN KEY (tenant_id, based_on_run_id)
        REFERENCES agent_run (tenant_id, agent_run_id),
    CONSTRAINT uq_position_plan_tenant_id UNIQUE (tenant_id, position_plan_version_id),
    CONSTRAINT uq_position_plan_task_version UNIQUE (tenant_id, recruitment_task_id, version_no),
    CONSTRAINT ck_position_plan_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SUPERSEDED')),
    CONSTRAINT ck_position_plan_generator_kind CHECK (generator_kind IN ('DETERMINISTIC_DEMO'))
);

CREATE INDEX ix_position_plan_tenant_task_status
    ON position_plan_version (tenant_id, recruitment_task_id, status, version_no);

CREATE TABLE position_plan_command (
    position_plan_command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recruitment_task_id UUID NOT NULL,
    resource_id UUID NOT NULL,
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
    CONSTRAINT fk_position_plan_command_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_position_plan_command_task FOREIGN KEY (tenant_id, recruitment_task_id)
        REFERENCES recruitment_task (tenant_id, recruitment_task_id),
    CONSTRAINT uq_position_plan_command_key UNIQUE (tenant_id, operation, idempotency_key)
);

CREATE INDEX ix_position_plan_command_resource
    ON position_plan_command (tenant_id, resource_id, created_at);
