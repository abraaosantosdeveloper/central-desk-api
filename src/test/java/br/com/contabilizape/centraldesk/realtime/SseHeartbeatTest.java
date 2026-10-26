package br.com.contabilizape.centraldesk.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseHeartbeatTest {

    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);

    @Test
    void registersOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new SseHeartbeat(registry, Duration.ofSeconds(25)).configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getInterval()).isEqualTo(25_000L);
            // O primeiro ping espera um intervalo: logo depois de subir ainda não há conexões
            assertThat(task.getInitialDelay()).isEqualTo(25_000L);
        });
    }

    @Test
    void theScheduledTaskSendsTheHeartbeat() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new SseHeartbeat(registry, Duration.ofSeconds(25)).configureTasks(registrar);

        IntervalTask task = registrar.getFixedDelayTaskList().get(0);
        task.getRunnable().run();

        verify(registry).sendHeartbeat();
    }
}
