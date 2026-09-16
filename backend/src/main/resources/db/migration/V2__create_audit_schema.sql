-- V2__create_audit_schema.sql
CREATE TABLE audit_log (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID,                                   -- nullable: nulled on account deletion
  entity_type VARCHAR(50) NOT NULL,
  entity_id   UUID        NOT NULL,
  action      VARCHAR(30) NOT NULL
                CHECK (action IN ('CREATE','UPDATE','DELETE','STATUS_CHANGE','LOGIN','LOGOUT')),
  before_json JSONB,
  after_json  JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user   ON audit_log(user_id, created_at DESC);
