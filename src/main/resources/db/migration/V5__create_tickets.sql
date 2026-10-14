CREATE TABLE tickets (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(120) NOT NULL,
    description   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    category_id   UUID         NOT NULL REFERENCES categories (id),
    priority_id   SMALLINT     NOT NULL REFERENCES priorities (id),
    requester_id  UUID         NOT NULL REFERENCES users (id),
    assignee_id   UUID         REFERENCES users (id),
    assigned_at   TIMESTAMPTZ,
    closed_by_id  UUID         REFERENCES users (id),
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ
);

CREATE INDEX idx_tickets_requester_id    ON tickets (requester_id);
CREATE INDEX idx_tickets_assignee_status ON tickets (assignee_id, status);
CREATE INDEX idx_tickets_category_id     ON tickets (category_id);
CREATE INDEX idx_tickets_priority_id     ON tickets (priority_id);