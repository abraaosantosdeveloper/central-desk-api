package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.AccountNotDeactivatedException;
import br.com.contabilizape.centraldesk.exception.CategoryDoesNotExistException;
import br.com.contabilizape.centraldesk.exception.CommentForbiddenException;
import br.com.contabilizape.centraldesk.exception.ReassignConflictException;
import br.com.contabilizape.centraldesk.exception.ReassignForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketOverviewForbiddenException;
import br.com.contabilizape.centraldesk.exception.CategoryLabelTakenException;
import br.com.contabilizape.centraldesk.exception.CategoryNotFoundException;
import br.com.contabilizape.centraldesk.exception.CpfAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.DepartmentRequestAlreadyReviewedException;
import br.com.contabilizape.centraldesk.exception.DepartmentRequestNotFoundException;
import br.com.contabilizape.centraldesk.exception.EmailAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.InvalidCredentialsException;
import br.com.contabilizape.centraldesk.exception.InvalidFieldException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.InvalidTicketTransitionException;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.exception.PriorityNotFoundException;
import br.com.contabilizape.centraldesk.exception.SelfAssignmentException;
import br.com.contabilizape.centraldesk.exception.SelfDeactivationException;
import br.com.contabilizape.centraldesk.exception.TicketClosedForCommentsException;
import br.com.contabilizape.centraldesk.exception.TicketCloseForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketReopenConflictException;
import br.com.contabilizape.centraldesk.exception.TicketReopenForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketEditForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketNotEditableException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import br.com.contabilizape.centraldesk.exception.AccountBlockedException;
import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.exception.EmailNotVerifiedException;
import br.com.contabilizape.centraldesk.exception.InvalidLoginChallengeException;
import br.com.contabilizape.centraldesk.exception.LoginRateLimitException;
import java.time.Duration;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduz exceções em respostas HTTP no formato ProblemDetail (RFC 9457).
 *
 * Estende ResponseEntityExceptionHandler para tratar também os erros do próprio Spring MVC
 * (JSON malformado, método não suportado...). Com isso, o Spring Boot não registra o
 * tratador padrão dele, que antes respondia a validação sem a lista de erros por campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({EmailAlreadyRegisteredException.class, CpfAlreadyRegisteredException.class})
    public ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Cadastro duplicado", exception.getMessage());
    }

    @ExceptionHandler({DepartmentNotFoundException.class, CategoryNotFoundException.class, PriorityNotFoundException.class})
    public ProblemDetail handleInvalidInput(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Dados inválidos", exception.getMessage());
    }

    @ExceptionHandler(CategoryLabelTakenException.class)
    public ProblemDetail handleCategoryLabelTaken(CategoryLabelTakenException exception) {
        return problem(HttpStatus.CONFLICT, "Categoria duplicada", exception.getMessage());
    }

    @ExceptionHandler(CategoryDoesNotExistException.class)
    public ProblemDetail handleCategoryDoesNotExist(CategoryDoesNotExistException exception) {
        return problem(HttpStatus.NOT_FOUND, "Categoria não encontrada", exception.getMessage());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ProblemDetail handleTicketNotFound(TicketNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Chamado não encontrado", exception.getMessage());
    }

    @ExceptionHandler({TicketEditForbiddenException.class, SelfAssignmentException.class, CommentForbiddenException.class, TicketCloseForbiddenException.class, TicketReopenForbiddenException.class,
            ReassignForbiddenException.class, TicketOverviewForbiddenException.class})
    public ProblemDetail handleTicketActionForbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado", exception.getMessage());
    }

    @ExceptionHandler(TicketClosedForCommentsException.class)
    public ProblemDetail handleTicketClosedForComments(TicketClosedForCommentsException exception) {
        return problem(HttpStatus.CONFLICT, "Chamado encerrado", exception.getMessage());
    }

    @ExceptionHandler(InvalidTicketTransitionException.class)
    public ProblemDetail handleInvalidTicketTransition(InvalidTicketTransitionException exception) {
        return problem(HttpStatus.CONFLICT, "Transição de status inválida", exception.getMessage());
    }

    @ExceptionHandler(ReassignConflictException.class)
    public ProblemDetail handleReassignConflict(ReassignConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Reatribuição não permitida", exception.getMessage());
    }

    @ExceptionHandler(TicketReopenConflictException.class)
    public ProblemDetail handleTicketReopenConflict(TicketReopenConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Reabertura não permitida", exception.getMessage());
    }

    @ExceptionHandler(TicketNotEditableException.class)
    public ProblemDetail handleTicketNotEditable(TicketNotEditableException exception) {
        return problem(HttpStatus.CONFLICT, "Chamado não editável", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", exception.getMessage());
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ProblemDetail handleInvalidSession(InvalidSessionException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Sessão inválida", exception.getMessage());
    }

    @ExceptionHandler(DepartmentRequestNotFoundException.class)
    public ProblemDetail handleDepartmentRequestNotFound(DepartmentRequestNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Pedido não encontrado", exception.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Usuário não encontrado", exception.getMessage());
    }

    @ExceptionHandler({AccountNotDeactivatedException.class, LastAdminException.class, SelfDeactivationException.class,
            InvalidAccountStatusException.class, DepartmentRequestAlreadyReviewedException.class})
    public ProblemDetail handleAccountConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Operação não permitida", exception.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ProblemDetail handleAccountNotActive(AccountNotActiveException exception) {
        return coded(problem(HttpStatus.FORBIDDEN, "Conta inativa", exception.getMessage()), "account-not-active");
    }

    // ---------- segurança das contas (PLANO-SEGURANCA-CONTAS-E-EMAILS.md) ----------
    // O campo "code" deixa o front decidir a tela sem depender do texto da mensagem

    @ExceptionHandler(AccountBlockedException.class)
    public ProblemDetail handleAccountBlocked(AccountBlockedException exception) {
        return coded(problem(HttpStatus.FORBIDDEN, "Conta bloqueada", exception.getMessage()), "account-blocked");
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException exception) {
        return coded(problem(HttpStatus.FORBIDDEN, "E-mail não confirmado", exception.getMessage()), "email-not-verified");
    }

    @ExceptionHandler(InvalidLoginChallengeException.class)
    public ProblemDetail handleInvalidLoginChallenge(InvalidLoginChallengeException exception) {
        return coded(problem(HttpStatus.UNAUTHORIZED, "Verificação expirada", exception.getMessage()), "login-challenge-expired");
    }

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleLoginRateLimit(LoginRateLimitException exception) {
        return tooManyRequests(exception.getMessage(), exception.getRetryAfter());
    }

    @ExceptionHandler(CodeRequestLimitException.class)
    public ResponseEntity<ProblemDetail> handleCodeRequestLimit(CodeRequestLimitException exception) {
        return tooManyRequests(exception.getMessage(), exception.getRetryAfter());
    }

    private static ResponseEntity<ProblemDetail> tooManyRequests(String detail, Duration retryAfter) {
        long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", detail));
    }

    private static ProblemDetail coded(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        return problem;
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ProblemDetail handleInvalidField(InvalidFieldException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Verifique os campos e tente novamente.");
        problem.setProperty("errors", Map.of(exception.getField(), exception.getMessage()));
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), error.getDefaultMessage(), (first, second) -> first + "; " + second));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Verifique os campos e tente novamente.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
