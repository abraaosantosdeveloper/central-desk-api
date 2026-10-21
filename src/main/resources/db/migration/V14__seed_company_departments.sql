-- SCRUM-170: demais departamentos da empresa, listados no cadastro.
-- Mudanças futuras de nomes ou novos departamentos entram em novas migrations.
INSERT INTO departments (name) VALUES
    ('Administrativo'),
    ('Comercial'),
    ('Contábil'),
    ('Departamento Pessoal'),
    ('Financeiro'),
    ('Fiscal'),
    ('Recursos Humanos'),
    ('Societário')
ON CONFLICT (name) DO NOTHING;
