# CentralDesk

Painel web para registro e acompanhamento de chamados internos da Contabiliza, em substituição ao atendimento por e-mail.

## Problema

Hoje os chamados são registrados por e-mail, o que dificulta a organização, a priorização por urgência e o acompanhamento do andamento, tornando o atendimento lento e ineficiente.

## Solução

Um painel onde os colaboradores registram seus chamados, classificados por **categoria**, **prioridade** e **solicitante**, e onde o departamento de Inovação os atende. O sistema notifica os envolvidos por **e-mail** e dentro da **interface**.

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Front-end | Vue.js |
| Back-end | Java 21 + Spring Boot 4.1.1 (Spring Security, Spring Data JPA) |
| Banco de dados | PostgreSQL 16 (Docker), administrado com pgAdmin |
| Migrations | Flyway |
| Autenticação | JWT em cookies `HttpOnly` + refresh token; senhas com bcrypt |
| Identificadores | UUID |

## Requisitos funcionais

**Contas**

- **RF00**: criação de conta
- **RF01**: login
- **RF02**: edição dos dados do usuário
- **RF03**: logout
- **RF04**: encerramento de conta (anonimização, sem exclusão física)

**Chamados**

- **RF05**: abertura de chamados
- **RF06**: alteração de status (Em aberto, Em progresso, Encerrado)
- **RF07**: encerramento de chamados
- **RF08**: categorização no momento da abertura
- **RF09**: definição de urgência
- **RF10**: associação a setor e colaborador responsável
- **RF11**: listagem com filtros por status, categoria, urgência, setor e colaborador
- **RF12**: consulta aos detalhes e à última alteração do chamado
- **RF13**: comentários nos chamados
- **RF14**: notificações por e-mail
- **RF15**: notificações internas na interface

## Pré-requisitos

- Java 21 ou superior
- Docker e Docker Compose
- Node.js e npm (front-end)

O Maven não precisa ser instalado: o projeto usa o Maven Wrapper (`mvnw`).

## Como executar

1. Copie `.env.example` para `.env` e preencha as credenciais:

   ```env
   DB_URL=jdbc:postgresql://localhost:5432/chamados
   DB_USER=
   DB_PASSWORD=
   PGADMIN_EMAIL=
   PGADMIN_PASSWORD=

   # Primeiro administrador (criado ao subir a aplicação, se ainda não houver nenhum)
   ADMIN_NAME=
   ADMIN_CPF=
   ADMIN_EMAIL=
   ADMIN_PASSWORD=
   ```

   As variáveis `ADMIN_*` substituem qualquer dado pessoal em migration: o e-mail precisa ser `@contabilizape.com.br`, o CPF válido (só dígitos) e a senha com 8 a 72 caracteres, letra e número.

2. Suba o banco e o pgAdmin:

   ```powershell
   docker compose up -d
   ```

   - Banco: `localhost:5432`, banco `chamados`
   - pgAdmin: `http://localhost:5050` (ao registrar o servidor, use o host `db`)

3. Inicie o back-end, na raiz do projeto:

   ```powershell
   .\mvnw clean spring-boot:run
   ```

   No Linux, macOS ou WSL: `./mvnw clean spring-boot:run`. O Flyway aplica as migrations ao subir, e a API fica em `http://localhost:8080`.

## Estrutura do projeto

```
.
├── src/main/java/br/com/contabilizape/centraldesk/
│   ├── config/          # segurança e configurações
│   ├── controller/      # endpoints HTTP
│   ├── dto/             # objetos de entrada e saída da API
│   ├── model/           # entidades JPA
│   ├── notification/    # eventos e notificações
│   ├── repository/      # acesso ao banco
│   └── service/         # regras de negócio
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/    # migrations do Flyway (V1..V9)
├── docs/                # documentação ABNT (PDF)
├── docker-compose.yaml  # PostgreSQL e pgAdmin
├── CONTEXT.md           # decisões e estado do projeto
├── .env.example
└── README.md
```

## Rotas da API

| Método | Rota | Finalidade | Situação |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | Cadastrar novo usuário (público) | implementada |
| `GET` | `/departments` | Listar departamentos para o cadastro (público) | prevista |
| `POST` | `/auth/login` | Iniciar sessão | prevista |
| `POST` | `/auth/refresh` | Renovar a sessão | prevista |
| `POST` | `/auth/logout` | Encerrar sessão | prevista |
| `GET` | `/users/me` | Perfil do usuário logado | prevista |
| `PUT` | `/users/me` | Editar o próprio perfil | prevista |
| `PATCH` | `/users/me/password` | Trocar a senha | prevista |
| `GET` | `/users/{id}` | Consultar outro usuário (administrador) | prevista |
| `PATCH` | `/users/{id}/deactivate` | Desativar conta (administrador) | prevista |
| `GET` | `/department-requests` | Fila de pedidos de departamento (administrador) | prevista |
| `PATCH` | `/department-requests/{id}/approve` | Aprovar pedido (administrador) | prevista |
| `PATCH` | `/department-requests/{id}/reject` | Recusar pedido (administrador) | prevista |
| `POST` | `/tickets` | Abrir chamado | prevista |
| `PATCH` | `/tickets/{id}/assign` | Assumir chamado | prevista |
| `PATCH` | `/tickets/{id}/close` | Encerrar chamado | prevista |

As rotas de autenticação ficam sob `/auth`; a ação vem do método HTTP, não da URL. Detalhes e regras de negócio estão no [`CONTEXT.md`](CONTEXT.md).

## Status

Back-end em desenvolvimento: cadastro de conta implementado; login e autenticação são o próximo passo. O andamento é acompanhado no Jira (projeto **Innovation Unit**, chave `SCRUM`).

## Como contribuir

Branches, commits, pull requests e versões estão descritos em [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Documentação

A documentação formal, em LaTeX seguindo as normas ABNT, está em `docs/DOC_SISTEMA_CHAMADOS_0.1.pdf`.
