package br.com.contabilizape.centraldesk.email;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Os e-mails do sistema: arquivo do template, assunto e as variáveis que cada um exige.
 *
 * O assunto é fixo no código e nunca leva texto digitado por usuário (só o número do chamado,
 * em {@link #TICKET_NEW_MESSAGE}): não há injeção de cabeçalho, e o código de verificação nunca
 * aparece no assunto (a pré-visualização da caixa de entrada e as notificações do celular o mostram).
 *
 * {@link #isConfidential()}: o e-mail leva um código ou trata de segurança da conta. O corpo não vai
 * para o log em nível nenhum e o envio usa a fila prioritária ({@link EmailConfig#PRIORITY_EXECUTOR}).
 */
public enum EmailTemplate {

    // ---------- chamados (ARQUITETURA-EMAILS §8 e PLANO-SEGURANCA §13.1) ----------
    TICKET_OPENED_REQUESTER("ticket-opened-requester", "Chamado Aberto", false),
    TICKET_OPENED_DEPARTMENT("ticket-opened-department", "Chamado Aberto", false),
    TICKET_IN_PROGRESS("ticket-in-progress", "Chamado em andamento", false,
            "assigneeName", "assignedDate", "assignedTime"),
    TICKET_REASSIGNED_REQUESTER("ticket-reassigned-requester", "Chamado reatribuído", false,
            "newAssigneeName", "reassignedByName", "reassignedDate", "reassignedTime"),
    TICKET_REASSIGNED_NEW_ASSIGNEE("ticket-reassigned-new-assignee", "Chamado reatribuído", false,
            "reassignedByName", "reassignedDate", "reassignedTime"),
    TICKET_REASSIGNED_PREVIOUS_ASSIGNEE("ticket-reassigned-previous-assignee", "Chamado reatribuído", false,
            "newAssigneeName", "reassignedByName", "reassignedDate", "reassignedTime"),
    TICKET_NEW_MESSAGE("ticket-new-message", "Nova mensagem no chamado #%d", false,
            "authorName", "authorRole", "messageLines", "messageTruncated", "sentDate", "sentTime"),
    TICKET_CLOSED("ticket-closed", "Chamado Encerrado", false,
            "closedByName", "closedDate", "closedTime", "resolutionLines"),
    TICKET_REOPENED("ticket-reopened", "Chamado reaberto", false,
            "reopenedByName", "reopenedDate", "reopenedTime", "reasonLines", "backToQueue", "assigneeName"),

    // ---------- segurança da conta (PLANO-SEGURANCA §10) ----------
    EMAIL_VERIFICATION_CODE("email-verification-code", "Confirme seu e-mail", true, "code", "validityMinutes"),
    LOGIN_CODE("login-code", "Seu código de acesso", true, "code", "validityMinutes"),
    PASSWORD_RESET_CODE("password-reset-code", "Código para redefinir sua senha", true, "code", "validityMinutes"),
    PASSWORD_CHANGE_CODE("password-change-code", "Código para alterar sua senha", true, "code", "validityMinutes"),
    TWO_FACTOR_DISABLE_CODE("two-factor-disable-code", "Código para desligar a verificação em duas etapas", true,
            "code", "validityMinutes"),
    PASSWORD_CHANGED("password-changed", "Sua senha foi alterada", true, "eventDate", "eventTime"),
    PASSWORD_RESET_BY_ADMIN("password-reset-by-admin", "Sua senha foi redefinida pela equipe de Inovação", true,
            "adminName", "eventDate", "eventTime"),
    ACCOUNT_BLOCKED_OWNER("account-blocked-owner", "Sua conta foi bloqueada", true, "eventDate", "eventTime"),
    // Sem o nome no assunto: nome é texto digitado por usuário (vai no corpo)
    ACCOUNT_BLOCKED_ADMINS("account-blocked-admins", "Uma conta foi bloqueada no CentralDesk", true,
            "blockedUserName", "blockedUserEmail", "eventDate", "eventTime", "usersUrl"),
    ACCOUNT_BLOCKED_RESET_ATTEMPT("account-blocked-reset-attempt", "Sua conta está bloqueada", true),
    TFA_FAILED_AFTER_PASSWORD("tfa-failed-after-password", "Tentativa de acesso à sua conta", true,
            "eventDate", "eventTime"),
    NEW_BROWSER_LOGIN("new-browser-login", "Novo acesso à sua conta", true, "browserLabel", "eventDate", "eventTime"),
    TWO_FACTOR_DISABLED("two-factor-disabled", "Verificação em duas etapas desligada", true, "eventDate", "eventTime"),
    // Sem os níveis no assunto: o corpo diz o que mudou (PLANO-REATRIBUICAO §3, PV7)
    PRIVILEGE_CHANGED("privilege-changed", "Seu nível de acesso mudou", true,
            "adminName", "previousLevel", "newLevel", "eventDate", "eventTime");

    /** Variáveis de todo e-mail de chamado (ARQUITETURA-EMAILS §8, "comuns a todos"). */
    public static final Set<String> TICKET_HEADER = Header.FIELDS;

    // Constantes do enum não leem campos estáticos dele no construtor: a lista mora numa classe à parte
    private static final class Header {
        static final Set<String> FIELDS = Set.of("ticketNumber", "ticketTitle", "ticketUrl", "categoryName",
                "priorityName", "requesterName", "departmentName", "openedDate", "openedTime");
    }

    private final String file;
    private final String subjectPattern;
    private final boolean confidential;
    private final Set<String> variables;

    EmailTemplate(String file, String subjectPattern, boolean confidential, String... specific) {
        this.file = file;
        this.subjectPattern = subjectPattern;
        this.confidential = confidential;
        Stream<String> common = file.startsWith("ticket-") ? Header.FIELDS.stream() : Stream.empty();
        this.variables = Stream.of(Stream.of("recipientName"), common, Stream.of(specific))
                .flatMap(s -> s).collect(Collectors.toUnmodifiableSet());
    }

    /** Caminho resolvido pelo Thymeleaf: templates/email/&lt;arquivo&gt;.html */
    public String view() {
        return "email/" + file;
    }

    public String file() {
        return file;
    }

    /** Assunto. Só {@link #TICKET_NEW_MESSAGE} usa o número do chamado; nos outros ele é ignorado. */
    public String subject(Long ticketNumber) {
        return subjectPattern.contains("%d") ? subjectPattern.formatted(ticketNumber) : subjectPattern;
    }

    public boolean isConfidential() {
        return confidential;
    }

    /** Variáveis que o template exige; o {@link EmailRenderer} recusa a renderização se faltar alguma. */
    public Set<String> variables() {
        return variables;
    }

    /** Rótulo para o painel do Resend (só letras ASCII, números, "_" e "-"). */
    public String tag() {
        return file.replace('-', '_');
    }
}
