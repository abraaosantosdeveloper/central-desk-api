CREATE TABLE admins (
    user_id        UUID        PRIMARY KEY REFERENCES users (id),
    granted_by_id  UUID        REFERENCES users (id),
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);