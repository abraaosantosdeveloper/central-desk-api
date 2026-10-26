package br.com.contabilizape.centraldesk.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o agendamento do Spring, usado pelo heartbeat do SSE ({@link SseHeartbeat}). */
@Configuration
@EnableScheduling
public class RealtimeConfig {
}
