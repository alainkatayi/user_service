CREATE TABLE app_user (
                          id                    UUID PRIMARY KEY,
                          email                 VARCHAR(150) NOT NULL UNIQUE,
                          password_hash         VARCHAR(100) NOT NULL,
                          first_name            VARCHAR(100) NOT NULL,
                          last_name             VARCHAR(100) NOT NULL,
                          phone                 VARCHAR(20)  NOT NULL UNIQUE,
                          status                VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
                          failed_login_attempts INT          NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
                          locked_until          TIMESTAMPTZ,
                          version               BIGINT       NOT NULL DEFAULT 0,
                          created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          CHECK (email = lower(email)),
                          CHECK (phone ~ '^\+[1-9][0-9]{7,14}$')
    );

CREATE TABLE refresh_token (
                               id          UUID PRIMARY KEY,
                               user_id     UUID        NOT NULL REFERENCES app_user(id),
                               token_hash  CHAR(64)    NOT NULL UNIQUE,
                               expires_at  TIMESTAMPTZ NOT NULL,
                               revoked_at  TIMESTAMPTZ,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);