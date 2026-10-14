# Sistema de Chamados — Contexto do Projeto

Este documento resume o estado do projeto e as decisões já tomadas. Ele serve como contexto para continuar o desenvolvimento em novas conversas: **as decisões listadas aqui já foram discutidas e não precisam ser reabertas**, a menos que eu peça.

---

## 1. Visão geral

Sistema web de chamados (*tickets*) internos da empresa. Hoje os pedidos de suporte chegam por e-mail, o que torna o atendimento lento e desorganizado. O sistema oferece um painel onde os colaboradores abrem chamados, organizados por **categoria**, **prioridade** e **solicitante**, e onde a equipe de suporte os atende.

- Atende **todo tipo de suporte**, não só manutenção física.
- **Somente o departamento de Inovação atende chamados** (equipe de 3 pessoas, com um coordenador).
- Notificações desejadas: por e-mail (domínio da empresa) e dentro da interface. **Ainda não modeladas.**
- O projeto também tem uma **documentação formal em ABNT, escrita no Overleaf**.

## 2. Stack e ambiente

| Camada | Tecnologia |
|---|---|
| Front-end | Vue.js |
| Back-end | Java + Spring Boot 3 (Hibernate 6, Spring Security) |
| Banco | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Administração do banco | pgAdmin (Docker) |
| Ambiente | VS Code, Ubuntu 26.04 no WSL 2 |

- Pacote base: `br.com.contabilizape.sistema_chamados`
- Classe principal: `SistemaChamadosApplication`

## 3. Como rodar

### Banco e pgAdmin

Sobem com o `docker-compose.yaml` da raiz (serviços `db` e `pgadmin`):

```bash
docker compose up -d
```

- Postgres em `localhost:5432`, banco `chamados`.
- pgAdmin em `http://localhost:5050`. Ao cadastrar o servidor no pgAdmin, o **host é `db`** (nome do serviço no compose), não `localhost`.
- Usuário e senha do banco só são aplicados na **primeira** criação do volume. Para recomeçar do zero em desenvolvimento: `docker compose down -v`.

### Variáveis de ambiente (`.env` na raiz, fora do Git)

```
DB_URL=jdbc:postgresql://localhost:5432/chamados
DB_USER=...
DB_PASSWORD=...
PGADMIN_EMAIL=...
PGADMIN_PASSWORD=...
```

O compose lê o `.env` automaticamente. O Spring lê pelo `application.properties`:

```properties
spring.config.import=optional:file:.env[.properties]
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

### Aplicação

```bash
./mvnw clean spring-boot:run
```

O Flyway aplica as migrations ao subir. Dependências necessárias: `flyway-core`, `flyway-database-postgresql` e `postgresql`.

## 4. Banco de dados

### Migrations (`src/main/resources/db/migration/`)

Formato obrigatório: `V<n>__<descricao>.sql` (dois underlines). **Depois de aplicada, uma migration não é editada**: qualquer mudança vira um novo arquivo.

| Arquivo | Conteúdo |
|---|---|
| `V1__create_departments.sql` | departamentos |
| `V2__create_users.sql` | usuários |
| `V3__create_admins.sql` | administradores |
| `V4__create_categories_and_priorities.sql` | categorias e prioridades (com as 4 prioridades inseridas) |
| `V5__create_tickets.sql` | chamados |
| `V6__create_comments.sql` | comentários |
| `V7__create_refresh_tokens.sql` | refresh tokens |

Próxima migration prevista: `V8__seed_inicial.sql`, com o departamento de Inovação e o primeiro administrador.

### Tabelas

**departments** — `id` UUID PK · `name` VARCHAR(120) UNIQUE · `created_at` · `updated_at`

**users** — `id` UUID PK · `full_name` VARCHAR(150) · `cpf` CHAR(11) UNIQUE, nulo após anonimização · `email` VARCHAR(255) UNIQUE (login) · `password_hash` VARCHAR(255) · `status` (`ACTIVE`, `DEACTIVATED`, `ANONYMIZED`) · `department_id` FK · `created_at` · `updated_at` · `validated_at` · `deactivated_at` · `deleted_at`

**admins** — `user_id` UUID PK e FK (1:1 com users) · `granted_by_id` FK nullable (o primeiro admin não tem) · `granted_at`

**categories** — `id` UUID PK · `label` VARCHAR(60) UNIQUE · `color` CHAR(7) (`#RRGGBB`, validado por CHECK). Cadastradas pelos administradores.

**priorities** — `id` SMALLINT PK · `label` VARCHAR(20) UNIQUE. Valores fixos: 1 Baixa, 2 Média, 3 Alta, 4 Crítica. O `id` define a ordem de urgência.

**tickets** — `id` UUID PK · `title` VARCHAR(120) · `description` TEXT · `status` (`OPEN`, `IN_PROGRESS`, `CLOSED`) · `category_id` FK · `priority_id` FK · `requester_id` FK · `assignee_id` FK nullable · `assigned_at` nullable · `closed_by_id` FK nullable · `closed_at` nullable · `created_at` · `updated_at`

**comments** — `id` UUID PK · `content` TEXT · `user_id` FK · `ticket_id` FK · `created_at` · `updated_at`

**refresh_tokens** — `id` UUID PK · `user_id` FK · `token_hash` CHAR(64) UNIQUE (SHA-256) · `created_at` · `expires_at` NOT NULL · `revoked_at` nullable

### Convenções do banco

- Nomes de tabelas e colunas em inglês, `snake_case`, minúsculas.
- Chaves primárias UUID com `DEFAULT gen_random_uuid()`; no Java, `@GeneratedValue(strategy = GenerationType.UUID)`.
- Datas de eventos sempre `TIMESTAMPTZ` com `DEFAULT CURRENT_TIMESTAMP` (Java: `OffsetDateTime`, `@CreationTimestamp` / `@UpdateTimestamp`).
- Valores fixos ligados ao código (status): `VARCHAR` + `CHECK`, mapeados com `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
- Valores cadastráveis (categorias, departamentos): tabela própria com FK.
- **Soft delete**: usuários não são apagados; são desativados e depois anonimizados, para preservar o histórico dos chamados.
- Índices criados em todas as FKs usadas em consultas frequentes, além de `(assignee_id, status)` em tickets e `(ticket_id, created_at)` em comments.

### Diagramas

- Modelo conceitual (notação de Chen): `modelo_conceitual_chamados.drawio`
- Modelo lógico: `modelo_logico_chamados.dbml` (para o dbdiagram.io)

## 5. Regras de negócio

### Permissões

- **Colaborador comum**: abre chamados, comenta nos próprios chamados, edita o próprio perfil.
- **Membro da Inovação**: assume, atende, reatribui e encerra chamados. Identificado pelo departamento; o id do departamento de Inovação fica em configuração (`app.tickets.handler-department-id`), não fixo no código.
- **Administrador** (registro na tabela `admins`): tudo o que a Inovação faz, mais desativar e anonimizar contas, cadastrar categorias e conceder ou remover privilégio de administrador.
  - Só membros da Inovação podem ser administradores (validado no service).
  - Não é possível remover o último administrador.

### Ciclo de vida do chamado

O status **não é editado livremente**: ele muda como consequência de ações.

- Abrir → `OPEN`, sem responsável.
- Assumir → `IN_PROGRESS`, preenche `assignee_id` e `assigned_at`.
- Encerrar → `CLOSED`, preenche `closed_by_id` e `closed_at`.
- Cada ação é um endpoint próprio, e o service valida se a transição é permitida (máquina de estados).

Métricas previstas: tempo de resposta (`assigned_at - created_at`) e tempo de resolução (`closed_at - created_at`).

### Desativação de conta (ex.: demissão)

Preenche `deactivated_at` e bloqueia o login imediatamente, revoga todos os refresh tokens do usuário e devolve à fila (`assignee_id = NULL`) os chamados que estavam com ele. A anonimização (LGPD) vem depois, sem apagar fisicamente o registro.

## 6. Autenticação

- Senhas com **bcrypt** (`password_hash`).
- **Access token**: JWT curto (10 a 15 min), não armazenado no banco.
- **Refresh token**: valor aleatório (`SecureRandom`, 32+ bytes), armazenado como **hash SHA-256** em `refresh_tokens`.
  - Rotação a cada uso: o atual é revogado e um novo é emitido. Reuso de token revogado revoga todos os tokens do usuário.
  - Logout revoga o token da sessão atual.
- Cookies `HttpOnly`, `Secure`, `SameSite=Strict`; o refresh token com `Path=/auth/refresh`.
- Authorities derivadas no login: `ROLE_ADMIN` (se está em `admins`) e `TICKET_HANDLER` (se é da Inovação).

## 7. Convenções da API

- Recursos em inglês e no plural: `/users`, `/tickets`, `/categories`.
- `PUT` para edição completa de formulário; `PATCH` para ações pontuais.
- **DTOs** como `record`, separados em entrada e saída. Nunca expor entidades JPA, e nunca retornar `password_hash`.
- Validação no DTO (`@NotBlank`, `@Size`, `@Email`, `@Pattern`), com os mesmos limites do banco.

Endpoints previstos:

| Método | Rota | Descrição |
|---|---|---|
| GET | `/users/me` | dados do usuário logado |
| PUT | `/users/me` | edição do próprio perfil (DTO restrito: sem senha, e-mail ou permissões) |
| PATCH | `/users/me/password` | troca de senha (exige a senha atual) |
| GET | `/users/{id}` | consulta de outro usuário (admin) |
| PATCH | `/users/{id}/deactivate` | desativação de conta (admin) |
| POST | `/tickets` | abrir chamado |
| PATCH | `/tickets/{id}/assign` | assumir chamado |
| PATCH | `/tickets/{id}/close` | encerrar chamado |

No Spring, rotas literais como `/users/me` têm prioridade sobre `/users/{id}`.

## 8. Requisitos funcionais

| Código | Requisito |
|---|---|
| RF00 | Criação de conta |
| RF01 | Login |
| RF02 | Edição de dados do usuário |
| RF03 | Logout |
| RF04 | Encerramento de conta (anonimização, sem exclusão física) |
| RF05 | Abertura de chamados |
| RF06 | Alteração de status |
| RF07 | Encerramento de chamados |

Decisão tomada: alteração de status (RF06) e encerramento (RF07) continuam **separados**, porque o encerramento tem regras próprias.

## 9. Documentação ABNT (Overleaf)

- Texto em português; termos técnicos em inglês em itálico.
- Na primeira menção: "chamado (denominado *ticket* no código-fonte)".
- O diagrama de estados do chamado é recomendado para ilustrar RF06 e RF07.
- O nome das colunas na documentação deve ser o mesmo das migrations.

## 10. Pendências

- [ ] Criar a `V8__seed_inicial.sql` (departamento de Inovação e primeiro administrador).
- [ ] Criar as entidades JPA, compatíveis com o schema (`ddl-auto=validate`).
- [ ] Implementar autenticação (JWT, refresh token, cookies) e o carregamento das authorities.
- [ ] Implementar os services com as regras de transição de status.
- [ ] Modelar as notificações (e-mail e dentro da interface).
- [ ] Decidir se haverá status separado entre *Concluído* (técnico resolveu) e *Encerrado* (solicitante confirmou). Se sim: novo status `RESOLVED` e coluna `resolved_at`.
- [ ] Decidir se o painel terá uma fila ordenada por prioridade; se sim, criar o índice `(status, priority_id DESC, created_at)`.
- [ ] Mover as credenciais definitivamente para variáveis de ambiente e configurar os clientes (pgAdmin e Spring Boot).
- [ ] Alinhar a documentação ABNT com os nomes finais do banco.

## 11. Orientações para o assistente

- Responder em **português**; código, nomes de tabelas, colunas, classes e rotas em **inglês**.
- **Preferir soluções simples**, proporcionais a um sistema interno com equipe de 3 pessoas. Evitar abstrações que o projeto ainda não precisa; quando algo mais robusto fizer sentido no futuro, registrar como trabalho futuro.
- Seguir as convenções e decisões deste README. Se uma sugestão contradisser algo daqui, apontar a diferença explicitamente.
- Ao propor mudanças no banco, criar uma **nova migration**, nunca editar as existentes.
- Explicar o porquê das escolhas, já que elas também alimentam a documentação ABNT.