package br.com.contabilizape.centraldesk.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Escreve um comentário (":ping") periodicamente em todos os streams. Uma conexão ociosa pode ser derrubada
 * por um proxy (o do Railway, por exemplo) sem que ninguém perceba; o ping evita isso e remove do registro
 * as conexões que já morreram.
 */
@Component
public class SseHeartbeat implements SchedulingConfigurer {

    private final SseEmitterRegistry registry;
    private final Duration interval;

    // O intervalo vem como Duration ("25s"), como o sse-timeout; o @Scheduled só aceitaria milissegundos ou ISO-8601
    public SseHeartbeat(SseEmitterRegistry registry, @Value("${app.realtime.heartbeat-interval}") Duration interval) {
        this.registry = registry;
        this.interval = interval;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        long millis = interval.toMillis();
        registrar.addFixedDelayTask(new IntervalTask(registry::sendHeartbeat, millis, millis));
    }
}
