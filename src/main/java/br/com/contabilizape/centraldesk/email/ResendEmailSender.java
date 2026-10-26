package br.com.contabilizape.centraldesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envia pela API HTTPS do Resend ({@code POST /emails}). Não usa SMTP: o Railway só libera SMTP
 * de saída no plano Pro e recomenda APIs HTTPS.
 *
 * Até {@value #MAX_ATTEMPTS} tentativas, sempre com a mesma {@code Idempotency-Key}: se uma resposta se
 * perder no caminho, o Resend não manda o e-mail duas vezes. Repete em 429 (respeitando Retry-After),
 * 5xx, falha de rede e 409 "em andamento"; erro do nosso lado (401, 403, 422, 409 de conteúdo diferente)
 * não adianta repetir.
 */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    static final int MAX_ATTEMPTS = 3;
    // Uma thread presa por minutos atrasaria os e-mails seguintes; acima disto, desiste da espera
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(10);
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
    private static final int MAX_LOGGED_ERROR_CHARS = 300;
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() { };

    /** A espera entre tentativas, trocável nos testes para não demorar. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record Outcome(boolean sent, boolean retriable, Duration delay) {
        static final Outcome SENT = new Outcome(true, false, null);
        static final Outcome GAVE_UP = new Outcome(false, false, null);

        static Outcome retryAfter(Duration delay) {
            return new Outcome(false, true, delay);
        }
    }

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final Sleeper sleeper;

    public ResendEmailSender(RestClient restClient, String apiKey, String from) {
        this(restClient, apiKey, from, duration -> Thread.sleep(duration));
    }

    ResendEmailSender(RestClient restClient, String apiKey, String from, Sleeper sleeper) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.from = from;
        this.sleeper = sleeper;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            sendWithRetries(message);
        } catch (RuntimeException e) {
            // Rede de segurança: nada daqui pode desfazer ou atrapalhar a operação de origem
            log.error("Falha inesperada ao enviar o e-mail {}", message.idempotencyKey(), e);
        }
    }

    private void sendWithRetries(EmailMessage message) {
        for (int attempt = 1; ; attempt++) {
            Outcome outcome = attempt(message);
            if (outcome.sent() || !outcome.retriable()) {
                return;
            }
            if (attempt == MAX_ATTEMPTS) {
                log.error("E-mail {} não enviado: desistiu depois de {} tentativas", message.idempotencyKey(), MAX_ATTEMPTS);
                return;
            }
            Duration delay = outcome.delay() != null ? outcome.delay() : backoff(attempt);
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("E-mail {} não enviado: o envio foi interrompido (aplicação encerrando?)", message.idempotencyKey());
                return;
            }
        }
    }

    private Outcome attempt(EmailMessage message) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("Idempotency-Key", message.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(message))
                    .retrieve()
                    .body(JSON_OBJECT);
            log.info("E-mail enviado: chave={} resendId={}", message.idempotencyKey(),
                    response == null ? null : response.get("id"));
            return Outcome.SENT;
        } catch (RestClientResponseException e) {
            return classify(message, e);
        } catch (ResourceAccessException e) {
            log.warn("E-mail {}: falha de rede ({})", message.idempotencyKey(), e.getMessage());
            return Outcome.retryAfter(null);
        }
    }

    private Outcome classify(EmailMessage message, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        if (status == 429) {
            return Outcome.retryAfter(retryAfter(e));
        }
        if (status >= 500) {
            log.warn("E-mail {}: Resend respondeu {}", message.idempotencyKey(), status);
            return Outcome.retryAfter(null);
        }
        if (status == 409 && body.contains("concurrent_idempotent_requests")) {
            return Outcome.retryAfter(null); // o próprio Resend diz que é seguro repetir
        }
        // 401/403 (chave), 422 (domínio ou endereço) e 409 de conteúdo diferente: repetir não resolve
        log.error("E-mail {} recusado pelo Resend ({}): {}", message.idempotencyKey(), status, abbreviate(body));
        return Outcome.GAVE_UP;
    }

    private static Duration retryAfter(RestClientResponseException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null) {
            return null;
        }
        try {
            Duration requested = Duration.ofSeconds(Long.parseLong(header.trim()));
            return requested.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : requested;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // 1 s, 2 s, ...
    private static Duration backoff(int attempt) {
        return FIRST_BACKOFF.multipliedBy(1L << (attempt - 1));
    }

    // Corpo da requisição. O X-Entity-Ref-ID único por e-mail evita o agrupamento de assuntos iguais no Gmail
    private Map<String, Object> payload(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(message.to()));
        body.put("subject", message.subject());
        body.put("html", message.html());
        body.put("headers", Map.of("X-Entity-Ref-ID", message.idempotencyKey()));
        if (!message.tags().isEmpty()) {
            List<Map<String, String>> tags = new ArrayList<>();
            message.tags().forEach((name, value) -> tags.add(Map.of("name", name, "value", value)));
            body.put("tags", tags);
        }
        return body;
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_LOGGED_ERROR_CHARS ? text : text.substring(0, MAX_LOGGED_ERROR_CHARS) + "...";
    }
}
