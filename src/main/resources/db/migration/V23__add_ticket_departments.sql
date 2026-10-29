-- SCRUM-213 (DECISOES-PAINEIS-METRICAS-BI §2, D1 e D2): setor de origem e setor de destino no chamado.
-- Origem: o departamento de quem abriu, no momento da abertura (fato do chamado, nunca alterado).
-- Destino: o setor que recebe o chamado; hoje sempre a Inovação (a escolha na abertura é a SCRUM-148).
ALTER TABLE tickets
    ADD COLUMN origin_department_id UUID REFERENCES departments (id),
    ADD COLUMN target_department_id UUID REFERENCES departments (id);

-- Povoamento (D1): origem = departamento atual do solicitante; destino = Inovação (UUID fixo da V8).
-- Inconsistência aceita: sistema novo, ninguém mudou de setor até aqui.
UPDATE tickets t
   SET origin_department_id = u.department_id
  FROM users u
 WHERE u.id = t.requester_id;

UPDATE tickets SET target_department_id = 'e9790f1f-41df-4369-bfe7-1be4be535561';

ALTER TABLE tickets
    ALTER COLUMN origin_department_id SET NOT NULL,
    ALTER COLUMN target_department_id SET NOT NULL,
    ALTER COLUMN target_department_id SET DEFAULT 'e9790f1f-41df-4369-bfe7-1be4be535561';

-- Quem grava sem informar a origem (scripts, inserts manuais) recebe o departamento do solicitante na hora
-- da abertura, a mesma regra da aplicação. Só preenche: a imutabilidade no banco segue em aberto (D2).
CREATE FUNCTION tickets_fill_origin_department() RETURNS trigger AS $$
BEGIN
    IF NEW.origin_department_id IS NULL THEN
        SELECT department_id INTO NEW.origin_department_id FROM users WHERE id = NEW.requester_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tickets_fill_origin_department
    BEFORE INSERT ON tickets
    FOR EACH ROW EXECUTE FUNCTION tickets_fill_origin_department();

CREATE INDEX idx_tickets_origin_status ON tickets (origin_department_id, status);
CREATE INDEX idx_tickets_target_status ON tickets (target_department_id, status);
CREATE INDEX idx_tickets_created_at    ON tickets (created_at);
