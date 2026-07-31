CREATE TABLE tenant (
    tenant_id UUID PRIMARY KEY,
    tenant_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system'
);

CREATE TABLE external_identity (
    external_identity_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider VARCHAR(80) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    display_name VARCHAR(200),
    email VARCHAR(320),
    status VARCHAR(32) NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_external_identity_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_external_identity_subject UNIQUE (tenant_id, provider, subject),
    CONSTRAINT uq_external_identity_tenant_id UNIQUE (tenant_id, external_identity_id)
);

CREATE INDEX ix_external_identity_tenant_status
    ON external_identity (tenant_id, status);

CREATE TABLE embed_client (
    embed_client_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    client_key VARCHAR(120) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    allowed_origins TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_embed_client_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_embed_client_key UNIQUE (tenant_id, client_key),
    CONSTRAINT uq_embed_client_tenant_id UNIQUE (tenant_id, embed_client_id)
);

CREATE TABLE embed_session (
    embed_session_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    embed_client_id UUID NOT NULL,
    external_identity_id UUID NOT NULL,
    bootstrap_token_hash CHAR(64) NOT NULL UNIQUE,
    protocol_version VARCHAR(20) NOT NULL,
    parent_origin VARCHAR(2048) NOT NULL,
    context_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_embed_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_embed_session_client FOREIGN KEY (tenant_id, embed_client_id)
        REFERENCES embed_client (tenant_id, embed_client_id),
    CONSTRAINT fk_embed_session_identity FOREIGN KEY (tenant_id, external_identity_id)
        REFERENCES external_identity (tenant_id, external_identity_id)
);

CREATE INDEX ix_embed_session_tenant_status_expiry
    ON embed_session (tenant_id, status, expires_at);

CREATE TABLE audit_event (
    audit_event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    tenant_sequence BIGINT NOT NULL CHECK (tenant_sequence > 0),
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    action VARCHAR(120) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    request_id UUID NOT NULL,
    payload TEXT,
    previous_hash CHAR(64),
    event_hash CHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_audit_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT uq_audit_event_tenant_sequence UNIQUE (tenant_id, tenant_sequence),
    CONSTRAINT uq_audit_event_tenant_hash UNIQUE (tenant_id, event_hash)
);

CREATE INDEX ix_audit_event_tenant_occurred
    ON audit_event (tenant_id, occurred_at);

CREATE INDEX ix_audit_event_trace
    ON audit_event (tenant_id, trace_id, occurred_at);

CREATE TABLE outbox_event (
    outbox_event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    headers TEXT,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_outbox_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
);

CREATE INDEX ix_outbox_event_delivery
    ON outbox_event (status, available_at, created_at);

CREATE INDEX ix_outbox_event_tenant_aggregate
    ON outbox_event (tenant_id, aggregate_type, aggregate_id);
