# Exportação de chamados em CSV

> **História (SCRUM-XXX):** "Eu, Coordenador, quero poder exportar as informações dos chamados em forma de CSV a fim de fazer análises alheias ao sistema."

## 1. Visão geral

O endpoint `GET /tickets/export` devolve um arquivo `chamados-AAAA-MM-DD.csv` com os chamados que a pessoa logada pode acompanhar. É para análises fora do CentralDesk (Excel, Power BI, Google Sheets). Não há paginação nem filtros: sai **toda a base dentro do escopo** de quem pede.

## 2. Quem pode exportar

O escopo é o mesmo da tela "Todos os chamados" (SCRUM-219). Administrador e coordenador são lidos **do banco** a cada requisição, nunca do token.

| Perfil | Recebe |
|---|---|
| Administrador | Todos os chamados, de todos os setores |
| Coordenador | Os chamados que o próprio setor **abriu (origem) ou recebeu (destino)** |
| Colaborador, Inovação sem papel de admin | `403 Acesso negado` |
| Sem sessão | `401` |
| Conta desativada durante a sessão | `403` (`AccountNotActiveException`) |

## 3. Conteúdo do arquivo

Uma linha por chamado, em ordem de número.

| Coluna | Origem | Observação |
|---|---|---|
| Número | `tickets.ticket_number` | |
| Título | `tickets.title` | |
| Descrição | `tickets.description` | Pode ter quebras de linha (vai entre aspas) |
| Solicitante | `users.full_name` (solicitante) | Conta anonimizada aparece como "Usuário removido" |
| Setor de origem | `departments.name` (origem) | Setor de quem abriu, gravado na abertura |
| Setor de destino | `departments.name` (destino) | |
| Responsável | `users.full_name` (responsável) | **"Em espera"** quando o chamado está na fila (sem responsável) |
| Status | `tickets.status` | Aberto, Em andamento, Encerrado |
| Aberto em | `tickets.created_at` | |
| Primeiro atendimento em | `tickets.first_assigned_at` | Vazio se nunca foi assumido |
| Encerrado em | `tickets.closed_at` | Vazio se não está encerrado |
| Atualizado em | `tickets.updated_at` | |

As datas estão no formato `dd/MM/yyyy HH:mm:ss`, no fuso da empresa (`app.tickets.time-zone`).

> "Setor de destino" e "Status" não estavam na lista original da história. Entraram porque, sem eles, a análise fica incompleta (o coordenador não distingue o que o setor abriu do que recebeu, nem o que já foi resolvido). Para tirar, basta remover a coluna em `TicketCsvWriter`.

### O que **não** sai

CPF, e-mail, IDs internos (UUID), comentários, resolução do encerramento, categoria, prioridade, quem encerrou e histórico de encerramentos. A consulta monta só as colunas acima (projeção `TicketExportRow`), então um campo novo em `Ticket` não vaza para o CSV por acidente.

## 4. Formato

- **Codificação:** UTF-8 **com BOM**, para o Excel em português abrir com os acentos certos ao dar dois cliques.
- **Separador:** `;` (o Excel em pt-BR usa a vírgula como separador decimal).
- **Quebra de linha:** CRLF (RFC 4180).
- **Escape:** campos com `;`, aspas ou quebra de linha vão entre aspas; as aspas internas são dobradas (`"` → `""`).
- **Proteção contra injeção de fórmula (CSV injection):** título, descrição e nomes são texto livre. Um campo que começa com `=`, `+`, `-`, `@`, tabulação ou retorno de carro ganha um apóstrofo (`'`) na frente, para a planilha mostrar o texto em vez de executar a fórmula.

Exemplo:

```
Número;Título;Descrição;Solicitante;Setor de origem;Setor de destino;Responsável;Status;Aberto em;Primeiro atendimento em;Encerrado em;Atualizado em
12;Impressora;"Não imprime; papel preso";Maria da Silva;Financeiro;Inovação;Em espera;Aberto;03/11/2026 10:05:09;;;03/11/2026 10:05:09
13;VPN caiu;Sem acesso;João Souza;Fiscal;Inovação;Carlos Lima;Em andamento;03/11/2026 11:20:00;03/11/2026 11:42:13;;03/11/2026 11:42:13
```

## 5. API

```
GET /tickets/export
Cookie: access_token=...
```

| Resposta | Quando |
|---|---|
| `200` `text/csv;charset=UTF-8` | Arquivo, com `Content-Disposition: attachment; filename="chamados-AAAA-MM-DD.csv"` e `Cache-Control: no-store` |
| `401` | Sem sessão |
| `403` `application/problem+json` | Não é administrador nem coordenador, ou a conta foi desativada |

Documentado no Swagger, na tag **Chamados** (`TicketExportApi`).

### Chamada no front-end (Vue)

O download precisa ir com o cookie de sessão e virar arquivo no navegador:

```js
const response = await api.get('/tickets/export', { responseType: 'blob' })
const url = URL.createObjectURL(response.data)
const link = Object.assign(document.createElement('a'), {
  href: url,
  download: `chamados-${new Date().toISOString().slice(0, 10)}.csv`,
})
link.click()
URL.revokeObjectURL(url)
```

O botão "Exportar CSV" só aparece para administrador e coordenador (mesma regra do menu "Todos os chamados"); o back-end barra os demais de qualquer forma.

## 6. Implementação

| Arquivo | Papel |
|---|---|
| `controller/TicketExportController.java` | `GET /tickets/export`: devolve o arquivo com os cabeçalhos de download |
| `controller/doc/TicketExportApi.java` | Documentação OpenAPI |
| `service/TicketExportService.java` | Decide o escopo (admin, coordenador ou 403) e busca as linhas |
| `service/TicketCsvWriter.java` | Monta o CSV: BOM, cabeçalho, escape, "Em espera", datas no fuso, proteção contra fórmula |
| `dto/TicketExportRow.java` | Projeção com só as colunas permitidas |
| `repository/TicketRepository.java` | `findAllForExport()` e `findSectorForExport(sector)`: uma consulta JPQL cada, com `left join` no responsável (sem N+1) |

O 403 reaproveita a `TicketOverviewForbiddenException`, que já está mapeada no `GlobalExceptionHandler`. Não há migração nova.

**Dependência:** a coluna "Primeiro atendimento em" usa `Ticket.firstAssignedAt` (migração `V25`), que vem da funcionalidade do painel de métricas. A branch da exportação precisa partir do `main` **depois** do merge das métricas.

**Volume:** o arquivo é montado em memória. Para a base de chamados internos da empresa (milhares de linhas) isso é suficiente. Se um dia passar de dezenas de milhares, trocar para `StreamingResponseBody` com consulta em `Stream`.

## 7. Testes

| Classe | Tipo | Cobre |
|---|---|---|
| `TicketCsvWriterTest` | Unitário | BOM e cabeçalho; uma linha por chamado com datas no fuso; "Em espera" e datas vazias; escape de `;`, aspas e quebra de linha; neutralização de fórmulas |
| `TicketExportServiceTest` | Unitário (Mockito) | Admin usa a consulta de tudo; coordenador, a do setor; colaborador recebe 403 sem consultar chamados; conta desativada; sessão inválida |
| `TicketExportIntegrationTest` | Integração (Testcontainers) | Admin baixa os 4 chamados com os cabeçalhos de download; coordenador só os do setor; nenhum CPF, e-mail ou UUID no arquivo; colaborador e Inovação sem admin recebem 403; sem sessão recebe 401 |

Comandos:

```powershell
.\mvnw test "-Dtest=TicketCsvWriterTest,TicketExportServiceTest"
.\mvnw test "-Dtest=TicketExportIntegrationTest"
.\mvnw test
```

Registrar as execuções (inclusive as que falharem) em `docs/testes/registro-de-execucoes.md`.

## 8. Critérios de aceite

1. Administrador baixa um CSV com todos os chamados do sistema.
2. Coordenador baixa um CSV só com os chamados que o setor dele abriu ou recebeu.
3. Colaborador e Inovação sem papel de administrador recebem 403.
4. O arquivo tem número, título, descrição, solicitante, setor, responsável ("Em espera" quando não há) e as datas.
5. O arquivo não tem CPF, e-mail, comentários nem IDs internos.
6. O arquivo abre no Excel em português com os acentos e as colunas certas, com dois cliques.
7. Fórmulas digitadas em títulos ou descrições não são executadas pela planilha.
