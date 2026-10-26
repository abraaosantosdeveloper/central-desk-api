package br.com.contabilizape.centraldesk.realtime;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeEventListenerTest {

    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
    private final RealtimeEventListener listener = new RealtimeEventListener(registry);

    @Test
    void permissionChangeClosesTheStreamsOfThatUser() {
        UUID userId = UUID.randomUUID();

        listener.onUserPermissionsChanged(new UserPermissionsChangedEvent(userId));

        // O navegador reconecta renovando a sessão, então as permissões novas entram sem recarregar a página
        verify(registry).closeUsers(Set.of(userId));
    }
}
