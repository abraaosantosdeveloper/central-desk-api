# CentralDesk — Contexto do Projeto

Este documento resume o estado do projeto e as decisões já tomadas. Ele serve como contexto para continuar o desenvolvimento em novas conversas: **as decisões listadas aqui já foram discutidas e não precisam ser reabertas**, a menos que eu peça.

Última atualização: 15/10/2026.

---

## 1. Visão geral

**CentralDesk** é o sistema web de chamados (*tickets*) internos da Contabiliza. Hoje os pedidos de suporte chegam por e-mail, o que torna o atendimento lento e desorganizado. O sistema oferece um painel onde os colaboradores abrem chamados, organizados por **categoria**, **prioridade** e **solicitante**, e onde a equipe de suporte os atende.

- Atende **todo tipo de suporte**, não só manutenção física.
- **Somente o departamento de Inovação atende chamados** (equipe de 3 pessoas, com um coordenador).
- Notificações: por e-mail (envio via **Resend**) e dentro da interface. **Ainda não modeladas**; hoje existe só o ponto de disparo (seção 6).
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

**categories** — `id` UUID PK · `label` VARCHAR(60) UNIQUE · `color` CHAR(7) (`#RRGGBB`, validado por CHECK). Cadastradas pelos administradores.

**priorities** — `id` SMALLINT PK · `label` VARCHAR(20) UNIQUE. Valores fixos: 1 Baixa, 2 Média, 3 Alta, 4 Crítica. O `id` define a ordem de urgência.

**tickets** — `id` UUID PK · `title` VARCHAR(120) · `description` TEXT · `status` (`OPEN`, `IN_PROGRESS`, `CLOSED`) · `category_id` FK · `priority_id` FK · `requester_id` FK · `assignee_id` FK nullable · `assigned_at` nullable · `closed_by_id` FK nullable · `closed_at` nullable · `created_at` · `updated_at`

**comments** — `id` UUID PK · `content` TEXT · `user_id` FK · `ticket_id` FK · `created_at` · `updated_at`

**refresh_tokens** — `id` UUID PK · `user_id` FK · `token_hash` CHAR(64) UNIQUE (SHA-256) · `created_at` · `expires_at` NOT NULL · `revoked_at` nullable

### Convenções do banco

- Nomes de tabelas e colunas em inglês, `snake_case`, minúsculas.
- Chaves primárias UUID com `DEFAULT gen_random_uuid()` no banco **e** `@GeneratedValue(strategy = GenerationType.UUID)` no Java. A aplicação gera o UUID antes do `INSERT`; o default do banco serve para inserts feitos fora da aplicação (seeds, pgAdmin). Os dois convivem sem conflito.
- Datas de eventos sempre `TIMESTAMPTZ` com `DEFAULT CURRENT_TIMESTAMP` (Java: `OffsetDateTime`, `@CreationTimestamp` / `@UpdateTimestamp`).
- Valores fixos ligados ao código (status): `VARCHAR` + `CHECK`, mapeados com `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
- Valores cadastráveis (categorias, departamentos): tabela própria com FK.
- **Soft delete**: usuários não são apagados; são desativados e depois anonimizados, para preservar o histórico dos chamados. Por isso as FKs não usam `ON DELETE CASCADE`.
- Índices em todas as FKs usadas em consultas frequentes, além de `(assignee_id, status)` em tickets, `(ticket_id, created_at)` em comments e `(status, created_at)` em department_requests.
- **Criptografia em repouso com AES-GCM**: comentários e descrições dos chamados ficam cifrados no banco, com a chave no servidor (variável `CONTENT_ENCRYPTION_KEY`), além do HTTPS. Consequência aceita: não há busca no conteúdo cifrado. Ainda não implementado (ADR no Jira: SCRUM-52).

### Diagramas

- Modelo conceitual (notação de Chen): `modelo_conceitual_chamados.drawio`
- Modelo lógico: `modelo_logico_chamados.dbml` (para o dbdiagram.io)
- Ambos ainda não incluem `department_requests`.

## 5. Regras de negócio

### Permissões

- **Colaborador comum**: abre chamados, comenta nos próprios chamados, edita o próprio perfil.
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

Métricas previstas: tempo de resposta (`assigned_at - created_at`) e tempo de resolução (`closed_at - created_at`).

### Desativação de conta (ex.: demissão)

Preenche `deactivated_at` e bloqueia o login imediatamente, revoga todos os refresh tokens do usuário e devolve à fila (`assignee_id = NULL`) os chamados que estavam com ele. A anonimização (LGPD) vem depois, sem apagar fisicamente o registro.

## 6. Notificações e tempo real

- **Atualizações em tempo real na interface via SSE** (Server-Sent Events), e não WebSocket: o fluxo é só do servidor para o cliente, com um stream único por usuário. Limite conhecido: funciona com uma única instância da aplicação; pub/sub entre instâncias fica como trabalho futuro (ADR no Jira: SCRUM-51).
- Canais de notificação no MVP (e-mail e interface, ou só interface e e-mail depois) ainda em decisão (SCRUM-44).
- E-mail: previsto com `spring-boot-starter-mail` (`JavaMailSender`) via SMTP — Resend com domínio próprio para testes, Mailpit no Docker para desenvolvimento, e SMTP da empresa (`@contabilizape.com.br`) como alvo de produção. Ainda não implementado.
- Disparadas por **eventos do Spring** (`ApplicationEventPublisher`), ouvidos com `@TransactionalEventListener(phase = AFTER_COMMIT)`: o ouvinte só roda depois que a transação foi gravada, e uma falha no envio não desfaz a operação.
- Implementado: `DepartmentRequestCreatedEvent` → `DepartmentRequestNotificationListener`, que busca os admins ativos (`UserRepository.findAdminsByStatus`). **Provisório**: por enquanto só registra no log (`Notificar ...`). Quando o envio pelo Resend e a notificação na interface forem modelados, só o ouvinte muda.

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

### Previsto (RF03)

- **Refresh token**: valor aleatório (`SecureRandom`, 32+ bytes), armazenado como **hash SHA-256** em `refresh_tokens`, em cookie com `Path=/auth/refresh`.
  - Rotação a cada uso: o atual é revogado e um novo é emitido. Reuso de token revogado revoga todos os tokens do usuário.
- **Logout** revoga o refresh token da sessão e apaga os cookies. Atenção: `/auth/logout` **não** pode entrar em `PUBLIC_ENDPOINTS`, porque precisa ler o cookie.

### `SecurityConfig` atual

- `PUBLIC_ENDPOINTS` é a lista única de rotas sem login: `POST /auth/register`, `POST /auth/login`, `GET /departments`, `/error` e as rotas da documentação (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`). A mesma lista libera o acesso e faz o cookie ser ignorado. Todo o resto exige um JWT válido.
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
| POST | `/auth/refresh` | rotação do refresh token | previsto |
| POST | `/auth/logout` | logout | previsto |
| GET | `/users/me` | dados e permissões do usuário logado | **implementado** |
| PUT | `/users/me` | edição do próprio perfil (DTO restrito: sem senha, e-mail ou permissões) | previsto |
| PATCH | `/users/me/password` | troca de senha (exige a senha atual) | previsto |
| GET | `/users/{id}` | consulta de outro usuário (admin) | previsto |
| PATCH | `/users/{id}/deactivate` | desativação de conta (admin) | previsto |
| GET | `/department-requests` | fila de pedidos pendentes (admin) | previsto |
| PATCH | `/department-requests/{id}/approve` | aprovar pedido (admin) | previsto |
| PATCH | `/department-requests/{id}/reject` | recusar pedido (admin) | previsto |
| POST | `/tickets` | abrir chamado | previsto |
| PATCH | `/tickets/{id}/assign` | assumir chamado | previsto |
| PATCH | `/tickets/{id}/close` | encerrar chamado | previsto |

No Spring, rotas literais como `/users/me` têm prioridade sobre `/users/{id}`.

### Documentação OpenAPI (Swagger)

- springdoc gera o OpenAPI a partir do código. Liga com `API_DOCS_ENABLED=true` (só em desenvolvimento): Swagger UI em `/swagger-ui.html`, JSON em `/v3/api-docs`.
- Os textos da documentação ficam em **interfaces** no pacote `controller/doc/` (`AuthApi`, `DepartmentApi`, `UserApi`), e o controller as implementa. Assim o controller fica só com o código (mapeamento HTTP e chamada ao service).
- **Todo endpoint novo** ganha, na interface: `@Operation` (resumo e regras) e um `@ApiResponse` para **cada status** que pode devolver. Erros usam `application/problem+json` com `schema = ProblemDetail.class` e exemplos de `ProblemExamples`, que precisam refletir o que o `GlobalExceptionHandler` devolve de fato.
- `springdoc.override-with-generic-response=false`: só aparecem as respostas declaradas, e não todos os erros do handler em todos os endpoints.
- DTOs com `@Schema` (descrição e exemplo). Senhas com `accessMode = WRITE_ONLY`.
- Endpoint protegido declara `security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)`. O esquema `cookieAuth` descreve o cookie `access_token`. No Swagger UI, depois do *Try it out* do login, o navegador envia o cookie sozinho (o botão *Authorize* não é necessário).
- `OpenApiConfig` define o título ("CentralDesk API"), a versão do contrato (`v1`) e a descrição geral, que explica o formato de erro.
- Os testes `ApiDocsIntegrationTest` (ligado) e `ApiDocsDisabledIntegrationTest` (desligado → 404) garantem o comportamento.

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
├── controller/      AuthController, DepartmentController, UserController, GlobalExceptionHandler
│   └── doc/         AuthApi, DepartmentApi, UserApi (documentação OpenAPI), ProblemExamples
├── dto/             UserCreateRequest, UserResponse, DepartmentResponse, LoginRequest, CurrentUserResponse
├── exception/       EmailAlreadyRegisteredException, CpfAlreadyRegisteredException,
│                    DepartmentNotFoundException, InvalidCredentialsException,
│                    AccountNotActiveException, InvalidSessionException
├── bootstrap/       AdminBootstrap (cria o primeiro admin), BootstrapAdminProperties (ADMIN_*)
├── model/           User, UserStatus, Department, Admin, DepartmentRequest, DepartmentRequestStatus
├── notification/    DepartmentRequestCreatedEvent, DepartmentRequestNotificationListener
├── repository/      UserRepository, AdminRepository, DepartmentRepository, DepartmentRequestRepository
├── security/        JwtConfig + JwtProperties (chave, encoder, decoder), TokenService, AccessTokenCookie,
│                    CookieBearerTokenResolver, Roles
├── service/         UserService, DepartmentService, AuthService (+ AuthenticatedSession, Emails)
└── validation/      @Cpf + CpfValidator, @CorporateEmail + CorporateEmailValidator
```

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
├── controller/UserControllerIntegrationTest   GET /users/me: sessão válida, sem cookie, chave errada, vencido (7)
├── docs/ApiDocsIntegrationTest                OpenAPI e Swagger UI ligados (7 casos)
├── docs/ApiDocsDisabledIntegrationTest        sem API_DOCS_ENABLED: 404 (2 casos)
├── dto/UserCreateRequestValidationTest        regras do DTO (Bean Validation)
├── service/UserServiceTest                    regras de negócio (JUnit 5 + Mockito, sem banco)
├── service/DepartmentServiceTest              listagem ordenada (Mockito, 2 casos)
├── service/AuthServiceTest                    regras do login e das permissões (Mockito, 8 casos)
├── security/JwtConfigTest                     tamanho e formato da chave (3)
├── security/TokenServiceTest                  claims, validade, chave e emissor errados (5)
├── security/CookieBearerTokenResolverTest     leitura do cookie e rotas públicas (5)
├── validation/CorporateEmailValidatorTest     domínio do e-mail (9 casos)
└── validation/CpfValidatorTest                algoritmo do CPF (16 casos)
```

### Rodando os testes

- Todos: `.\mvnw test` — **o Docker precisa estar rodando** (os testes de integração sobem o próprio Postgres; não usam o banco de desenvolvimento nem o `.env`).
- Uma classe: `.\mvnw test "-Dtest=UserServiceTest"` (aspas por causa do PowerShell).
- Relatório HTML: `.\mvnw surefire-report:report` (em `target\reports\`).
- Os testes de integração usam `ddl-auto=validate`, então também conferem se as entidades batem com as migrations.
- **Teste de integração novo usa `@IntegrationTest`** (`@SpringBootTest` + MockMvc + Testcontainers + propriedades que isolam do `.env`). Classes com as mesmas propriedades compartilham um único contexto e um único contêiner. Para mudar uma propriedade, `@TestPropertySource` na classe: tem precedência, mas cria outro contexto (outro contêiner), então use com moderação.
- Cada teste de integração apaga os usuários criados (preservando administradores, se houver) antes de rodar.
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

## 11. Documentação ABNT (Overleaf)

- Texto em português; termos técnicos em inglês em itálico.
- Na primeira menção: "chamado (denominado *ticket* no código-fonte)".
- O diagrama de estados do chamado é recomendado para ilustrar RF06 e RF07.
- O nome das colunas na documentação deve ser o mesmo das migrations.

## 12. Pendências

- [ ] Trocar `spring.jpa.hibernate.ddl-auto` de `none` para `validate` e adicionar as duas linhas `server.error.*` ao `application.properties`.
- [ ] Endpoints de aprovação e recusa de `department_requests` (admin).
- [x] Login com JWT em cookie e authorities no token (SCRUM-137).
- [ ] Refresh token com rotação e logout (RF03).
- [ ] Implementar os services com as regras de transição de status dos chamados.
- [ ] Modelar as notificações (e-mail via Resend e dentro da interface) e substituir o log provisório.
- [ ] Definir o significado de `validated_at` (hoje nasce nulo).
- [ ] Criptografia em repouso (AES-GCM) de comentários e descrições.
- [x] Swagger (springdoc), documentando também o formato de erro `ProblemDetail` (SCRUM-122).
- [ ] Avisos da execução 3 dos testes: desligar `spring.jpa.open-in-view`; configurar o Mockito como agente do Surefire (ver `docs/testes/registro-de-execucoes.md`).
- [ ] Decidir se haverá status separado entre *Concluído* (técnico resolveu) e *Encerrado* (solicitante confirmou). Se sim: novo status `RESOLVED` e coluna `resolved_at`.
- [ ] Decidir se o painel terá uma fila ordenada por prioridade; se sim, criar o índice `(status, priority_id DESC, created_at)`.
- [ ] Incluir `department_requests` nos diagramas e alinhar a documentação ABNT com os nomes finais do banco.

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
