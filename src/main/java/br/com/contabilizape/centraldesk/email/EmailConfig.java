package br.com.contabilizape.centraldesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Infraestrutura dos e-mails: o executor onde eles são enviados e a escolha do {@link EmailSender}.
 *
 * Atenção: ao existir um {@code Executor} próprio, o Spring Boot deixa de criar o
 * {@code applicationTaskExecutor}. Hoje nada depende dele; quem precisar de outro executor
 * (ex.: {@code @Scheduled}) deve declarar o seu.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    /** Nome usado em {@code @Async(EmailConfig.EXECUTOR)}: e-mails de chamado. */
    public static final String EXECUTOR = "emailExecutor";

    /** Fila só dos e-mails de conta (códigos, bloqueio): não esperam atrás dos e-mails de chamado. */
    public static final String PRIORITY_EXECUTOR = "priorityEmailExecutor";

    /** Variável que o Railway define em todo serviço: lá, e-mail sem chave do Resend não é aceito. */
    static final String RAILWAY_ENVIRONMENT = "RAILWAY_ENVIRONMENT_NAME";

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    // Poucas threads: o envio é raro e o limite do Resend é 10 requisições por segundo por conta
    @Bean(EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "true", matchIfMissing = true)
    ThreadPoolTaskExecutor backgroundEmailExecutor() {
        return pool("email-", 500);
    }

    @Bean(PRIORITY_EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "true", matchIfMissing = true)
    ThreadPoolTaskExecutor priorityEmailExecutor() {
        return pool("email-conta-", 200);
    }

    @Bean(PRIORITY_EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "false")
    TaskExecutor sameThreadPriorityEmailExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    EmailFormats emailFormats(EmailProperties properties) {
        return new EmailFormats(properties.zone(), properties.maxMessageChars());
    }

    private static ThreadPoolTaskExecutor pool(String threadPrefix, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadPrefix);
        // Deploy no Railway: termina os e-mails da fila antes de a instância sair
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    // Testes de integração: o e-mail sai na própria thread, para conferir sem esperar
    @Bean(EXECUTOR)
    @ConditionalOnProperty(name = "app.email.async", havingValue = "false")
    TaskExecutor sameThreadEmailExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    EmailSender emailSender(EmailProperties properties, Environment environment) {
        if (!properties.hasResendKey() && (properties.required() || environment.containsProperty(RAILWAY_ENVIRONMENT))) {
            // Sem a chave, códigos de acesso iriam para o log do servidor (B6)
            throw new IllegalStateException("RESEND_API_KEY é obrigatória neste ambiente (app.email.required ou Railway).");
        }
        if (!properties.hasResendKey()) {
            log.info("RESEND_API_KEY não definida: os e-mails serão apenas registrados no log.");
            return new LoggingEmailSender();
        }
        EmailProperties.Resend resend = properties.resend();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(resend.connectTimeout()).build());
        requestFactory.setReadTimeout(resend.readTimeout());
        RestClient client = RestClient.builder().baseUrl(resend.baseUrl()).requestFactory(requestFactory).build();
        return new ResendEmailSender(client, resend.apiKey(), properties.from());
    }
}
