# CentralDesk — Contexto do Projeto

Este documento resume o estado do projeto e as decisões já tomadas. Ele serve como contexto para continuar o desenvolvimento em novas conversas: **as decisões listadas aqui já foram discutidas e não precisam ser reabertas**, a menos que eu peça.

Última atualização: 20/10/2026.

---

## 1. Visão geral

**CentralDesk** é o sistema web de chamados (*tickets*) internos da Contabiliza. Hoje os pedidos de suporte chegam por e-mail, o que torna o atendimento lento e desorganizado. O sistema oferece um painel onde os colaboradores abrem chamados, organizados por **categoria**, **prioridade** e **solicitante**, e onde a equipe de suporte os atende.

- Atende **todo tipo de suporte**, não só manutenção física.
- **Somente o departamento de Inovação atende chamados** (equipe de 3 pessoas, com um coordenador).
- Notificações: **painel de notificações na interface, com filtro por categoria** (modelado em 20/10, seção 6) e, depois, e-mail (envio via **Resend**).
- **Avaliação do atendimento**: o solicitante pode avaliar o profissional que atendeu o chamado encerrado (modelada em 20/10, seção 5).
- O projeto também tem uma **documentação formal em ABNT, escrita no Overleaf** (`docs/DOC_SISTEMA_CHAMADOS_0.1.pdf`).
- As tarefas do projeto são organizadas no **Jira**.

## 2. Stack e ambiente

| Camada | Tecnologia |
|---|---|
| Front-end | Vue.js |
| Back-end | Java 21 + **Spring Boot 4.1.1** (Spring Framework 7, Hibernate 7, Spring Security) |
| Banco | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Administração do banco | pgAdmin (Docker) |
| Documentação da API | springdoc-openapi 3.1.1 (OpenAPI 3 + Swagger UI) |
| Testes de API | Swagger UI e Insomnia |
| Ambiente | VS Code no Windows, comandos no **PowerShell**; projeto em `C:\projetos\centraldesk` |

- Pacote base: `br.com.contabilizape.centraldesk`
- Classe principal: `CentralDeskApplication`
- Lombok disponível: usar `@Getter` nas entidades; **nunca `@Data` nem `@Setter` geral** em entidades (o `equals`/`hashCode`/`toString` gerados disparam o carregamento de relações `LAZY`).

### Dependências (`pom.xml`)

No Spring Boot 4, alguns starters mudaram de nome:

- `spring-boot-starter-webmvc` (antigo `spring-boot-starter-web`)
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation` (sem ele, `@NotBlank`, `@Email` e `@Valid` são ignorados)
- `spring-boot-starter-flyway` + `flyway-database-postgresql` (só o `flyway-core` não basta para o Spring rodar as migrations)
- `postgresql` (runtime), `lombok`, `spring-boot-devtools`, `spring-boot-starter-actuator`
- `spring-boot-starter-oauth2-resource-server` (emite e valida o JWT do login)
- `springdoc-openapi-starter-webmvc-ui` (versão na propriedade `springdoc.version`; a linha 3.1.x é a do Spring Boot 4.1)

## 3. Como rodar

### Banco e pgAdmin

Sobem com o `docker-compose.yaml` da raiz (serviços `db` e `pgadmin`):

```powershell
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
ADMIN_NAME=...
ADMIN_CPF=...
ADMIN_EMAIL=...
ADMIN_PASSWORD=...
API_DOCS_ENABLED=true
JWT_SECRET=...
```

O compose lê o `.env` automaticamente. O Spring também o lê, pelo `spring.config.import` do `application.properties` — então a aplicação recebe as variáveis tanto pelo VS Code quanto por `.\mvnw spring-boot:run`. Os testes que sobem o Spring usam a anotação `@IntegrationTest`, que zera `app.bootstrap.admin.*`, fixa `API_DOCS_ENABLED=false` e usa uma chave JWT própria (`IntegrationTest.JWT_SECRET`), para não depender do `.env` local.

`JWT_SECRET` é a chave que assina os tokens de login: base64 de 32 bytes aleatórios (comando no README). Sem ela, ou com menos de 256 bits, a aplicação não sobe. `COOKIE_SECURE` (opcional, padrão `true`) só vira `false` se o navegador recusar o cookie em `http://localhost`.

`API_DOCS_ENABLED=true` liga a documentação da API (Swagger). Sem a variável, ela fica desligada: é o caso de produção.

**Acentos no `.env`:** pelo `spring.config.import`, o Spring lê o `.env` como `.properties`, em **ISO-8859-1**. Um valor acentuado salvo em UTF-8 chega corrompido ("Abraão" vira "AbraÃ£o"). Nas variáveis lidas pelo Spring, escreva acentos como escape Unicode: `ADMIN_NAME=Abra\u00e3o ...` (ã = `\u00e3`, ç = `\u00e7`, é = `\u00e9`). Senhas: prefira só ASCII; um acento corrompido mudaria o hash gravado. As variáveis `ADMIN_*` definem o **primeiro administrador**, criado pela aplicação ao subir (ver seção 5).

### `application.properties`

```properties
spring.application.name=centraldesk

# Lê o .env da raiz, seja qual for a forma de subir (VS Code, mvnw...)
spring.config.import=optional:file:.env[.properties]

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}

spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Mensagens de erro no corpo da resposta (desenvolvimento)
server.error.include-message=always
server.error.include-binding-errors=always

# Regras da aplicação
app.users.allowed-email-domain=contabilizape.com.br
app.tickets.handler-department-id=e9790f1f-41df-4369-bfe7-1be4be535561
app.departments.default-department-id=3094ed3f-42ab-4bc0-9d8d-f6eb08891016

# Primeiro administrador (vazio = nenhum admin criado)
app.bootstrap.admin.name=${ADMIN_NAME:}
app.bootstrap.admin.cpf=${ADMIN_CPF:}
app.bootstrap.admin.email=${ADMIN_EMAIL:}
app.bootstrap.admin.password=${ADMIN_PASSWORD:}

# Autenticação (JWT HS256)
app.security.jwt.secret=${JWT_SECRET:}
app.security.jwt.access-token-ttl=15m
app.security.jwt.issuer=centraldesk
app.security.cookie.secure=${COOKIE_SECURE:true}

# Documentação da API: desligada por padrão; API_DOCS_ENABLED=true no .env liga
springdoc.api-docs.enabled=${API_DOCS_ENABLED:false}
springdoc.swagger-ui.enabled=${API_DOCS_ENABLED:false}
springdoc.override-with-generic-response=false
springdoc.swagger-ui.operations-sorter=alpha
springdoc.swagger-ui.tags-sorter=alpha
```

- `ddl-auto=validate`: **o Flyway cria as tabelas, o Hibernate só confere** se as entidades batem com o schema ao subir. Um nome de coluna errado impede a aplicação de iniciar, em vez de falhar só na primeira requisição.
- Os nomes das chaves `app.*` precisam bater **exatamente** com os `@Value` do código. Uma chave ausente ou com nome diferente impede a aplicação de subir (*Could not resolve placeholder*).

### Aplicação

```powershell
.\mvnw clean spring-boot:run
```

O Flyway aplica as migrations ao subir. Com `API_DOCS_ENABLED=true`, a documentação fica em `http://localhost:8080/swagger-ui.html` (o JSON em `/v3/api-docs`). Para checar só a compilação: `.\mvnw clean compile`. Se o VS Code acusar erros que o terminal não acusa: `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace**.

## 4. Banco de dados

### Migrations (`src/main/resources/db/migration/`)

Formato obrigatório: `V<n>__<descricao>.sql` — **`V` maiúsculo** e dois underlines. **Depois de aplicada, uma migration não é editada** (nem os comentários, porque o checksum muda): qualquer mudança vira um novo arquivo.

| Arquivo | Conteúdo |
|---|---|
| `V1__create_departments.sql` | departamentos |
| `V2__create_users.sql` | usuários |
| `V3__create_admins.sql` | administradores |
| `V4__create_priorities_and_categories.sql` | categorias e prioridades (com as 4 prioridades inseridas) |
| `V5__create_tickets.sql` | chamados |
| `V6__create_comments.sql` | comentários |
| `V7__create_refresh_tokens.sql` | refresh tokens |
| `V8__seed_departments.sql` | departamentos Inovação e Geral (UUIDs fixos). **Sem dados pessoais**: o primeiro admin vem do `AdminBootstrap` |
| `V9__create_departments_and_requests.sql` | pedidos de entrada em departamento (`department_requests`) |
| `V10__seed_categories.sql` | categorias iniciais dos chamados (Hardware, Software, Acesso e contas, Rede e internet, Sistemas internos, Outros) |
| `V11__create_ticket_ratings.sql` | avaliações do atendimento (`ticket_ratings`). **Escrita em 20/10, ainda não aplicada** |
| `V12__create_notifications.sql` | notificações do painel (`notifications`). **Escrita em 20/10, ainda não aplicada** |

Conferido em 20/10: a V11 e a V12 não conflitam com as anteriores (numeração, nomes de tabelas, índices e constraints livres; todas as FKs apontam para tabelas já existentes). Até serem aplicadas, ainda podem ser ajustadas.

### Departamentos fixos (criados na V8)

| Departamento | UUID | Uso |
|---|---|---|
| Inovação | `e9790f1f-41df-4369-bfe7-1be4be535561` | atende chamados; entrada exige aprovação |
| Geral | `3094ed3f-42ab-4bc0-9d8d-f6eb08891016` | departamento inicial de quem aguarda aprovação ou foi recusado |

Os UUIDs são fixos porque a aplicação os referencia por configuração: assim são iguais em qualquer banco recriado a partir das migrations. **Não existe campo `handles_tickets`**: o departamento atendente é identificado pelo id em configuração.

### Tabelas

**departments** — `id` UUID PK · `name` VARCHAR(120) UNIQUE · `created_at` · `updated_at`

**users** — `id` UUID PK · `full_name` VARCHAR(150) · `cpf` CHAR(11) UNIQUE, nulo após anonimização · `email` VARCHAR(255) UNIQUE (login) · `password_hash` VARCHAR(255) · `status` (`ACTIVE`, `DEACTIVATED`, `ANONYMIZED`) · `department_id` FK · `created_at` · `updated_at` · `validated_at` · `deactivated_at` · `deleted_at`

**admins** — `user_id` UUID PK e FK para `users` (1:1) · `granted_by_id` FK para `users`, nullable (o primeiro admin não tem) · `granted_at`

**department_requests** — `id` UUID PK · `user_id` FK · `department_id` FK (departamento pedido) · `status` (`PENDING`, `APPROVED`, `REJECTED`) · `reviewed_by_id` FK nullable · `reviewed_at` nullable · `created_at` · `updated_at`
- `CHECK`: pedido `PENDING` não tem revisor; pedido revisado precisa ter `reviewed_by_id` e `reviewed_at`.
- Índice único parcial: no máximo **um pedido pendente por usuário** (pode pedir de novo depois de uma recusa).
- Pedidos revisados não são apagados: o histórico fica preservado.

**categories** — `id` UUID PK · `label` VARCHAR(60) UNIQUE · `color` CHAR(7) (`#RRGGBB`, validado por CHECK). Iniciais na V10; novas entram por migration (no futuro, por uma tela do administrador).

**priorities** — `id` SMALLINT PK · `label` VARCHAR(20) UNIQUE. Valores fixos: 1 Baixa, 2 Média, 3 Alta, 4 Crítica. O `id` define a ordem de urgência.

**tickets** — `id` UUID PK · `title` VARCHAR(120) · `description` TEXT · `status` (`OPEN`, `IN_PROGRESS`, `CLOSED`) · `category_id` FK · `priority_id` FK · `requester_id` FK · `assignee_id` FK nullable · `assigned_at` nullable · `closed_by_id` FK nullable · `closed_at` nullable · `created_at` · `updated_at`

**comments** — `id` UUID PK · `content` TEXT · `user_id` FK · `ticket_id` FK · `created_at` · `updated_at`

**refresh_tokens** — `id` UUID PK · `user_id` FK · `token_hash` CHAR(64) UNIQUE (SHA-256) · `created_at` · `expires_at` NOT NULL · `revoked_at` nullable

**ticket_ratings** (V11) — `id` UUID PK · `ticket_id` FK, UNIQUE (`uq_ticket_ratings_ticket`: no máximo uma avaliação por chamado) · `rater_id` FK (o solicitante) · `rated_id` FK (o responsável no momento do encerramento) · `score` SMALLINT (`chk_ticket_ratings_score`: 1 a 5) · `comment` TEXT nullable · `created_at`
- `chk_ticket_ratings_not_self`: `rater_id <> rated_id` (ninguém avalia a si mesmo).
- `rated_id` é gravado, e não lido de `tickets.assignee_id`, para que uma reatribuição posterior não mude quem foi avaliado.
- Sem `updated_at`: a avaliação não é editada depois de enviada.
- Índice `(rated_id, created_at)` para consultas de desempenho por profissional.

**notifications** (V12) — `id` UUID PK · `recipient_id` FK · `type` VARCHAR(40) (`chk_notifications_type`) · `actor_id` FK nullable (quem causou o evento) · `ticket_id` FK nullable · `department_request_id` FK nullable · `read_at` nullable · `created_at`
- Tipos: `TICKET_ASSIGNED`, `TICKET_COMMENTED`, `TICKET_CLOSED`, `DEPARTMENT_REQUEST_CREATED`, `DEPARTMENT_REQUEST_APPROVED`, `DEPARTMENT_REQUEST_REJECTED`, `ADMIN_GRANTED`. Um tipo novo exige migration que altere o `CHECK`.
- **Uma linha por destinatário**: cada um tem o próprio estado de leitura, sem tabela intermediária.
- **Sem coluna de texto**: o front monta a mensagem a partir de `type`, `actor` e das referências. O texto nunca fica desatualizado e o conteúdo de comentários (que será cifrado) não é copiado para cá.
- Referências com FKs explícitas e anuláveis, e não um par genérico `entity_type`/`entity_id`: são poucos alvos, e o banco garante a integridade.
- Índices: `(recipient_id, created_at DESC)` para a listagem; parcial `(recipient_id) WHERE read_at IS NULL` para o contador de não lidas; `ticket_id` e `department_request_id`.

### Convenções do banco

- Nomes de tabelas e colunas em inglês, `snake_case`, minúsculas.
- Nomes de constraints: `chk_<tabela>_<regra>` para `CHECK` e `uq_<tabela>_<coluna>` para unicidade (padrão da V9).
- Chaves primárias UUID com `DEFAULT gen_random_uuid()` no banco **e** `@GeneratedValue(strategy = GenerationType.UUID)` no Java. A aplicação gera o UUID antes do `INSERT`; o default do banco serve para inserts feitos fora da aplicação (seeds, pgAdmin). Os dois convivem sem conflito.
- Datas de eventos sempre `TIMESTAMPTZ` com `DEFAULT CURRENT_TIMESTAMP` (Java: `OffsetDateTime`, `@CreationTimestamp` / `@UpdateTimestamp`).
- Valores fixos ligados ao código (status, tipos de notificação): `VARCHAR` + `CHECK`, mapeados com `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
- Valores cadastráveis (categorias, departamentos): tabela própria com FK.
- **Soft delete**: usuários não são apagados; são desativados e depois anonimizados, para preservar o histórico dos chamados. Por isso as FKs não usam `ON DELETE CASCADE`. **Exceção:** notificações não têm valor histórico e são apagadas fisicamente na anonimização.
- Índices em todas as FKs usadas em consultas frequentes, além de `(assignee_id, status)` em tickets, `(ticket_id, created_at)` em comments e `(status, created_at)` em department_requests.
- **Criptografia em repouso com AES-GCM**: comentários e descrições dos chamados ficam cifrados no banco, com a chave no servidor (variável `CONTENT_ENCRYPTION_KEY`), além do HTTPS. O comentário da avaliação (`ticket_ratings.comment`) segue a mesma regra. Consequência aceita: não há busca no conteúdo cifrado. Ainda não implementado (ADR no Jira: SCRUM-52).

### Diagramas

- Modelo conceitual (notação de Chen): `modelo_conceitual_chamados.drawio`
- Modelo lógico: `modelo_logico_chamados.dbml` (para o dbdiagram.io)
- Ambos ainda não incluem `department_requests`, `ticket_ratings` e `notifications`.

## 5. Regras de negócio

### Permissões

- **Colaborador comum**: abre chamados, comenta nos próprios chamados, avalia o atendimento dos próprios chamados, edita o próprio perfil.
- **Membro da Inovação** (authority `TICKET_HANDLER`): assume, atende, reatribui e encerra chamados. Identificado pelo departamento: no login, `user.department.id` é comparado com `app.tickets.handler-department-id`.
- **Administrador** (registro na tabela `admins`, authority `ROLE_ADMIN`): tudo o que a Inovação faz, mais aprovar ou recusar pedidos de departamento, desativar e anonimizar contas, cadastrar categorias e conceder ou remover privilégio de administrador.
  - Só membros da Inovação podem ser administradores (condição necessária, não suficiente). Entrar na Inovação **não** torna ninguém admin: a concessão é sempre um ato explícito de outro admin, registrado em `granted_by_id` e `granted_at`.
  - Não é possível remover o último administrador.
  - O **primeiro admin** é criado pelo `AdminBootstrap` (`ApplicationRunner`) ao subir a aplicação, **se ainda não houver nenhum admin** e as variáveis `ADMIN_NAME`, `ADMIN_CPF`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` estiverem definidas. As credenciais passam pelas mesmas validações do cadastro (CPF, e-mail corporativo, força da senha); se forem inválidas, ou se o e-mail ou o CPF já existir, a aplicação não sobe. Ele entra na Inovação com `granted_by_id` nulo.
  - Por quê: dados pessoais e senha não podem ficar numa migration versionada no Git. Cada ambiente define o próprio admin no `.env`.

### Ações do admin sobre a conta de outro usuário

Não existe edição livre da conta de outra pessoa. O admin tem **ações pontuais**, cada uma com seu endpoint:

| Ação | O que muda no banco |
|---|---|
| Aprovar ou recusar pedido de departamento | `department_requests.status` e, se aprovado, `users.department_id` |
| Tornar admin / remover admin | insere ou apaga registro em `admins` |
| Desativar conta | `users.status` e `deactivated_at` |

### Cadastro de conta (RF00)

1. O e-mail precisa ser do domínio `@contabilizape.com.br` (anotação `@CorporateEmail` no DTO; o domínio vem de `app.users.allowed-email-domain`, sem diferenciar maiúsculas) e é normalizado no service (`trim` + minúsculas) antes de gravar e de checar duplicidade.
2. E-mail e CPF precisam ser únicos (409 se já existirem).
3. O usuário escolhe qualquer departamento.
   - **Qualquer departamento exceto Inovação**: o usuário é criado nele.
   - **Inovação**: o usuário é criado no **Geral** e, na mesma transação, ganha um `department_request` `PENDING` para a Inovação. Os **administradores ativos** são notificados.
4. A conta nasce `ACTIVE`, sem confirmação de e-mail. `validated_at` fica nulo (significado ainda a definir — ver pendências).
5. A senha é gravada com bcrypt; a resposta nunca traz `password_hash` nem `cpf`.
6. **Validações do DTO** (`UserCreateRequest`): nome até 150 caracteres; **CPF com `@Cpf`** (11 dígitos sem pontuação, dígitos verificadores pelo módulo 11, recusa sequências como `11111111111`); e-mail válido até 255; **senha de 8 a 72 caracteres com ao menos uma letra e um número** (letras acentuadas valem); departamento obrigatório.
7. O front envia o CPF só com dígitos: **não há normalização**, e pontuação é recusada com 400.

### Jornada de quem quer entrar na Inovação

1. Cadastro escolhendo Inovação → fica no Geral, pedido `PENDING`, admins notificados.
2. Um admin aprova → pedido `APPROVED`, `department_id` passa a ser o da Inovação; no próximo login recebe `TICKET_HANDLER`. Se recusar → `REJECTED`, continua no Geral.
3. (Opcional, depois) um admin o torna administrador.

### Ciclo de vida do chamado

O status **não é editado livremente**: ele muda como consequência de ações.

- Abrir → `OPEN`, sem responsável.
- Assumir → `IN_PROGRESS`, preenche `assignee_id` e `assigned_at`.
- Encerrar → `CLOSED`, preenche `closed_by_id` e `closed_at`.
- Cada ação é um endpoint próprio, e o service valida se a transição é permitida (máquina de estados).

Os status continuam `OPEN`, `IN_PROGRESS` e `CLOSED` (enum `TicketStatus`). A separação entre *Concluído* e *Encerrado* segue como pendência (seção 12) e **não** foi adotada para a avaliação.

Métricas previstas: tempo de resposta (`assigned_at - created_at`) e tempo de resolução (`closed_at - created_at`).

### Avaliação do atendimento (decidida em 20/10/2026)

O solicitante avalia o profissional que atendeu o chamado com uma nota de **1 a 5** e um comentário opcional. A avaliação é **opcional**: o usuário pode simplesmente fechar o formulário.

**Quando o formulário aparece** (comparando `tickets.requester_id` com `tickets.closed_by_id`):

| Situação | Comportamento |
|---|---|
| Quem encerrou é o próprio solicitante | o front abre o formulário logo depois do encerramento |
| Quem encerrou foi outra pessoa | o solicitante recebe uma notificação `TICKET_CLOSED`, e o painel oferece a avaliação a partir dela |
| O solicitante é o próprio responsável (`requester_id = assignee_id`) | não há avaliação (seria autoavaliação), e nenhuma notificação de avaliação é criada |
| Chamado encerrado sem responsável (`assignee_id` nulo) | não há avaliação (não há quem avaliar) |

**Regras do back-end** (`POST /tickets/{id}/rating`):

- só o solicitante do chamado avalia (403 para os demais);
- o chamado precisa estar `CLOSED` e ter `assignee_id` (409 caso contrário);
- o avaliador não pode ser o avaliado (409; o `CHECK` do banco garante o mesmo);
- uma segunda avaliação do mesmo chamado → 409;
- `rated_id` recebe o `assignee_id` do chamado no momento da avaliação.

**O encerramento não sabe da avaliação.** O front chama `PATCH /tickets/{id}/close` e, se o usuário quiser, abre o formulário e faz o `POST`. "Pular" significa não chamar nada. Assim o endpoint de encerramento não ganha um campo opcional que mistura duas responsabilidades.

**Em aberto** (decidir com o coordenador antes de expor as notas): quem vê as avaliações (só coordenador e admins? o avaliado vê as próprias? notas individuais ou só médias?) e se haverá prazo para avaliar. Numa equipe de 3 pessoas a avaliação é nominal, então isso tem um lado de clima de equipe e de LGPD (dado de desempenho de uma pessoa).

### Desativação de conta (ex.: demissão)

Preenche `deactivated_at` e bloqueia o login imediatamente, revoga todos os refresh tokens do usuário e devolve à fila (`assignee_id = NULL`) os chamados que estavam com ele. A anonimização (LGPD) vem depois, sem apagar fisicamente o registro do usuário; as notificações dele são apagadas.

## 6. Notificações e tempo real

### Painel de notificações (decidido em 20/10/2026)

O sistema terá um **painel de notificações** na interface, com **filtro por categoria**. Decidimos pelo painel porque as filas (de chamados para a Inovação, de pedidos para os admins) não cobrem o solicitante: ele precisa saber de comentários novos, encerramentos e avaliações pendentes sem ter de abrir cada chamado.

**Tipos e destinatários:**

| Tipo | Destinatário | Disparado por | Categoria |
|---|---|---|---|
| `TICKET_ASSIGNED` | solicitante | alguém assume o chamado | `TICKETS` |
| `TICKET_COMMENTED` | solicitante e responsável, exceto o autor do comentário | comentário novo | `TICKETS` |
| `TICKET_CLOSED` | solicitante, **só se outra pessoa encerrou** (convida a avaliar) | encerramento | `TICKETS` |
| `DEPARTMENT_REQUEST_CREATED` | cada administrador ativo | cadastro pedindo Inovação | `ADMINISTRATION` |
| `DEPARTMENT_REQUEST_APPROVED` | quem pediu | admin aprova | `ACCOUNT` |
| `DEPARTMENT_REQUEST_REJECTED` | quem pediu | admin recusa | `ACCOUNT` |
| `ADMIN_GRANTED` | o novo administrador | admin concede o privilégio | `ACCOUNT` |

Ninguém é notificado de uma ação que ele mesmo fez (o `actor` nunca é o destinatário).

**Categorização:** a categoria **não é coluna no banco**. É um enum `NotificationCategory` (`TICKETS`, `ACCOUNT`, `ADMINISTRATION`) no Java, e cada `NotificationType` sabe a sua categoria. O filtro vira `WHERE type IN (...)` com os tipos da categoria. Assim não há coluna redundante que possa divergir do tipo, e mudar um tipo de categoria não exige migration.

**Fora do escopo agora** (YAGNI): preferências por categoria (silenciar, escolher canais) e registro de envio de e-mail por notificação. Se o "silenciar" virar requisito, entra uma tabela `notification_preferences`.

**Leitura:** `read_at` preenchido quando o usuário marca como lida (uma ou todas). Só o destinatário pode marcar a própria notificação; a de outra pessoa responde 404, para não revelar que ela existe.

### Infraestrutura

- **Atualizações em tempo real na interface via SSE** (Server-Sent Events), e não WebSocket: o fluxo é só do servidor para o cliente, com um stream único por usuário. Limite conhecido: funciona com uma única instância da aplicação; pub/sub entre instâncias fica como trabalho futuro (ADR no Jira: SCRUM-51). O painel pode começar consultando a API e ganhar o SSE depois.
- Canais no MVP: **painel na interface primeiro**; e-mail depois, aproveitando os mesmos eventos (SCRUM-44).
- E-mail: previsto com `spring-boot-starter-mail` (`JavaMailSender`) via SMTP — Resend com domínio próprio para testes, Mailpit no Docker para desenvolvimento, e SMTP da empresa (`@contabilizape.com.br`) como alvo de produção. Ainda não implementado.
- Disparadas por **eventos do Spring** (`ApplicationEventPublisher`), ouvidos com `@TransactionalEventListener(phase = AFTER_COMMIT)`: o ouvinte só roda depois que a transação foi gravada, e uma falha no envio não desfaz a operação.
- Implementado: `DepartmentRequestCreatedEvent` → `DepartmentRequestNotificationListener`, que busca os admins ativos (`UserRepository.findAdminsByStatus`). **Provisório**: por enquanto só registra no log (`Notificar ...`). Com o painel, o ouvinte passa a gravar uma notificação `DEPARTMENT_REQUEST_CREATED` por administrador.

## 7. Autenticação

- Senhas com **bcrypt** (`BCryptPasswordEncoder`, bean no `SecurityConfig`). Senha limitada a 72 caracteres, porque o bcrypt ignora o que passa de 72 bytes.

### Implementado (RF01, SCRUM-137)

- **Spring Security OAuth2 Resource Server**: o próprio Spring valida o JWT a cada requisição. Escolha feita em vez do jjwt para não escrevermos o filtro.
- **Access token**: JWT **HS256**, válido por **15 minutos**, não armazenado no banco. Claims: `iss=centraldesk`, `sub` (id do usuário), `email`, `roles`.
  - Chave em `JWT_SECRET` (base64, ≥ 256 bits), checada ao subir (`JwtConfig`).
  - O `JwtDecoder` confere a assinatura, a validade e o emissor.
- **Cookie** `access_token`: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age` igual à validade do token. O token **nunca** vai no corpo da resposta. A API **não** aceita `Authorization: Bearer`.
- **Login** (`POST /auth/login`, `AuthService`):
  - e-mail normalizado como no cadastro;
  - e-mail inexistente e senha errada → **401** com a mesma mensagem ("E-mail ou senha inválidos."). No e-mail inexistente, o bcrypt compara com um hash fictício, para o tempo de resposta não revelar quem tem conta;
  - senha certa mas conta não `ACTIVE` → **403**. O status só é verificado depois da senha.
- **Authorities** calculadas no login e gravadas na claim `roles` (constantes em `security/Roles`): `ROLE_ADMIN` (está em `admins`) e `TICKET_HANDLER` (é da Inovação). Valem até o token vencer: uma mudança de permissão só aparece no próximo login.
- **`GET /users/me`**: dados do usuário do token e as `roles` dele.
  - Sem cookie, ou com token inválido ou vencido → **401** do próprio Spring Security, sem corpo, com `WWW-Authenticate`.
  - Usuário apagado → 401 `Sessão inválida`; conta desativada depois do login → 403.
- **Rotas públicas ignoram o cookie** (`CookieBearerTokenResolver`). Sem isso, um token vencido guardado no navegador faria o Spring responder 401 até no login.
- Sessão `STATELESS` (o servidor não guarda sessão).

### Refresh token com rotação (RF03, SCRUM-143)

- **Valor**: 32 bytes do `SecureRandom` em base64url (43 caracteres). Em `refresh_tokens` fica **só o hash SHA-256** (hex, `CHAR(64)`); quem lê o banco não consegue usar os tokens. Validade: `app.security.refresh-token.ttl` (7 dias).
- **Cookie `refresh_token`**: `HttpOnly`, `Secure`, `SameSite=Strict`, **`Path=/auth`**. Só `/auth/refresh` e `/auth/logout` o recebem; as demais rotas da API não.
- **Login** emite os dois cookies (o `access_token` vem primeiro na resposta).
- **`POST /auth/refresh`** (público, porque é chamado justamente com o access token vencido): **rotação**. O refresh token atual é revogado (`revoked_at`) e saem um access token e um refresh token novos. As **permissões são recalculadas**, então uma promoção a admin ou uma entrada na Inovação vale a partir da próxima renovação, sem novo login.
- **Detecção de reuso**: um refresh token já revogado que volta a ser apresentado é tratado como roubo. Todas as sessões ativas do usuário são revogadas (`revokeAllActiveByUserId`) e a resposta é 401.
- **Recusas**: sem cookie, token desconhecido ou vencido → 401 `Sessão inválida`; conta não `ACTIVE` → todas as sessões revogadas e 403.
- As recusas **não desfazem a transação** (`noRollbackFor` no `RefreshTokenService.rotate` e no `AuthService.refresh`). Sem isso, a revogação feita antes da exceção seria perdida.
- **Limitação**: duas abas renovando ao mesmo tempo podem disparar a detecção de reuso e derrubar a sessão. O front-end deve **serializar a renovação**: uma chamada por vez, com as demais requisições aguardando o resultado.

### Logout (RF03, SCRUM-142 e SCRUM-143)

- `POST /auth/logout` → **204**: revoga o refresh token da sessão no banco e apaga os dois cookies (`Max-Age=0`, mesmos `Path` e atributos dos originais; sem isso o navegador não os substitui).
- **Público e idempotente**: está em `PUBLIC_ENDPOINTS`. O controller lê o cookie `refresh_token` direto (`@CookieValue`), sem passar pelo `CookieBearerTokenResolver`. Com sessão vencida ou sem cookie, também responde 204.
- Uma cópia do access token feita antes do logout vale até vencer (≤ 15 min), mas não consegue mais renovar a sessão.

### Pendências de sessão

- Limpeza periódica de `refresh_tokens` vencidos ou revogados (tarefa agendada).
- Desativar uma conta (admin) deve revogar as sessões dela na hora; hoje isso acontece só na próxima renovação.

### `SecurityConfig` atual

- `PUBLIC_ENDPOINTS` é a lista única de rotas sem login: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /departments`, `/error` e as rotas da documentação (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`). A mesma lista libera o acesso e faz o cookie ser ignorado. Todo o resto exige um JWT válido.
- `/error` precisa estar liberado: o Spring encaminha os erros para lá, e se a rota estiver protegida o cliente recebe um 401 que esconde o erro real.
- Com a documentação desligada, as rotas dela respondem 404 (não existem), e não 401.
- CSRF desligado: o `SameSite=Strict` do cookie impede que outro site envie requisições com ele. Rever se o front-end passar a ficar em outro domínio.

## 8. Convenções da API

- Recursos em inglês e no plural: `/users`, `/tickets`, `/categories`.
- **Autenticação fica separada sob `/auth`** (cadastro, login, refresh, logout). Assim todas as rotas públicas ficam num só prefixo e o resto da API fica inteiro protegido.
- A ação vem do método HTTP, não da URL (`PUT /users/me`, e não `/users/me/edit`). `PUT` para edição completa de formulário; `PATCH` para ações pontuais.
- **DTOs** como `record`, separados em entrada e saída, no pacote `dto/`. Nunca expor entidades JPA, e nunca retornar `password_hash` nem `cpf`.
- Validação de formato no DTO (`@NotBlank`, `@Size`, `@Email`, `@Pattern` e os validadores próprios `@Cpf` e `@CorporateEmail`), com os mesmos limites do banco. Regras que dependem do banco (unicidade, departamento existente) ficam no service.
- **Erros no formato `ProblemDetail`** (RFC 9457, `application/problem+json`). Os services lançam **exceções de domínio** (pacote `exception/`), e o `GlobalExceptionHandler` (`@RestControllerAdvice`) as traduz: duplicidade → 409, dados inválidos → 400. Erros de validação trazem `errors` com a mensagem de cada campo. O handler **estende `ResponseEntityExceptionHandler`**, então também devolve `ProblemDetail` para os erros do próprio Spring MVC (JSON malformado etc.). **Não usar `spring.mvc.problemdetails.enabled=true`**: ele registra um tratador concorrente que responde a validação sem `errors` (SCRUM-133).

  ```json
  { "type": "about:blank", "title": "Dados inválidos", "status": 400,
    "detail": "Verifique os campos e tente novamente.", "instance": "/auth/register",
    "errors": { "cpf": "CPF inválido", "password": "deve conter ao menos uma letra e um número" } }
  ```
- Criação devolve **201 Created** com o cabeçalho `Location` apontando para o recurso.

### Endpoints

| Método | Rota | Descrição | Status |
|---|---|---|---|
| POST | `/auth/register` | cadastro de conta (público) | **implementado** |
| GET | `/departments` | lista de departamentos (`id`, `name`) ordenada por nome, para o select do cadastro (público) | **implementado** |
| POST | `/auth/login` | login (público): usuário + permissões no corpo, JWT no cookie `access_token` | **implementado** |
| POST | `/auth/refresh` | renovação da sessão (público): rotação do refresh token, cookies novos | **implementado** |
| POST | `/auth/logout` | logout (público): revoga o refresh token e apaga os dois cookies (204) | **implementado** |
| GET | `/users/me` | dados e permissões do usuário logado | **implementado** |
| PUT | `/users/me` | edição do próprio perfil: **só o nome** (SCRUM-156) | **implementado** |
| PATCH | `/users/me/password` | troca de senha: exige a atual, revoga todas as sessões e reabre a atual com cookies novos (204) (SCRUM-157) | **implementado** |
| DELETE | `/users/me` | encerramento da própria conta: confirma a senha, anonimiza e apaga os cookies (204); último admin → 409 (SCRUM-159) | **implementado** |
| PATCH | `/users/{id}/anonymize` | anonimização pelo admin, só de conta `DEACTIVATED` (404 / 409) (SCRUM-159) | **implementado** |
| GET | `/users/{id}` | consulta de outro usuário (admin) | previsto |
| PATCH | `/users/{id}/deactivate` | desativação de conta (admin) | previsto |
| GET | `/department-requests` | fila de pedidos pendentes (admin) | previsto |
| PATCH | `/department-requests/{id}/approve` | aprovar pedido (admin) | previsto |
| PATCH | `/department-requests/{id}/reject` | recusar pedido (admin) | previsto |
| POST | `/tickets` | abrir chamado (logado): solicitante = usuário do token, nasce `OPEN` | **implementado** |
| GET | `/tickets/mine` | chamados abertos pelo usuário: paginado, mais recentes primeiro, filtros `status`/`categoryId`/`priorityId`, resumo sem descrição (SCRUM-160) | **implementado** |
| GET | `/tickets/queue` | fila da Inovação: `OPEN` sem responsável; só `TICKET_HANDLER` e admin (SCRUM-160) | **implementado** |
| GET | `/tickets/assigned` | chamados sob responsabilidade do usuário; só `TICKET_HANDLER` e admin (SCRUM-160) | **implementado** |
| GET | `/tickets/{id}` | detalhes do chamado: solicitante, `TICKET_HANDLER` e admin; demais → 404 (SCRUM-161) | **implementado** |
| PUT | `/tickets/{id}` | editar chamado: só o solicitante e só enquanto `OPEN` (403 / 409 / 404) | **implementado** |
| GET | `/categories` | categorias do formulário de chamado, por nome (logado) | **implementado** |
| GET | `/priorities` | prioridades, da menos à mais urgente (logado) | **implementado** |
| PATCH | `/tickets/{id}/assign` | assumir chamado: `OPEN` → `IN_PROGRESS`, responsável = usuário logado; só `TICKET_HANDLER` e admin (403 / 404 / 409) (SCRUM-162) | **implementado** |
| PATCH | `/tickets/{id}/close` | encerrar chamado: `OPEN`/`IN_PROGRESS` → `CLOSED`, preenche `closed_by_id`/`closed_at`; solicitante, `TICKET_HANDLER` e admin; demais → 404; já encerrado → 409; sem corpo (SCRUM-163) | **implementado** |
| POST | `/tickets/{id}/rating` | avaliar o atendimento: só o solicitante, chamado `CLOSED` com responsável, uma vez (201 / 403 / 404 / 409) | previsto |
| GET | `/notifications` | notificações do usuário logado, mais recentes primeiro, paginadas; filtros opcionais `category` e `unread` | previsto |
| GET | `/notifications/unread-count` | contador de não lidas (para o ícone do painel) | previsto |
| PATCH | `/notifications/{id}/read` | marcar uma como lida (204; 404 se não for do usuário) | previsto |
| PATCH | `/notifications/read-all` | marcar todas como lidas, respeitando o filtro `category` se vier (204) | previsto |

No Spring, rotas literais como `/users/me` têm prioridade sobre `/users/{id}` (o mesmo vale para `/notifications/read-all` e `/notifications/unread-count`).

### Documentação OpenAPI (Swagger)

- springdoc gera o OpenAPI a partir do código. Liga com `API_DOCS_ENABLED=true` (só em desenvolvimento): Swagger UI em `/swagger-ui.html`, JSON em `/v3/api-docs`.
- Os textos da documentação ficam em **interfaces** no pacote `controller/doc/` (`AuthApi`, `DepartmentApi`, `UserApi`), e o controller as implementa. Assim o controller fica só com o código (mapeamento HTTP e chamada ao service).
- **Todo endpoint novo** ganha, na interface: `@Operation` (resumo e regras) e um `@ApiResponse` para **cada status** que pode devolver. Erros usam `application/problem+json` com `schema = ProblemDetail.class` e exemplos de `ProblemExamples`, que precisam refletir o que o `GlobalExceptionHandler` devolve de fato.
- `springdoc.override-with-generic-response=false`: só aparecem as respostas declaradas, e não todos os erros do handler em todos os endpoints.
- DTOs com `@Schema` (descrição e exemplo). Senhas com `accessMode = WRITE_ONLY`.
- Endpoint protegido declara `security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)`. O esquema `cookieAuth` descreve o cookie `access_token`. No Swagger UI, depois do *Try it out* do login, o navegador envia o cookie sozinho (o botão *Authorize* não é necessário).
- `OpenApiConfig` define o título ("CentralDesk API"), a versão do contrato (`v1`) e a descrição geral, que explica o formato de erro.
- Os testes `ApiDocsIntegrationTest` (ligado) e `ApiDocsDisabledIntegrationTest` (desligado → 404) garantem o comportamento.

### Testes manuais: use o Swagger

Os endpoints que dependem de sessão (`/users/me`, `/auth/refresh`, `/auth/logout`) são testados no **Swagger UI**. O navegador guarda os cookies `Secure` em `http://localhost`; o Insomnia não guardou nos testes feitos em 16/10/2026. Decisão: manter `COOKIE_SECURE=true`, que é o valor de produção, e não alterar a API por causa de uma ferramenta de teste. O Insomnia continua servindo para os endpoints públicos (cadastro, departamentos, login).

### Testando o cadastro no Insomnia

`POST http://localhost:8080/auth/register`, body JSON:

```json
{
  "fullName": "Maria da Silva",
  "cpf": "12345678901",
  "email": "maria.silva@contabilizape.com.br",
  "password": "senhaForte123",
  "departmentId": "3094ed3f-42ab-4bc0-9d8d-f6eb08891016"
}
```

| Cenário | Resposta esperada |
|---|---|
| Dados válidos, departamento Geral | 201, `departmentApprovalPending: false` |
| Dados válidos, departamento Inovação | 201, usuário no Geral, `departmentApprovalPending: true`, linhas `Notificar ...` no log |
| Mesmo e-mail / mesmo CPF | 409 |
| E-mail de outro domínio | 400 com `errors.email` |
| CPF com pontuação, senha curta | 400 com o campo que falhou |
| `departmentId` inexistente | 400 "Departamento não encontrado" |

## 9. Estrutura do código

Só a classe principal fica na raiz; o Spring procura componentes do pacote dela para baixo.

```
src/main/java/br/com/contabilizape/centraldesk/
├── CentralDeskApplication.java
├── config/          SecurityConfig, OpenApiConfig
├── controller/      AuthController, DepartmentController, UserController, TicketController,
│                    TicketCatalogController, GlobalExceptionHandler
│   └── doc/         AuthApi, DepartmentApi, UserApi, TicketApi, TicketCatalogApi (OpenAPI), ProblemExamples
├── dto/             UserCreateRequest, UserResponse, DepartmentResponse, LoginRequest, CurrentUserResponse,
│                    TicketCreateRequest, TicketUpdateRequest, TicketResponse, CategoryResponse, PriorityResponse
├── exception/       EmailAlreadyRegisteredException, CpfAlreadyRegisteredException,
│                    DepartmentNotFoundException, InvalidCredentialsException,
│                    AccountNotActiveException, InvalidSessionException, CategoryNotFoundException,
│                    PriorityNotFoundException, TicketNotFoundException, TicketEditForbiddenException,
│                    TicketNotEditableException
├── bootstrap/       AdminBootstrap (cria o primeiro admin), BootstrapAdminProperties (ADMIN_*)
├── model/           User, UserStatus, Department, Admin, DepartmentRequest, DepartmentRequestStatus,
│                    RefreshToken, Ticket, TicketStatus,
│                    Category, Priority
├── notification/    DepartmentRequestCreatedEvent, DepartmentRequestNotificationListener
├── repository/      UserRepository, AdminRepository, DepartmentRepository, DepartmentRequestRepository,
│                    RefreshTokenRepository, TicketRepository, CategoryRepository, PriorityRepository,
│                    NotificationCleanupRepository (JdbcTemplate, provisório até a SCRUM-152)
├── security/        JwtConfig + JwtProperties (chave, encoder, decoder), TokenService, AccessTokenCookie,
│                    RefreshTokenCookie, CookieBearerTokenResolver, Roles
├── service/         UserService, DepartmentService, AuthService (+ AuthenticatedSession, Emails),
│                    RefreshTokenService (+ RotatedRefreshToken), TicketService (+ TicketSpecifications), TicketCatalogService,
│                    AccountAnonymizationService
└── validation/      @Cpf + CpfValidator, @CorporateEmail + CorporateEmailValidator, @StrongPassword (regras de senha)
```

Previsto para notificações e avaliações: `model/` ganha `Notification`, `NotificationType` (com a categoria) e `NotificationCategory`, além de `TicketRating`; `repository/` ganha `NotificationRepository` e `TicketRatingRepository`; `service/` ganha `NotificationService` e `TicketRatingService`; `controller/` ganha `NotificationController` (doc em `NotificationApi`) e o endpoint de avaliação no `TicketController`.

Testes (`src/test/java/br/com/contabilizape/centraldesk/`):

```
├── IntegrationTest                 meta-anotação dos testes de integração (contexto e propriedades únicos)
├── CentralDeskApplicationTests      sobe o contexto inteiro (Postgres real, ddl-auto=validate)
├── bootstrap/AdminBootstrapTest               regras do primeiro admin (Mockito, 7 casos)
├── bootstrap/AdminBootstrapIntegrationTest    admin criado ao subir, sem duplicar (2 casos)
├── TestcontainersConfiguration      PostgreSQL 16 descartável no Docker (@ServiceConnection)
├── controller/AuthControllerIntegrationTest   POST /auth/register de ponta a ponta (8 cenários)
├── controller/DepartmentControllerIntegrationTest   GET /departments público e ordenado (4 cenários)
├── controller/AuthLoginIntegrationTest        POST /auth/login: cookie, 401, 403, cookie vencido ignorado (8)
├── controller/AuthLogoutIntegrationTest       POST /auth/logout: apaga os dois cookies com ou sem sessão (3)
├── controller/AuthRefreshIntegrationTest      rotação, reuso, vencido, conta inativa, logout revoga (11)
├── controller/UserControllerIntegrationTest   /users/me: GET (7), PUT nome (4), PATCH senha (5)
├── controller/TicketControllerIntegrationTest abertura, detalhes, edição, categorias e prioridades (20)
├── controller/TicketListIntegrationTest       /tickets/mine, /queue e /assigned: ordem, paginação, filtros, 400, 401, 403 (10)
├── controller/TicketAssignIntegrationTest     PATCH /tickets/{id}/assign: handler, admin, fila→atribuídos, 409, 403, 404, 401 (8)
├── controller/TicketCloseIntegrationTest      PATCH /tickets/{id}/close: handler, solicitante, admin, outro → 404, 409, corpo ignorado, 401 (8)
├── controller/AccountAnonymizationIntegrationTest  DELETE /users/me e PATCH /users/{id}/anonymize (10)
├── docs/ApiDocsIntegrationTest                OpenAPI e Swagger UI ligados (15 casos)
├── docs/ApiDocsDisabledIntegrationTest        sem API_DOCS_ENABLED: 404 (2 casos)
├── dto/UserCreateRequestValidationTest        regras do DTO (Bean Validation)
├── dto/PasswordChangeRequestValidationTest    senha atual obrigatória e  na nova (6)
├── service/UserServiceTest                    regras de negócio (JUnit 5 + Mockito, sem banco)
├── service/DepartmentServiceTest              listagem ordenada (Mockito, 2 casos)
├── service/AccountAnonymizationServiceTest   anonimização: dados, sessões, fila, notificações, último admin (Mockito, 10)
├── service/AuthServiceTest                    login, permissões, renovação, logout e troca de senha (Mockito, 16 casos)
├── service/RefreshTokenServiceTest            emissão, hash, rotação, reuso, revogação, revogar todas (Mockito, 11)
├── service/TicketServiceTest                  abertura, detalhes, edição, listagens, assumir e encerrar (Mockito, 29)
├── model/TicketTest                           máquina de estados: transições, assumir, devolver à fila, encerrar (17)
├── security/JwtConfigTest                     tamanho e formato da chave (3)
├── security/TokenServiceTest                  claims, validade, chave e emissor errados (5)
├── security/CookieBearerTokenResolverTest     leitura do cookie e rotas públicas (5)
├── security/AccessTokenCookieTest             atributos do cookie criado e do apagado (2)
├── security/RefreshTokenCookieTest            idem, com Path=/auth (2)
├── validation/CorporateEmailValidatorTest     domínio do e-mail (9 casos)
└── validation/CpfValidatorTest                algoritmo do CPF (16 casos)
```

### Rodando os testes

- Todos: `.\mvnw test` — **o Docker precisa estar rodando** (os testes de integração sobem o próprio Postgres; não usam o banco de desenvolvimento nem o `.env`).
- Uma classe: `.\mvnw test "-Dtest=UserServiceTest"` (aspas por causa do PowerShell).
- Relatório HTML: `.\mvnw surefire-report:report` (em `target\reports\`).
- Os testes de integração usam `ddl-auto=validate`, então também conferem se as entidades batem com as migrations.
- **Teste de integração novo usa `@IntegrationTest`** (`@SpringBootTest` + MockMvc + Testcontainers + propriedades que isolam do `.env`). Classes com as mesmas propriedades compartilham um único contexto e um único contêiner. Para mudar uma propriedade, `@TestPropertySource` na classe: tem precedência, mas cria outro contexto (outro contêiner), então use com moderação.
- Cada teste de integração apaga os usuários criados (preservando administradores, se houver) antes de rodar, com `DatabaseCleanup.removeNonAdminUsers(jdbc)`. A ordem respeita as chaves estrangeiras (`notifications` → `ticket_ratings` → `comments` → `tickets` → `refresh_tokens` → `department_requests` → `users`); toda tabela nova que aponte para `users` entra ali. **`notifications` e `ticket_ratings` entram na mesma entrega da V11/V12.**
- **Rastreabilidade:** toda execução relevante, **inclusive as que falham**, é registrada em `docs/testes/registro-de-execucoes.md` (comando, resultado, causa raiz, correção). Cada falha vira uma subtarefa `[BUG]` no Jira, com o rótulo `bug`.

Fluxo de uma requisição: `Controller (HTTP + DTO) → Service (regras, @Transactional) → Repository (consultas) → banco`.

### Notas de implementação

- Entidades: construtor `protected` vazio (exigido pelo JPA) e construtor público com os campos obrigatórios. `@Table(name = "users")` é obrigatório (`user` é palavra reservada no Postgres).
- `cpf` é `CHAR(11)`: mapear com `@JdbcTypeCode(SqlTypes.CHAR)`, senão o `validate` acusa *found bpchar, expecting varchar*.
- `Admin` usa `@MapsId` + `@OneToOne`: a PK é o próprio id do usuário, sem `@GeneratedValue`.
- Relações `@ManyToOne(fetch = LAZY)`.
- Injeção de dependências pelo construtor, não `@Autowired` em campo.
- Em `@Query`, o nome usado é o da **classe Java** (`Admin`), não o da tabela, e a entidade precisa de `@Entity`.
- `userRepository.saveAndFlush` no cadastro: o flush executa o `INSERT` na hora, e o `@CreationTimestamp` preenche o `createdAt` antes de a resposta ser montada.
- Validadores customizados ficam em `validation/` (não são "middlewares": não veem a requisição; são acionados pelo `@Valid`). O que participa da cadeia HTTP do Spring Security (leitura do cookie, JWT) fica em `security/`.
- Teste unitário do `TokenService` e do `JwtConfig` usa os beans reais, sem Spring: `new JwtConfig().jwtEncoder(...)`. O `TokenService` recebe um `Clock`, o que permite gerar tokens já vencidos nos testes.
- Validador que precisa de configuração (como o `CorporateEmailValidator`) recebe o valor pelo construtor com `@Value`: o Spring cria os validadores como beans. Em teste unitário sem Spring, instancia-se o validador passando o valor (ver `UserCreateRequestValidationTest`).
- `ticket_ratings.score` é `SMALLINT`: mapear como `short` (ou `Short`) no Java, senão o `validate` acusa divergência de tipo (*found int2, expecting integer*).

## 10. Requisitos funcionais

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

Decisão tomada: alteração de status (RF06) e encerramento (RF07) continuam **separados**, porque o encerramento tem regras próprias. O README lista requisitos complementares (RF08 a RF15: categorização, urgência, filtros, comentários, notificações).

Requisitos complementares (README): RF08 categorização, RF09 urgência, RF10 setor e responsável, RF11 listagem com filtros, RF12 detalhes, RF13 comentários, RF14 notificações por e-mail, RF15 notificações internas na interface. Incluídos em 20/10:

- **RF15** detalhado como **painel de notificações com filtro por categoria** (SCRUM-149).
- **RF16 — Avaliação do atendimento** pelo solicitante, depois do encerramento (novo; SCRUM-150). Precisa entrar no README e na documentação ABNT.

## 11. Documentação ABNT (Overleaf)

- Texto em português; termos técnicos em inglês em itálico.
- Na primeira menção: "chamado (denominado *ticket* no código-fonte)".
- O diagrama de estados do chamado é recomendado para ilustrar RF06 e RF07.
- O nome das colunas na documentação deve ser o mesmo das migrations.
- A documentação precisa incluir as tabelas `ticket_ratings` e `notifications` e os dois requisitos complementares de 20/10.

## 12. Pendências

### Roteiro (definido em 16/10/2026, atualizado em 20/10/2026)

0. **Fechar a SCRUM-146 (edição de chamado):** os commits estão na branch `feature/SCRUM-146-editar-chamado`. Falta registrar a execução 13 dos testes (182 esperados) e fazer o PR e o merge.
1. ✅ **Edição dos dados do usuário (RF02)** — implementada na branch `feature/SCRUM-147-edicao-dados-usuario` (20/10): `PUT /users/me` (perfil, com DTO restrito: sem senha, e-mail ou permissões) e `PATCH /users/me/password` (exige a senha atual). Fecha a parte de contas.
2. **Chamados:** listar (os meus e a fila da Inovação), assumir (`PATCH /tickets/{id}/assign`) e encerrar (`PATCH /tickets/{id}/close`), com as regras de transição de status (RF06 e RF07).
3. **Comentários** nos chamados.
4. **Gerenciamento de contas (admin):** aprovar e recusar `department_requests`, desativar contas (revogando as sessões na hora) e consultar usuários.
5. **Segregação por papel:** colaborador, `TICKET_HANDLER` e `ROLE_ADMIN` veem e podem fazer coisas diferentes. No back-end, as camadas de segurança são as regras por rota e por método (`@PreAuthorize`) e as checagens no service. O front-end só esconde as opções, e a API continua barrando sozinha.

**Histórias no Jira (20/10):** RF02 SCRUM-147 (subtarefas 156, 157, 158) · RF04 SCRUM-159 · RF11 SCRUM-160 · RF12 SCRUM-161 · RF06 SCRUM-162 · RF07 SCRUM-163 · RF13 SCRUM-164 · admin SCRUM-165 · RF15 SCRUM-149 (subtarefas 152 a 155) · RF16 SCRUM-150 · DEC-006 SCRUM-151.

**Decisões da RF02 (20/10):** `PUT /users/me` edita só o nome; `PATCH /users/me/password` exige a senha atual (errada → 400 em `errors.currentPassword`), aplica as regras de senha do cadastro e revoga as outras sessões, mantendo a atual.

**Implementação da RF02 (SCRUM-147, 20/10):** como o cookie `refresh_token` tem `Path=/auth`, ele não chega em `/users/me/password`. Em vez de mudar o `Path`, a troca de senha revoga **todas** as sessões e devolve, no próprio 204, cookies novos (`access_token` e `refresh_token`) para a sessão atual. As regras de senha ficaram na anotação composta `@StrongPassword` (pacote `validation/`), usada no cadastro e na troca. Erros de negócio ligados a um campo estendem `InvalidFieldException` e saem como 400 com `errors.<campo>`, no mesmo formato do Bean Validation.

**Implementação da RF04 (SCRUM-159, 20/10):** dispara pelos dois caminhos — o próprio usuário (`DELETE /users/me`, confirmando a senha; errada → 400 em `errors.password`) e o admin (`PATCH /users/{id}/anonymize`, só com a conta `DEACTIVATED`, senão 409; regra de rota `hasAuthority(ROLE_ADMIN)` no `SecurityConfig`). Na mesma transação: dados pessoais anonimizados (`User.anonymize`), todos os refresh tokens revogados, chamados `IN_PROGRESS` do usuário voltam a `OPEN` sem responsável (`Ticket.returnToQueue`; os `CLOSED` guardam o histórico), notificações apagadas (via `NotificationCleanupRepository`, até a entidade existir) e o registro em `admins` removido — recusado com 409 se for o último admin ativo.
**Implementação da RF12 (SCRUM-161, 20/10):** `GET /tickets/{id}` usa as permissões do token (como o `/users/me`) e decide a visibilidade em `TicketService.canView` (base para comentários e ações). Quem não pode ver recebe 404, não 403. O `TicketResponse` ganhou `assigneeName`, `assignedAt`, `closedById`, `closedByName`, `closedAt` e `updatedAt`; as listagens (SCRUM-160) usarão um resumo sem a descrição.
**Implementação da RF11 (SCRUM-160, 20/10):** `GET /tickets/mine` (qualquer usuário ativo), `/tickets/queue` (`OPEN` sem responsável) e `/tickets/assigned` (responsável = usuário logado, inclusive encerrados); fila e atribuídos só para `TICKET_HANDLER` e admin, por regra de rota no `SecurityConfig`. Resposta em `PageResponse<TicketSummaryResponse>` (`content`, `page`, `size`, `totalElements`, `totalPages`), formato próprio para não serializar o `PageImpl`. Ordem fixa por `created_at` decrescente (a ordenação enviada pelo cliente é ignorada); `size` padrão 20, máximo 100. Filtros por `Specification` (`TicketSpecifications`); o `findAll` paginado tem `@EntityGraph` para não fazer uma consulta por chamado.
**Implementação da RF06 (SCRUM-162, 20/10):** `PATCH /tickets/{id}/assign`, sem corpo, só `TICKET_HANDLER` e admin (regra de rota). **Máquina de estados:** as transições permitidas ficam em `TicketStatus.canTransitionTo` (`OPEN → IN_PROGRESS | CLOSED`, `IN_PROGRESS → OPEN | CLOSED`, `CLOSED` é final) e o único ponto que altera o status é `Ticket.moveTo`, usado por `assign` e `returnToQueue` (e, na SCRUM-163, por `close`); transição inválida → `InvalidTicketTransitionException` → 409 "Transição de status inválida". Para dois atendentes não assumirem o mesmo chamado, o service lê com `findByIdForUpdate` (`PESSIMISTIC_WRITE`, `SELECT ... FOR UPDATE`): o segundo espera, encontra `IN_PROGRESS` e recebe 409. O `TicketService` passou a receber o `Clock` (preenche `assigned_at`).
**Implementação da RF07 (SCRUM-163, 21/10):** `PATCH /tickets/{id}/close`, sem corpo (a avaliação é outro endpoint, `POST /tickets/{id}/rating`). Podem encerrar as mesmas pessoas que veem o chamado (`TicketService.canView`: solicitante, `TICKET_HANDLER`, admin); para os demais, 404. `Ticket.close` passa por `moveTo` (`OPEN`/`IN_PROGRESS` → `CLOSED`), preenche `closed_by_id` e `closed_at` e **mantém o responsável**, que é quem será avaliado. Já encerrado → 409. Também usa `findByIdForUpdate`.

**Notificações e avaliações (incluídas em 20/10), em fatias que acompanham o roteiro:**

- **Núcleo do painel (independente, pode começar já):** V12, entidade e repository, `NotificationService` (listar com filtro de categoria e não lidas, contador, marcar uma e todas como lidas), `NotificationController`, e o `DepartmentRequestNotificationListener` passando a gravar `DEPARTMENT_REQUEST_CREATED` em vez de só registrar no log.
- **Com o passo 2:** `TICKET_ASSIGNED` e `TICKET_CLOSED`; V11 e `POST /tickets/{id}/rating`. A avaliação depende do encerramento.
- **Com o passo 3:** `TICKET_COMMENTED`.
- **Com o passo 4:** `DEPARTMENT_REQUEST_APPROVED`, `DEPARTMENT_REQUEST_REJECTED` e `ADMIN_GRANTED`.
- **Depois:** SSE para atualizar o painel em tempo real e e-mail para os mesmos eventos.

**Processo combinado:** depois de cada merge no GitHub, rode `git switch main` e `git pull` antes de começar a branch seguinte, para que ela nasça da `main` atualizada.


- [ ] Trocar `spring.jpa.hibernate.ddl-auto` de `none` para `validate` e adicionar as duas linhas `server.error.*` ao `application.properties`.
- [ ] Endpoints de aprovação e recusa de `department_requests` (admin).
- [x] Login com JWT em cookie e authorities no token (SCRUM-137).
- [x] Logout que apaga o cookie (SCRUM-142).
- [x] Refresh token com rotação e detecção de reuso; logout revoga a sessão (SCRUM-143).
- [ ] Limpeza periódica de `refresh_tokens` e revogação imediata das sessões ao desativar uma conta.
- [x] Abertura de chamados (SCRUM-145) e edição pelo solicitante enquanto `OPEN` (SCRUM-146).
- [ ] Listar chamados (meus / fila da Inovação), assumir e encerrar, com as regras de transição de status (RF06, RF07).
- [x] Modelar as notificações: painel na interface com filtro por categoria (20/10, V12). E-mail fica para depois.
- [ ] Implementar o painel de notificações e substituir o log provisório do `DepartmentRequestNotificationListener`.
- [ ] Implementar a avaliação do atendimento (V11, `POST /tickets/{id}/rating`), depois do encerramento.
- [ ] Decidir com o coordenador quem vê as avaliações e se haverá prazo para avaliar.
- [ ] Limpeza periódica de notificações lidas há mais de N dias (junto da limpeza de `refresh_tokens`).
- [ ] Definir o significado de `validated_at` (hoje nasce nulo).
- [ ] Criptografia em repouso (AES-GCM) de comentários, descrições e comentários das avaliações.
- [x] Swagger (springdoc), documentando também o formato de erro `ProblemDetail` (SCRUM-122).
- [ ] Avisos da execução 3 dos testes: desligar `spring.jpa.open-in-view`; configurar o Mockito como agente do Surefire (ver `docs/testes/registro-de-execucoes.md`).
- [ ] Decidir se haverá status separado entre *Concluído* (técnico resolveu) e *Encerrado* (solicitante confirmou). Se sim: novo status `RESOLVED` e coluna `resolved_at`. **Adiado em 20/10**: a avaliação foi desenhada sobre `CLOSED`.
- [ ] Decidir se o painel terá uma fila ordenada por prioridade; se sim, criar o índice `(status, priority_id DESC, created_at)`.
- [ ] Incluir `department_requests`, `ticket_ratings` e `notifications` nos diagramas e alinhar a documentação ABNT com os nomes finais do banco.

## 13. Princípios de desenvolvimento

### Processo

- **Scrum** para gestão: sprints de 1 a 2 semanas no Jira (projeto `SCRUM`), coordenador da Inovação como *Product Owner*, revisão ao fim de cada sprint.
- **Práticas de engenharia do XP**: TDD, refatoração contínua, design simples, entregas pequenas. Pair programming adaptado: o assistente atua como navegador (propõe os testes, revisa, questiona); revisão humana nos pontos de integração com o front-end.

### TDD

- Ciclo vermelho → verde → refatorar: o teste vem **antes** do código de produção, inclusive nas entregas do assistente.
- **Testes unitários** (JUnit 5 + Mockito) para regras de negócio nos services e validadores.
- **Testes de integração** (`@SpringBootTest` + Testcontainers com PostgreSQL real) para endpoints e repositórios. Sem banco em memória: o comportamento precisa ser o do Postgres.
- O código do cadastro, escrito antes desta decisão, recebe testes retroativos (SCRUM-129).

### SOLID, aplicado com pragmatismo

- **Responsabilidade única**: um service por assunto (`UserService` para conta, `AuthService` para login e tokens). Controllers só traduzem HTTP.
- **Inversão de dependência**: interfaces onde há (ou certamente haverá) mais de uma implementação, como `EmailSender` (SMTP ou log). **Não** criar interface para service com implementação única.
- **Services não conhecem HTTP**: lançam exceções de domínio (`EmailAlreadyRegisteredException`...), e um `@RestControllerAdvice` as traduz em `ProblemDetail`. Implementado no cadastro em 16/10 (SCRUM-123).

### Clean Code

- Nomes que revelam a intenção, em inglês; métodos curtos, com um nível de abstração cada.
- Sem valores mágicos: regras configuráveis vão para `application.properties` (`app.*`).
- Comentários explicam o **porquê**, nunca o quê.
- Equilíbrio com a regra de simplicidade: nada de abstração "para o futuro" (YAGNI).

### Branches e fluxo de trabalho

Detalhado no `CONTRIBUTING.md`. Resumo:

- `main` sempre estável; nada é commitado direto nela. Todo trabalho entra por pull request (*squash and merge*).
- Proteção da `main`: o plano gratuito do GitHub não protege branches de repositório privado, então ela é feita por **hooks locais** em `.githooks/` (`pre-commit` e `pre-push`). Ative em toda cópia nova com `git config core.hooksPath .githooks`. Se a organização contratar o GitHub Team, a configuração do ruleset está no `CONTRIBUTING.md`.
- Branches curtas no formato `tipo/SCRUM-N-descricao`: `feature/`, `fix/`, `hotfix/` (urgente, em produção), `refactor/`, `test/`, `docs/`, `chore/` e `spike/` (experimento descartável, sem PR).
- Uma branch por item entregue de uma vez (normalmente a subtarefa).
- Commits no padrão Conventional Commits, em português, com `Refs: SCRUM-N`.
- Versões com tag `vMAJOR.MINOR.PATCH` na `main`.

## 14. Gestão no Jira

- Site `contabilizape.atlassian.net`, projeto **Innovation Unit** (chave `SCRUM`).
- O back-end do cadastro é acompanhado na história **SCRUM-118 — [RF00] Contas e Autenticação** e nas subtarefas dela.
- As tarefas de documentação seguem os códigos `DOC-`, `DEC-`, `DES-`, `QA-`, `OPS-` e `ABNT-`, agrupadas em épicos.

## 15. Orientações para o assistente

- Responder em **português**; código, nomes de tabelas, colunas, classes e rotas em **inglês**.
- **Preferir soluções simples**, proporcionais a um sistema interno com equipe de 3 pessoas. Evitar abstrações que o projeto ainda não precisa; quando algo mais robusto fizer sentido no futuro, registrar como trabalho futuro.
- Seguir as convenções e decisões deste documento. Se uma sugestão contradisser algo daqui, apontar a diferença explicitamente.
- Ao propor mudanças no banco, criar uma **nova migration**, nunca editar as existentes.
- Comandos para **PowerShell** (`.\mvnw`), não bash.
- Seguir a seção 13: em funcionalidades novas, entregar **primeiro a classe de testes** (vermelho) e só depois a implementação (verde).
- Trabalho novo começa numa branch (`CONTRIBUTING.md`); ao propor commits, sugerir também o nome da branch.
- Ao entregar código, entregar a **classe inteira** (com `package`, imports, campos e construtor), não só o trecho alterado.
- Explicar o porquê das escolhas, já que elas também alimentam a documentação ABNT.
