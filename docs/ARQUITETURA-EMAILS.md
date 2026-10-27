# CentralDesk — Arquitetura dos e-mails de chamado (Resend → Gmail)

> **Atualização (27/10/2026):** a matriz de destinatários (seção 2) foi revista: encerrar avisa solicitante e
> responsável, reatribuir avisa também o responsável anterior, e há o e-mail de reabertura. A matriz em vigor
> está em `ADR-SEGURANCA-CONTAS.md` §8. A infraestrutura descrita aqui continua valendo.

Documento de orientação para implementar o **RF14 (notificações por e-mail)**. Escrito em
23/10/2026 a partir do código da `main` (último merge: PR #28) e da branch
`chore/SCRUM-178-ci-github-actions`. Segue o `GUIA-DE-DESENVOLVIMENTO.md` (padrões, TDD,
*Definition of Done*) e aponta explicitamente onde diverge do `CONTEXT.md`.

| Seção | Conteúdo |
|---|---|
| 1 | Estado atual do projeto e o que falta para os e-mails existirem |
| 2 | As regras de negócio, numa matriz única |
| 3 | Decisões tomadas (com o porquê) e decisões em aberto |
| 4 | Arquitetura: fluxo, pacotes e classes |
| 5 | Banco: número do chamado (V17) |
| 6 | Eventos de domínio e onde publicá-los |
| 7 | Planejamento dos destinatários (as regras em código) |
| 8 | Templates HTML: contrato de variáveis, assuntos e renderização |
| 9 | Envio pelo Resend: API, idempotência, *retry*, *threading* do Gmail |
| 10 | Configuração e variáveis de ambiente |
| 11 | Resend + Gmail: domínio, DNS e regras de HTML |
| 12 | Segurança e LGPD |
| 13 | Testes (TDD) |
| 14 | Plano de entrega (branches, Jira) e atualização do `CONTEXT.md` |

---

## 1. Estado atual

### O que já existe e será reaproveitado

| Peça | Onde | Uso nos e-mails |
|---|---|---|
| Eventos de domínio pós-commit | `@TransactionalEventListener` em `RealtimeEventListener` e `DepartmentRequestNotificationListener` | mesmo mecanismo: o e-mail só sai depois que a operação foi gravada |
| `requester` e `assignee` no `Ticket` | `model/Ticket.java` | o registro do chamado é quem rege os destinatários, como pedido |
| `closedBy`, `closedAt`, `resolution` | `Ticket.close` (SCRUM-174) | conteúdo do e-mail de encerramento |
| Regra "só solicitante e responsável atual comentam" | `CommentService.create` (SCRUM-172) | garante que todo comentário tem exatamente **um** "outro lado" |
| Admins ativos | `UserRepository.findAdminsByStatus` | base da consulta de administradores do setor de destino |
| Conteúdo do comentário decifrado | `EncryptedStringConverter` (AES-256-GCM) | ao ler o `Comment` pela JPA o texto já vem em claro; nada muda na cifra |
| `Clock` como bean (UTC) | `JwtConfig.clock()` | datas dos eventos; testes determinísticos |
| Fallback em log | `DepartmentRequestNotificationListener` ("Provisório: troca por e-mail (Resend)…") | vira o `LoggingEmailSender` de desenvolvimento |

### O que **não** existe e é pré-requisito

1. **Número do chamado.** `tickets.id` é UUID e não há coluna sequencial. O assunto
   "Nova mensagem no chamado #<ticket_number>" exige um número legível. → **V17** (seção 5).
2. **Setor de destino no chamado.** A regra 1 fala em "administradores do departamento de
   destino", mas `tickets` não tem `target_department_id`; o setor de destino é a SCRUM-148
   (ainda não implementada; hoje é sempre a Inovação, fixa). → O e-mail usa a Inovação
   (`app.tickets.handler-department-id`) até a SCRUM-148 entrar, e a consulta já nasce
   parametrizada pelo departamento (seção 7), para não ser reescrita depois.
3. **Nenhuma infraestrutura de e-mail:** sem dependência no `pom.xml`, sem `@EnableAsync`,
   sem variáveis `RESEND_*`/`EMAIL_*` no `.env.example`.
4. **Eventos para assumir, reatribuir e encerrar.** Hoje só abertura (`TicketCreatedEvent`,
   para o SSE) e comentário (`CommentCreatedEvent`, para o SSE) publicam eventos, e os dois
   carregam DTOs de tela. `assign`, `reassign` e `close` não publicam nada.

### Divergências com o `CONTEXT.md` e com o guia (precisam de decisão registrada)

| Onde | O que está escrito | O que este documento propõe | Por quê |
|---|---|---|---|
| `CONTEXT.md` §6, Infraestrutura | e-mail via `spring-boot-starter-mail` (`JavaMailSender`) por **SMTP** | **API HTTPS do Resend** (`POST https://api.resend.com/emails`) com o `RestClient` do Spring | O CentralDesk está no **Railway**, que só libera SMTP de saída no plano **Pro** e recomenda APIs HTTPS (cita o Resend como preferido). A API também dá chave de idempotência e *headers* de *rate limit*. |
| `GUIA` §5, Roteiro | e-mail não aparece; notificações do painel (SCRUM-152 a 154) vêm antes | e-mails entram agora, por decisão do usuário em 23/10 | O roteiro diz que não é reordenado "sem decisão explícita": esta é a decisão, e precisa ir para o guia no mesmo PR. Os eventos daqui são os mesmos que o painel vai consumir (SCRUM-153/154), então nada é retrabalho. |
| `GUIA` §2, Integração | lista fechada de padrões | entra **processamento assíncrono** (`@Async` com executor próprio) | sem ele, a chamada HTTP ao Resend aumentaria a latência de cada ação do usuário. Registrar como ADR no Jira. |

---

## 2. Regras de negócio (matriz única)

O registro do chamado (`requester_id`, `assignee_id`) decide os destinatários. Regras gerais,
valendo para todas as linhas:

- **Ninguém recebe e-mail de uma ação que ele mesmo fez** (mesma regra do painel, §6 do `CONTEXT.md`).
- **Só contas `ACTIVE` recebem.** Conta desativada ou anonimizada (`@anonimizado.invalid`) é
  ignorada em silêncio.
- **Um e-mail por destinatário** (nunca vários endereços no mesmo `to`): ninguém vê o endereço
  dos outros e cada envio tem a própria chave de idempotência.

| # | Gatilho (método) | Destinatário | Assunto | Template |
|---|---|---|---|---|
| 1a | Abrir (`TicketService.open`) | solicitante | `Chamado Aberto` | `ticket-opened-requester` |
| 1b | Abrir (`TicketService.open`) | cada **admin ativo do setor de destino**, exceto o próprio solicitante | `Chamado Aberto` | `ticket-opened-department` |
| 2 | Assumir (`TicketService.assign`) | solicitante | `Chamado em andamento` | `ticket-in-progress` |
| 3 | Reatribuir (`TicketReassignmentService.reassign`) | **novo** responsável (se não foi ele quem reatribuiu) | `Chamado reatribuído` | `ticket-reassigned` |
| 3b | Reatribuir **a partir da fila** (`OPEN` → `IN_PROGRESS`) | solicitante | `Chamado em andamento` | `ticket-in-progress` |
| 4 | Comentário do **solicitante** (`CommentService.create`) | responsável **no momento do comentário** (se houver) | `Nova mensagem no chamado #<n>` | `ticket-new-message` |
| 5 | Comentário do **responsável** (`CommentService.create`) | solicitante | `Nova mensagem no chamado #<n>` | `ticket-new-message` |
| 6 | Encerrar (`TicketService.close`) | **apenas** o solicitante | `Chamado Encerrado` | `ticket-closed` |

Observações que saem do código atual:

- **Linha 4 com o chamado na fila:** sem responsável, o comentário do solicitante não gera
  e-mail (não há para quem mandar). A fila já avisa a Inovação pelo SSE.
- **Linha 4 após reatribuição:** o responsável é lido **dentro da transação do comentário** e
  gravado no evento. O responsável anterior não recebe mais nada, exatamente como pedido.
- **Solicitante = responsável** passou a existir com a SCRUM-207 (admin que assume a própria demanda):
  o comentário nesse caso não tem outro lado e não gera e-mail. A matriz em vigor é a do
  `ADR-SEGURANCA-CONTAS.md` §8 (PLANO-SEGURANCA §13.1), que substitui a tabela acima.
- **Encerramento por um admin que não é o responsável:** o e-mail diz "encerrado por
  <nome de quem encerrou>" (`closedBy`), não pelo responsável. É o que aconteceu de fato.
- **1b quando o solicitante é admin da Inovação:** ele recebe só a versão de solicitante (1a).
- **Linha 3b** é decisão em aberto (D1): ver seção 3.

---

## 3. Decisões

### Tomadas neste desenho

| Id | Decisão | Por quê |
|---|---|---|
| E1 | **Evento leva IDs, não nomes**; os dados do e-mail são relidos numa thread separada | Responde à sua dúvida de latência: a releitura é 1 consulta com *fetch join* (+1 para os admins, +1 para o comentário), alguns milissegundos, e acontece **depois** da resposta HTTP, numa thread do executor de e-mail. O usuário não espera nada. Ganhos: evento pequeno, sem `LazyInitializationException` (as entidades já estão desanexadas após o commit) e sem copiar dado pessoal para dentro do evento. |
| E2 | **Destinatários pessoais são resolvidos no momento da ação** e vão no evento (`recipientId`) | Regra 4: "o responsável naquele momento". Se a resolução ficasse para a thread assíncrona, uma reatribuição milissegundos depois mudaria o destinatário. Requester/assignee já estão em memória na transação: custo zero. Só a lista de admins (regra 1b) é consultada depois, porque não depende do instante. |
| E3 | **Assíncrono com `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`** e executor dedicado (2 threads) | A ação responde sem esperar o Resend; o limite padrão do Resend é **10 req/s por time**, e 2 threads ficam longe dele. |
| E4 | **Montar a mensagem dentro de uma transação somente leitura e enviar fora dela** | Enviar HTTP dentro da transação prenderia uma conexão do pool do banco durante a chamada ao Resend. |
| E5 | **Templates no repositório, renderizados com Thymeleaf** (`src/main/resources/templates/email/`) | Versionados no Git, revisados no PR, testáveis; *escape* de HTML automático com `th:text` (nomes e comentários são entrada do usuário). Os templates hospedados no painel do Resend foram descartados: ficariam fora do Git e do TDD. |
| E6 | **Assunto fixo no código** (`EmailTemplate`), com o número do chamado como único dado variável | Nenhum texto digitado por usuário entra no assunto: elimina injeção de cabeçalho e mantém os assuntos exatamente como definidos. |
| E7 | **Falha no envio não desfaz nada e não é reenviada depois de esgotar as tentativas** (só log de erro) | Mesma filosofia do SSE. Uma tabela *outbox* com reenvio persistente fica como trabalho futuro (seção 14), proporcional a uma equipe de 3 pessoas. |
| E8 | **Sem chave do Resend → `LoggingEmailSender`** | Desenvolvimento e CI sobem sem segredo, como o log provisório de hoje. |

### Em aberto (decidir antes da subtarefa correspondente; padrão proposto em negrito)

| Id | Pergunta | Padrão proposto |
|---|---|---|
| D1 | Admin reatribui um chamado **que estava na fila**: o solicitante recebe "Chamado em andamento"? Para ele, o efeito é idêntico a "assumir". | **Sim** (linha 3b). Na reatribuição de um chamado já em andamento, o solicitante não é avisado (regra 4 literal). |
| D2 | O e-mail de comentário leva o **texto** da mensagem? Os comentários são cifrados em repouso; no e-mail eles saem em claro para o Gmail e ficam no log do Resend. | **Sim, limitado a 1.500 caracteres** com "continue lendo no CentralDesk" (também evita o corte do Gmail em ~102 KB). Registrar no ADR que o canal e-mail aceita esse texto. |
| D3 | Assuntos idênticos ("Chamado Aberto" para chamados diferentes) fazem o Gmail **agrupar e-mails de chamados diferentes na mesma conversa**. | **Manter os assuntos definidos** e enviar o cabeçalho `X-Entity-Ref-ID` com valor único por e-mail, recurso que o Resend documenta para impedir o agrupamento no Gmail. Alternativa: sufixo ` — #<n>` em todos os assuntos. |
| D4 | Endereço remetente e domínio verificado no Resend. | **`CentralDesk <centraldesk@notificacoes.contabilizape.com.br>`**, num subdomínio, para não mexer no SPF do domínio principal (Google Workspace). Precisa de quem administra o DNS. |
| D5 | Rota do front para o link "Abrir chamado". | **`{FRONT_BASE_URL}/chamados/{id}`** (confirmar a rota real do Angular). |

---

## 4. Arquitetura

### Fluxo

```
TicketService.open / assign / close            ┐
TicketReassignmentService.reassign             ├─ publishEvent(TicketXxxEvent)   ← dentro da transação
CommentService.create                          ┘          │
                                                          ▼  (commit)
                                  TicketEmailListener  @Async("emailExecutor")
                                                       @TransactionalEventListener(AFTER_COMMIT)
                                                          │
                                  TicketEmailPlanner   @Transactional(readOnly = true)
                                    ├─ relê o chamado (1 consulta com fetch join)
                                    ├─ aplica a matriz da seção 2 → List<PlannedEmail>
                                    └─ EmailRenderer (Thymeleaf) → List<EmailMessage>  (assunto + HTML)
                                                          │   (transação já fechada)
                                                          ▼
                                  EmailSender  (interface)
                                    ├─ ResendEmailSender   → POST https://api.resend.com/emails
                                    └─ LoggingEmailSender  → log (dev / CI / sem chave)
```

### Pacotes e classes novas

```
br.com.contabilizape.centraldesk
├── event/                         eventos de domínio do chamado (servem ao e-mail agora e ao painel depois)
│   ├── TicketOpenedEvent
│   ├── TicketAssignedEvent
│   ├── TicketReassignedEvent
│   ├── TicketClosedEvent
│   └── TicketCommentedEvent
├── email/
│   ├── EmailTemplate              enum: arquivo do template + assunto
│   ├── EmailMessage               record: to, subject, html, idempotencyKey, tags
│   ├── EmailSender                interface (duas implementações: regra do guia para criar interface)
│   ├── ResendEmailSender          RestClient + retry em 429/5xx
│   ├── LoggingEmailSender         fallback
│   ├── EmailRenderer              Thymeleaf (TemplateEngine) → HTML
│   ├── EmailProperties            @ConfigurationProperties("app.email")
│   ├── EmailConfig                @EnableAsync, emailExecutor, escolha do EmailSender
│   ├── TicketEmailPlanner         a matriz da seção 2 + montagem das variáveis
│   ├── TicketEmailListener        ouve os eventos, chama planner e sender
│   └── model/                     um record de variáveis por template (seção 8)
└── repository/
    ├── TicketRepository           + findForEmail(id)  (EntityGraph)
    └── UserRepository             + findActiveAdminsInDepartment(departmentId)
```

`EmailSender` é a única interface nova, e tem duas implementações reais desde o primeiro dia
(Resend e log), como o próprio guia exemplifica. `TicketEmailPlanner` e `EmailRenderer` são
classes concretas.

---

## 5. Banco: número do chamado (V17)

Migration nova (nunca editar as existentes). Numera os chamados que já estão em produção em
ordem de abertura e deixa o Postgres gerar os próximos.

`src/main/resources/db/migration/V17__add_ticket_number.sql`

```sql
-- Número legível do chamado (#123), usado nos e-mails e na interface.
-- O id continua UUID; o número não é chave estrangeira de ninguém.
ALTER TABLE tickets ADD COLUMN ticket_number BIGINT;

-- Chamados que já existem: numerados na ordem em que foram abertos
UPDATE tickets t
SET ticket_number = numbered.n
FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS n FROM tickets) numbered
WHERE t.id = numbered.id;

ALTER TABLE tickets ALTER COLUMN ticket_number SET NOT NULL;
ALTER TABLE tickets ALTER COLUMN ticket_number ADD GENERATED BY DEFAULT AS IDENTITY;

-- A sequência continua depois do maior número já usado
SELECT setval(pg_get_serial_sequence('tickets', 'ticket_number'),
              COALESCE((SELECT MAX(ticket_number) FROM tickets), 0) + 1, false);

ALTER TABLE tickets ADD CONSTRAINT uq_tickets_ticket_number UNIQUE (ticket_number);
```

No `Ticket`:

```java
// Gerado pelo banco (identity, V17); lido de volta no flush da abertura
@Generated
@Column(name = "ticket_number", insertable = false, updatable = false, nullable = false)
private Long number;

public Long getNumber() { return number; }
```

(`org.hibernate.annotations.Generated`: o `saveAndFlush` do `open` já existe e passa a trazer o
número junto com o `createdAt`.)

Efeitos colaterais na mesma subtarefa:

- `TicketResponse` e `TicketSummaryResponse` ganham `Long number` (o front passa a mostrar `#123`).
- O filtro `q` das listagens pode aceitar o número (`#123` ou `123`): opcional, anotar no Jira.
- `DatabaseCleanup` não muda (coluna nova, tabela antiga).
- Teste de integração: dois chamados abertos em sequência recebem números consecutivos.

---

## 6. Eventos de domínio

Todos são `record` com IDs e um `eventId` próprio. O `eventId` é a base da chave de
idempotência (seção 9): reatribuir A → B → A no mesmo dia gera dois e-mails legítimos para A,
e uma chave baseada só em chamado + destinatário engoliria o segundo.

```java
package br.com.contabilizape.centraldesk.event;

/** Chamado aberto. O setor de destino vira campo quando a SCRUM-148 entrar. */
public record TicketOpenedEvent(UUID eventId, UUID ticketId, UUID requesterId) {
    public static TicketOpenedEvent of(Ticket ticket) {
        return new TicketOpenedEvent(UUID.randomUUID(), ticket.getId(), ticket.getRequester().getId());
    }
}

/** OPEN → IN_PROGRESS por quem assumiu. */
public record TicketAssignedEvent(UUID eventId, UUID ticketId, UUID assigneeId, UUID requesterId) { … }

/** Troca de responsável. fromQueue = o chamado estava OPEN (decisão D1). */
public record TicketReassignedEvent(UUID eventId, UUID ticketId, UUID actorId,
                                    UUID newAssigneeId, UUID requesterId, boolean fromQueue) { … }

/** Encerramento. closedById pode ser um admin que não era o responsável. */
public record TicketClosedEvent(UUID eventId, UUID ticketId, UUID closedById, UUID requesterId) { … }

/**
 * Comentário gravado. recipientId é o "outro lado" NO MOMENTO do comentário
 * (nulo quando o solicitante comenta com o chamado na fila).
 */
public record TicketCommentedEvent(UUID eventId, UUID ticketId, UUID commentId,
                                   UUID authorId, UUID recipientId) { … }
```

### Onde publicar (uma linha em cada método, depois do `flush`)

| Método | Linha nova |
|---|---|
| `TicketService.open` | `eventPublisher.publishEvent(TicketOpenedEvent.of(ticket));` (ao lado do `TicketCreatedEvent` do SSE, que continua) |
| `TicketService.assign` | `eventPublisher.publishEvent(TicketAssignedEvent.of(ticket));` |
| `TicketService.close` | `eventPublisher.publishEvent(TicketClosedEvent.of(ticket));` |
| `TicketReassignmentService.reassign` | guardar `boolean fromQueue = ticket.isOpen();` **antes** do `ticket.reassign(...)`, depois publicar `TicketReassignedEvent.of(ticket, userId, fromQueue)`. O service ganha `ApplicationEventPublisher` no construtor. |
| `CommentService.create` | `UUID recipientId = otherParticipant(ticket, author);` e publicar `TicketCommentedEvent` (ao lado do `CommentCreatedEvent` do SSE) |

```java
// Solicitante comenta → responsável atual (pode não haver); responsável comenta → solicitante
private static UUID otherParticipant(Ticket ticket, User author) {
    if (ticket.getRequester().getId().equals(author.getId())) {
        return ticket.getAssignee() == null ? null : ticket.getAssignee().getId();
    }
    return ticket.getRequester().getId();
}
```

Os eventos de SSE (`TicketCreatedEvent`, `CommentCreatedEvent`) ficam como estão: carregam
DTOs de tela e têm outros destinatários. Quando o painel de notificações (SCRUM-153/154)
for implementado, ele ouve os eventos de `event/`, não os do SSE.

---

## 7. Planejamento dos destinatários

### Consultas novas

```java
// TicketRepository — tudo o que os templates mostram, numa consulta
@EntityGraph(attributePaths = {"category", "priority", "requester", "assignee", "closedBy"})
@Query("select t from Ticket t where t.id = :id")
Optional<Ticket> findForEmail(@Param("id") UUID id);

// UserRepository — administradores ativos lotados no setor de destino.
// Hoje todo admin está na Inovação (regra de permissões), então o resultado = admins ativos;
// com a SCRUM-148, o departamento passa a vir do chamado e esta consulta não muda.
@Query("select a.user from Admin a where a.user.status = :status and a.user.department.id = :departmentId")
List<User> findAdminsInDepartment(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);
```

### `TicketEmailPlanner` (as regras num lugar só)

Um método por evento, todos `@Transactional(readOnly = true)`, cada um devolvendo
`List<EmailMessage>` já renderizadas. Esboço da abertura:

```java
@Transactional(readOnly = true)
public List<EmailMessage> plan(TicketOpenedEvent event) {
    Ticket ticket = ticketRepository.findForEmail(event.ticketId()).orElse(null);
    if (ticket == null) return List.of();                      // apagado entre o commit e aqui: nada a fazer

    List<EmailMessage> messages = new ArrayList<>();
    User requester = ticket.getRequester();
    addIfActive(messages, requester, EmailTemplate.TICKET_OPENED_REQUESTER, event.eventId(),
            TicketOpenedModel.forRequester(ticket, requester, departmentName(), links, formatter));

    for (User admin : userRepository.findAdminsInDepartment(targetDepartmentId, UserStatus.ACTIVE)) {
        if (admin.getId().equals(requester.getId())) continue;  // já recebeu a versão de solicitante
        addIfActive(messages, admin, EmailTemplate.TICKET_OPENED_DEPARTMENT, event.eventId(),
                TicketOpenedModel.forDepartment(ticket, admin, departmentName(), links, formatter));
    }
    return messages;
}
```

Os demais seguem a matriz da seção 2:

- `TicketAssignedEvent` → solicitante, `TICKET_IN_PROGRESS`.
- `TicketReassignedEvent` → novo responsável, `TICKET_REASSIGNED`, **exceto** se
  `newAssigneeId == actorId`; e, se `fromQueue` (D1), também o solicitante com `TICKET_IN_PROGRESS`.
- `TicketCommentedEvent` → se `recipientId == null`, nada; senão carrega o `Comment` pelo
  `commentId` (texto já decifrado pelo converter) e envia `TICKET_NEW_MESSAGE` a `recipientId`.
- `TicketClosedEvent` → só o solicitante, `TICKET_CLOSED`.

`addIfActive` concentra as regras gerais: ignora conta não `ACTIVE` e monta a chave de
idempotência `"{template}/{eventId}/{recipientId}"`.

### `TicketEmailListener`

```java
@Component
public class TicketEmailListener {

    private final TicketEmailPlanner planner;
    private final EmailSender sender;

    // Depois do commit e fora da thread da requisição: o usuário não espera o Resend,
    // e uma falha aqui não desfaz a operação
    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketOpenedEvent event) {
        planner.plan(event).forEach(sender::send);   // a transação do planner já fechou aqui
    }
    // … um método por evento, mesmo formato
}
```

O `sender.send` nunca lança para fora (seção 9): uma falha para um admin não impede os demais.

---

## 8. Templates HTML

### Onde ficam e como se chamam

```
src/main/resources/templates/email/
├── ticket-opened-requester.html     1a  Chamado Aberto (para o solicitante)
├── ticket-opened-department.html    1b  Chamado Aberto (para os admins do setor)
├── ticket-in-progress.html          2   Chamado em andamento
├── ticket-reassigned.html           3   Chamado reatribuído
├── ticket-new-message.html          4/5 Nova mensagem no chamado #<n>
└── ticket-closed.html               6   Chamado Encerrado
```

### Seleção dinâmica: `EmailTemplate`

```java
public enum EmailTemplate {
    TICKET_OPENED_REQUESTER ("ticket-opened-requester",  "Chamado Aberto"),
    TICKET_OPENED_DEPARTMENT("ticket-opened-department", "Chamado Aberto"),
    TICKET_IN_PROGRESS      ("ticket-in-progress",       "Chamado em andamento"),
    TICKET_REASSIGNED       ("ticket-reassigned",        "Chamado reatribuído"),
    TICKET_NEW_MESSAGE      ("ticket-new-message",       "Nova mensagem no chamado #%d"),
    TICKET_CLOSED           ("ticket-closed",            "Chamado Encerrado");

    private final String file;
    private final String subjectPattern;

    EmailTemplate(String file, String subjectPattern) { … }

    /** Caminho resolvido pelo Thymeleaf: templates/email/<file>.html */
    public String view() { return "email/" + file; }

    public String subject(long ticketNumber) { return subjectPattern.formatted(ticketNumber); }
}
```

(`formatted` ignora o número nos assuntos sem `%d`.) Trocar um assunto é mudar uma linha e o
teste do enum.

### Renderização

Dependência nova: `spring-boot-starter-thymeleaf`. O starter configura o `TemplateEngine` com
`classpath:/templates/` e `.html`; o `EmailRenderer` usa o motor diretamente (não há
controller MVC envolvido):

```java
@Component
public class EmailRenderer {
    private final ITemplateEngine engine;

    public String render(EmailTemplate template, Map<String, Object> variables) {
        Context context = new Context(Locale.forLanguageTag("pt-BR"));
        context.setVariables(variables);
        return engine.process(template.view(), context);
    }
}
```

O texto alternativo (`text`) **não** é enviado: quando ele é omitido, o Resend gera a versão
em texto a partir do HTML.

### Contrato de variáveis (o que os seus templates podem usar)

Todas as variáveis chegam **já formatadas como texto** (datas em `America/Recife`,
`dd/MM/yyyy` e `HH:mm`): o template não formata nada. Use sempre `th:text` (com *escape*),
nunca `th:utext`, porque nomes, títulos, comentários e resoluções são digitados por usuários.

**Comuns a todos os templates**

| Variável | Exemplo | Origem |
|---|---|---|
| `recipientName` | `Maria Souza` | destinatário |
| `ticketNumber` | `123` | `ticket.number` |
| `ticketTitle` | `Impressora do financeiro não imprime` | `ticket.title` |
| `ticketUrl` | `https://centraldesk…/chamados/<uuid>` | `app.email.front-base-url` + id (D5) |
| `categoryName` | `Hardware` | `ticket.category` |
| `priorityName` | `Alta` | `ticket.priority` |
| `requesterName` | `João Lima` | `ticket.requester` |
| `departmentName` | `Inovação` | setor de destino |
| `openedDate` / `openedTime` | `23/10/2026` / `11:45` | `ticket.createdAt` |

**Específicas**

| Template | Variáveis extras |
|---|---|
| `ticket-opened-requester` | — |
| `ticket-opened-department` | — (use `requesterName` para "João Lima abriu um chamado") |
| `ticket-in-progress` | `assigneeName`, `assignedDate`, `assignedTime` |
| `ticket-reassigned` | `reassignedByName`, `reassignedDate`, `reassignedTime`, `ticketStatus` (`Na fila` / `Em andamento`) |
| `ticket-new-message` | `authorName`, `authorRole` (`solicitante` / `responsável`), `messageLines` (lista de linhas), `messageTruncated` (boolean), `sentDate`, `sentTime` |
| `ticket-closed` | `closedByName`, `closedDate`, `closedTime`, `resolutionLines` (lista de linhas) |

Exemplo do trecho exigido pela regra 3 ("encerrado por <NOME_FUNCIONARIO> às hh:mm de dd/mm/aaaa"):

```html
<p style="margin:0 0 16px;font:16px/1.5 Arial,sans-serif;color:#1f2937;">
  O chamado <strong th:text="'#' + ${ticketNumber}">#123</strong> foi encerrado por
  <strong th:text="${closedByName}">Fulano</strong>
  às <span th:text="${closedTime}">14:30</span> de <span th:text="${closedDate}">23/10/2026</span>.
</p>

<!-- Quebras de linha de texto do usuário, sem th:utext -->
<p style="margin:0;font:14px/1.5 Arial,sans-serif;color:#374151;">
  <th:block th:each="line, it : ${resolutionLines}">
    <span th:text="${line}">linha</span><br th:unless="${it.last}"/>
  </th:block>
</p>
```

O conteúdo entre as tags (`Fulano`, `14:30`…) é só para visualizar o arquivo no navegador:
o Thymeleaf substitui na renderização. Assim você desenha o HTML abrindo o arquivo direto.

### Modelos por template

Um `record` por template em `email/model/`, com um método `variables()` que devolve o
`Map<String, Object>` acima. Exemplo:

```java
public record TicketClosedModel(TicketHeader header, String closedByName, String closedDate,
                                String closedTime, List<String> resolutionLines) {
    public Map<String, Object> variables() {
        Map<String, Object> vars = new HashMap<>(header.variables());
        vars.put("closedByName", closedByName);
        vars.put("closedDate", closedDate);
        vars.put("closedTime", closedTime);
        vars.put("resolutionLines", resolutionLines);
        return vars;
    }
}
```

`TicketHeader` guarda as variáveis comuns e é montado uma vez por chamado. As datas são
formatadas por um `EmailDateFormatter` (zona de `app.email.zone`), porque o servidor do
Railway roda em UTC e `closedAt` é gravado em UTC.

---

## 9. Envio pelo Resend

### Interface

```java
public interface EmailSender {
    /** Nunca lança: falha é registrada em log; a operação de origem já foi gravada. */
    void send(EmailMessage message);
}

public record EmailMessage(String to, String subject, String html,
                           String idempotencyKey, Map<String, String> tags) { }
```

### `ResendEmailSender`

`RestClient` do Spring, sem SDK (uma dependência a menos; testável com
`MockRestServiceServer`). Requisição:

```
POST https://api.resend.com/emails
Authorization: Bearer ${RESEND_API_KEY}
Idempotency-Key: ticket-closed/9b1c…/4f0e…          ← EmailMessage.idempotencyKey
Content-Type: application/json

{
  "from":    "CentralDesk <centraldesk@notificacoes.contabilizape.com.br>",
  "to":      ["maria.souza@contabilizape.com.br"],
  "subject": "Chamado Encerrado",
  "html":    "<!doctype html>…",
  "headers": { "X-Entity-Ref-ID": "ticket-closed/9b1c…/4f0e…" },   ← impede o agrupamento no Gmail (D3)
  "tags":    [ { "name": "type", "value": "ticket_closed" } ]
}
```

Comportamentos:

| Resposta | O que fazer |
|---|---|
| `200` | log `debug` com o `id` devolvido pelo Resend |
| `429` | esperar `retry-after` (segundos) e tentar de novo |
| `5xx` / timeout | tentar de novo com espera crescente (1 s, 2 s, 4 s) |
| `409 concurrent_idempotent_requests` | tentar de novo (o Resend diz que é seguro) |
| `409 invalid_idempotent_request` | **não** repetir: mesma chave com conteúdo diferente é *bug* |
| outros `4xx` (`401`, `403`, `422`…) | **não** repetir; log `error` (chave errada, domínio não verificado, endereço inválido) |

Até 3 tentativas no total. Com a mesma `Idempotency-Key` em todas, o Resend garante que uma
tentativa repetida dentro de 24 h não gera e-mail duplicado. Para o *retry*, o Spring Framework 7
(base do Boot 4) traz `RetryTemplate`/`@Retryable` no próprio núcleo; um laço simples de 3
tentativas também atende. Escolha um e documente no PR.

Timeouts do `RestClient`: conexão 3 s, leitura 10 s (chaves `app.email.resend.*`).

Logs nunca incluem o HTML (pode conter o texto do comentário): só tipo, chamado, `eventId`,
status HTTP e o `id` do Resend.

### `EmailConfig`

```java
@Configuration
@EnableAsync
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    public static final String EXECUTOR = "emailExecutor";

    // Poucas threads: o envio é raro e o limite do Resend é 10 req/s por time
    @Bean(EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "true", matchIfMissing = true)
    ThreadPoolTaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);   // deploy no Railway: termina a fila antes de sair
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    // Testes de integração: envio na mesma thread, para verificar sem esperar
    @Bean(EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "false")
    TaskExecutor syncEmailExecutor() { return new SyncTaskExecutor(); }

    @Bean
    EmailSender emailSender(EmailProperties props, RestClient.Builder builder) {
        return props.hasResendKey()
                ? new ResendEmailSender(builder, props)
                : new LoggingEmailSender();
    }
}
```

---

## 10. Configuração

`application.properties`:

```properties
# E-mails (Resend, API HTTPS). Sem RESEND_API_KEY, os e-mails só aparecem no log.
app.email.from=${EMAIL_FROM:CentralDesk <centraldesk@notificacoes.contabilizape.com.br>}
app.email.front-base-url=${FRONT_BASE_URL:http://localhost:4200}
app.email.zone=America/Recife
app.email.max-message-chars=1500
app.email.async=true
app.email.resend.api-key=${RESEND_API_KEY:}
app.email.resend.base-url=https://api.resend.com
app.email.resend.connect-timeout=3s
app.email.resend.read-timeout=10s
```

`.env.example` (e variáveis do serviço no Railway):

```
# E-mails pelo Resend. Vazio: os e-mails só são registrados no log (desenvolvimento).
# Em produção: chave com permissão "Sending access" restrita ao domínio do remetente.
RESEND_API_KEY=
# Remetente: precisa ser de um domínio verificado no Resend.
# EMAIL_FROM=CentralDesk <centraldesk@notificacoes.contabilizape.com.br>
# Endereço do front-end, usado nos links dos e-mails.
FRONT_BASE_URL=http://localhost:4200
```

`src/test/resources` (ou propriedades do `IntegrationTest`): `app.email.async=false` e
`RESEND_API_KEY` vazio. O teste de integração substitui o `EmailSender` por um
`RecordingEmailSender` (guarda as mensagens numa lista) via `@TestConfiguration`.

---

## 11. Resend + Gmail

### Domínio e DNS (bloqueia produção; depende de D4)

1. No Resend, adicionar o domínio de envio (proposta: `notificacoes.contabilizape.com.br`).
2. Criar no DNS os registros que o Resend mostrar (DKIM e SPF do subdomínio, e o MX de retorno).
   Usar um subdomínio evita mexer no SPF do domínio principal, que já atende o Google Workspace.
3. Conferir se o domínio principal tem DMARC (`_dmarc.contabilizape.com.br`); sem ele, o Gmail
   é mais desconfiado com remetentes automáticos.
4. Só depois disso trocar `EMAIL_FROM` em produção. Para testar antes, os endereços de teste do
   Resend (`delivered@resend.dev`) e o próprio remetente de sandbox dele servem.
5. Criar a API key com permissão só de envio, restrita a esse domínio, e guardá-la apenas nas
   variáveis do Railway.

Como os destinatários são todos `@contabilizape.com.br` (Gmail corporativo), vale pedir ao
administrador do Workspace que confirme que o subdomínio não cai em quarentena.

### Regras de HTML para o Gmail (para os seus templates)

- Layout em `<table>`, largura máxima de 600 px, **CSS inline** em cada elemento (o Gmail aceita
  `<style>` no `<head>` em boa parte dos casos, mas não em todos os clientes).
- Nada de JavaScript, formulário, `<link>` para CSS externo, fonte web obrigatória ou SVG.
- Imagens só por URL `https://` absoluta (o logo pode ficar no front publicado) e sempre com `alt`.
- Todo o HTML abaixo de ~100 KB: acima disso o Gmail corta a mensagem ("Ver mensagem inteira").
- Botão de ação como `<a>` estilizado, apontando para `ticketUrl`.
- Testar em modo escuro: fundo e texto com cores explícitas.
- Sem rastreamento de abertura/clique: é e-mail interno e transacional.

---

## 12. Segurança e LGPD

- **Escape sempre** (`th:text`). O teste do renderer injeta `<script>` e `<img onerror>` num nome
  e num comentário e confere que saem escapados.
- **Assunto sem texto do usuário** (E6): nada de título do chamado no assunto.
- **Nada de CPF, e-mail de terceiros ou dado de conta** nos templates; só nomes e o conteúdo do chamado.
- **Comentário em claro no e-mail** (D2): registrar no ADR que o e-mail é um canal que expõe o
  texto fora do banco cifrado (caixa do Gmail e log do Resend), aceito por regra de negócio.
- **Chave do Resend** só em variável de ambiente, com escopo de envio; nunca no log.
- **Anonimização:** a conta anonimizada tem status `ANONYMIZED`, então `addIfActive` já a exclui.
  Nada a apagar no Resend, porque não guardamos histórico de envio.

---

## 13. Testes (TDD: a classe de teste vem antes)

Ordem sugerida, uma classe por passo. Registrar cada execução, inclusive as que falham, em
`docs/testes/registro-de-execucoes.md`.

| Classe | Tipo | Casos principais |
|---|---|---|
| `TicketNumberIntegrationTest` | integração | V17: números consecutivos; `number` aparece em `TicketResponse` e no resumo |
| `EmailTemplateTest` | unitário | os 6 assuntos exatos; `#%d` só no de mensagem; todo `view()` existe no classpath |
| `EmailRendererTest` | unitário (motor Thymeleaf real) | cada template renderiza com um modelo de exemplo; *escape* de `<script>`; quebras de linha viram `<br>`; nenhuma variável do contrato fica sem uso (o arquivo contém cada `${...}` esperado) |
| `EmailDateFormatterTest` | unitário | `2026-10-23T17:30Z` → `14:30` e `23/10/2026` em `America/Recife` |
| `TicketEmailPlannerTest` | unitário (Mockito) | a matriz da seção 2 inteira: 1a+1b com 2 admins; solicitante admin não recebe em dobro; admin desativado fora; assumir → só solicitante; reatribuir em andamento → só novo responsável; reatribuir da fila → novo responsável + solicitante (D1); admin reatribui para si → ninguém; comentário do solicitante na fila → nada; comentário do solicitante → responsável; comentário do responsável → solicitante; encerrar → só solicitante; destinatário inativo → nada; chaves de idempotência distintas por `eventId` |
| `ResendEmailSenderTest` | unitário (`MockRestServiceServer`) | `Authorization`, `Idempotency-Key`, `X-Entity-Ref-ID` e corpo; 429 com `retry-after` → repete; 500 → repete até 3; 422 → não repete; 409 `invalid_idempotent_request` → não repete; exceção nunca escapa |
| `TicketEmailIntegrationTest` | integração (Testcontainers, `RecordingEmailSender`, `app.email.async=false`) | fluxos ponta a ponta pelos endpoints: abrir, assumir, comentar nos dois sentidos, **reatribuir e comentar de novo → só o novo responsável recebe**, encerrar; operação que falha (409, 403) não gera e-mail (evento só sai no commit) |

---

## 14. Plano de entrega

História no Jira: **SCRUM-179 — [RF14] Notificações por e-mail dos chamados (Resend → Gmail)**,
no épico SCRUM-6, com as subtarefas abaixo (criadas em 23/10/2026). Uma branch por subtarefa, nascendo da `main` atualizada (`git switch main; git pull`).

| # | Subtarefa | Branch | Depende de |
|---|---|---|---|
| 1 | SCRUM-180: ADR: e-mail por API HTTPS (Resend) em vez de SMTP; `@Async` como padrão novo; decisões D1 a D5 | `docs/SCRUM-180-adr-emails` | — |
| 2 | SCRUM-181: Número do chamado (V17, entidade, DTOs, front mostra `#n`) | `feature/SCRUM-181-numero-do-chamado` | — |
| 3 | SCRUM-182: Infra de e-mail: `EmailSender`, Resend, log, `EmailConfig`, propriedades, `.env.example` | `feature/SCRUM-182-infra-email-resend` | 1 |
| 4 | SCRUM-183: Templates e renderer (`EmailTemplate`, `EmailRenderer`, modelos, seus 6 HTML) | `feature/SCRUM-183-templates-email` | 3 |
| 5 | SCRUM-184: Eventos de domínio + `TicketEmailPlanner` + `TicketEmailListener` | `feature/SCRUM-184-emails-de-chamado` | 2, 4 |
| 6 | SCRUM-185: Domínio verificado no Resend, DNS, variáveis no Railway, teste real numa caixa Gmail | (operação, sem código) | 3 |

Os HTML podem ser desenhados em paralelo desde já: o contrato de variáveis da seção 8 é o que
o código vai entregar.

### Atualizar no mesmo PR (Definition of Done)

- `CONTEXT.md` §6 (Infraestrutura): trocar a linha do `JavaMailSender`/SMTP pela API do Resend
  e o motivo (Railway); listar os eventos de `event/`.
- `CONTEXT.md` §4: V17 e a coluna `ticket_number`.
- `CONTEXT.md` §9: pacotes `event/` e `email/`.
- `CONTEXT.md` §3: variáveis `RESEND_API_KEY`, `EMAIL_FROM`, `FRONT_BASE_URL`.
- `GUIA-DE-DESENVOLVIMENTO.md` §2: `@Async` com executor dedicado na lista de padrões; §5: e-mails no roteiro.
- Espelho `Context.md` no Projeto do claude.ai.

### Trabalho futuro (fora deste escopo, registrar no Jira)

- **Outbox persistente** (`email_outbox`) para não perder e-mails num *restart* entre o commit e o envio.
- **Preferências de notificação** por usuário (silenciar e-mails), junto da `notification_preferences` já prevista.
- **Setor de destino real** (SCRUM-148): `TicketOpenedEvent` ganha `targetDepartmentId` e o planner deixa de usar a configuração.
- E-mails de conta (pedido de departamento aprovado/recusado, admin concedido), reaproveitando a mesma infraestrutura.

---

**Fontes consultadas em 23/10/2026:** documentação do Resend
([enviar e-mail](https://resend.com/docs/api-reference/emails/send-email),
[chaves de idempotência](https://resend.com/docs/dashboard/emails/idempotency-keys),
[cabeçalhos personalizados e `X-Entity-Ref-ID`](https://resend.com/docs/dashboard/emails/custom-headers),
[limite de requisições](https://resend.com/docs/api-reference/rate-limit)) e do Railway
([rede de saída e SMTP](https://docs.railway.com/networking/outbound-networking)).
