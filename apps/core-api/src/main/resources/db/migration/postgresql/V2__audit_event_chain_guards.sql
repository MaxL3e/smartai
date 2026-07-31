CREATE FUNCTION enforce_audit_event_chain() RETURNS TRIGGER AS $$
DECLARE
    latest_sequence BIGINT;
    latest_hash CHAR(64);
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.tenant_id::text, 0));

    SELECT tenant_sequence, event_hash
      INTO latest_sequence, latest_hash
      FROM audit_event
     WHERE tenant_id = NEW.tenant_id
     ORDER BY tenant_sequence DESC
     LIMIT 1;

    IF latest_sequence IS NULL THEN
        IF NEW.tenant_sequence <> 1 OR NEW.previous_hash IS NOT NULL THEN
            RAISE EXCEPTION 'first audit event must have sequence 1 and no previous hash';
        END IF;
    ELSIF NEW.tenant_sequence <> latest_sequence + 1 THEN
        RAISE EXCEPTION 'audit event sequence must immediately follow the latest tenant event';
    ELSIF NEW.previous_hash IS DISTINCT FROM latest_hash THEN
        RAISE EXCEPTION 'audit event previous hash must match the latest tenant event hash';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_chain_insert_guard
    BEFORE INSERT ON audit_event
    FOR EACH ROW EXECUTE FUNCTION enforce_audit_event_chain();

CREATE FUNCTION reject_audit_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_update_delete_guard
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
