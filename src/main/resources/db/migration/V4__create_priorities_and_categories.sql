CREATE TABLE categories (
    id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    label  VARCHAR(60) NOT NULL UNIQUE,
    color  CHAR(7)     NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

-- O id define a ordem de urgência (1 = menor, 4 = maior)
CREATE TABLE priorities (
    id     SMALLINT    PRIMARY KEY,
    label  VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO priorities (id, label) VALUES
    (1, 'Baixa'),
    (2, 'Média'),
    (3, 'Alta'),
    (4, 'Crítica');