-- Notificações do painel: uma linha por destinatário, para que cada um tenha
-- o próprio estado de leitura.
-- Sem coluna de texto: o front-end monta a mensagem a partir do tipo e das
-- referências. Assim o texto não fica desatualizado e o conteúdo dos
-- comentários (que será cifrado) não é copiado para cá.
-- A categoria usada no filtro do painel é derivada do tipo no código.
CREATE TABLE notifications (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id          UUID        NOT NULL REFERENCES users (id),
    type                  VARCHAR(40) NOT NULL,
    actor_id              UUID REFERENCES users (id),
    ticket_id             UUID REFERENCES tickets (id),
    department_request_id UUID REFERENCES department_requests (id),
    read_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'TICKET_ASSIGNED',
        'TICKET_COMMENTED',
        'TICKET_CLOSED',
        'DEPARTMENT_REQUEST_CREATED',
        'DEPARTMENT_REQUEST_APPROVED',
        'DEPARTMENT_REQUEST_REJECTED',
        'ADMIN_GRANTED'
    ))
);

-- Listagem do painel, da mais recente para a mais antiga
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

-- Contador de não lidas
CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id)
    WHERE read_at IS NULL;

CREATE INDEX idx_notifications_ticket ON notifications (ticket_id);
CREATE INDEX idx_notifications_department_request ON notifications (department_request_id);
