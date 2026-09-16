-- V1__create_auth_schema.sql
-- All TIMESTAMPTZ values are UTC by definition.
-- Java code must use Instant.now() or ZonedDateTime.now(ZoneOffset.UTC) — never LocalDateTime.now().

CREATE TABLE users (
  id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  email                 VARCHAR(255) NOT NULL,
  email_normalized      VARCHAR(255) NOT NULL,
  password_hash         VARCHAR(255) NOT NULL,
  name                  VARCHAR(255) NOT NULL,
  role                  VARCHAR(20)  NOT NULL DEFAULT 'USER'
                          CHECK (role IN ('USER','ADMIN')),
  status                VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED'
                          CHECK (status IN ('UNVERIFIED','ACTIVE','DISABLED','PENDING_DELETION','DELETED')),
  failed_login_count    INT          NOT NULL DEFAULT 0,
  locked_until          TIMESTAMPTZ,
  deletion_requested_at TIMESTAMPTZ,
  version               INT          NOT NULL DEFAULT 0,
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email_normalized
  ON users(email_normalized) WHERE status <> 'DELETED';

CREATE TABLE refresh_tokens (
  id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  family_id              UUID         NOT NULL,
  token_hash             VARCHAR(255) NOT NULL,      -- SHA-256(raw token hex); raw token never stored
  expires_at             TIMESTAMPTZ  NOT NULL,
  revoked_at             TIMESTAMPTZ,
  superseded_at          TIMESTAMPTZ,               -- set at rotation; grace window = superseded_at + 10s
  replaced_by            UUID         REFERENCES refresh_tokens(id),
  grace_window_encrypted TEXT,                      -- AES-256-GCM ciphertext of {"r":<rawToken>,"j":<signedJwt>}
                                                    -- written to SUPERSEDED row at rotation; enables
                                                    -- byte-identical grace-window responses; valid only
                                                    -- while superseded_at + 10s > now(); null on rows
                                                    -- that have never been superseded
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ip_address             INET,
  user_agent             VARCHAR(500)
);
CREATE INDEX        idx_refresh_tokens_user   ON refresh_tokens(user_id);
CREATE UNIQUE INDEX uq_refresh_tokens_hash    ON refresh_tokens(token_hash);
CREATE INDEX        idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE auth_tokens (
  id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_type  VARCHAR(20)  NOT NULL CHECK (token_type IN ('EMAIL_VERIFY','PASSWORD_RESET')),
  token_hash  VARCHAR(255) NOT NULL,
  expires_at  TIMESTAMPTZ  NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_auth_tokens_hash  ON auth_tokens(token_hash);
CREATE INDEX idx_auth_tokens_user_type   ON auth_tokens(user_id, token_type);
