-- =============================================================================
-- V8 - Departamentos obrigatórios (Inovação e Geral)
--
-- Os UUIDs são fixos porque a aplicação os referencia por configuração:
--   app.tickets.handler-department-id     = e9790f1f-41df-4369-bfe7-1be4be535561 (Inovação)
--   app.departments.default-department-id = 3094ed3f-42ab-4bc0-9d8d-f6eb08891016 (Geral)
--
-- O primeiro administrador NÃO é criado aqui: dados pessoais e senha não ficam
-- em migration versionada. Ele é criado pela aplicação ao subir (AdminBootstrap),
-- a partir das variáveis ADMIN_NAME, ADMIN_CPF, ADMIN_EMAIL e ADMIN_PASSWORD.
-- =============================================================================

INSERT INTO departments (id, name) VALUES
    ('e9790f1f-41df-4369-bfe7-1be4be535561', 'Inovação'),
    ('3094ed3f-42ab-4bc0-9d8d-f6eb08891016', 'Geral');

-- Demais departamentos da empresa (opcional). Descomente e ajuste os nomes
-- ANTES de aplicar; depois de aplicada, use uma nova migration:
-- INSERT INTO departments (name) VALUES
--     ('Financeiro'),
--     ('Contábil'),
--     ('Fiscal'),
--     ('Recursos Humanos');
