-- V3__create_applications_schema.sql
-- Job Application Tracker domain table.
-- Status lifecycle enforced at service layer (api-spec.md §3, system-spec.md §7).
-- version column supports JPA @Version optimistic locking.

CREATE TABLE applications (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company      VARCHAR(255) NOT NULL,
  role_title   VARCHAR(255) NOT NULL,
  jd_text      TEXT,
  status       VARCHAR(20) NOT NULL DEFAULT 'APPLIED'
                 CHECK (status IN ('APPLIED','INTERVIEWING','OFFER','REJECTED','WITHDRAWN')),
  applied_date DATE NOT NULL DEFAULT CURRENT_DATE,
  is_deleted   BOOLEAN NOT NULL DEFAULT false,
  version      INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_user_status
  ON applications(user_id, status) WHERE is_deleted = false;

CREATE INDEX idx_applications_user_created
  ON applications(user_id, created_at DESC) WHERE is_deleted = false;
