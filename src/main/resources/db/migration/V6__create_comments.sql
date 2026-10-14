CREATE TABLE comments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content     TEXT        NOT NULL,
    user_id     UUID        NOT NULL REFERENCES users (id),
    ticket_id   UUID        NOT NULL REFERENCES tickets (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_comments_ticket_created ON comments (ticket_id, created_at);
CREATE INDEX idx_comments_user_id        ON comments (user_id);