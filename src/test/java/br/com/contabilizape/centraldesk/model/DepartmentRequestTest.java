package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.exception.DepartmentRequestAlreadyReviewedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pedido de entrada em departamento: PENDING → APPROVED | REJECTED, uma única vez.
 */
class DepartmentRequestTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 21, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void approvingMovesTheRequesterToTheDepartment() {
        Department innovation = mock(Department.class);
        User requester = user();
        User admin = user();
        DepartmentRequest request = new DepartmentRequest(requester, innovation);

        request.approve(admin, NOW);

        assertThat(request.getStatus()).isEqualTo(DepartmentRequestStatus.APPROVED);
        assertThat(request.getReviewedBy()).isSameAs(admin);
        assertThat(request.getReviewedAt()).isEqualTo(NOW);
        assertThat(requester.getDepartment()).isSameAs(innovation);
    }

    @Test
    void rejectingKeepsTheRequesterWhereHeIs() {
        User requester = user();
        Department current = requester.getDepartment();
        DepartmentRequest request = new DepartmentRequest(requester, mock(Department.class));

        request.reject(user(), NOW);

        assertThat(request.getStatus()).isEqualTo(DepartmentRequestStatus.REJECTED);
        assertThat(request.getReviewedAt()).isEqualTo(NOW);
        assertThat(requester.getDepartment()).isSameAs(current);
    }

    @Test
    void reviewedRequestCannotBeReviewedAgain() {
        DepartmentRequest request = new DepartmentRequest(user(), mock(Department.class));
        ReflectionTestUtils.setField(request, "status", DepartmentRequestStatus.REJECTED);

        assertThatThrownBy(() -> request.approve(user(), NOW)).isInstanceOf(DepartmentRequestAlreadyReviewedException.class);
        assertThatThrownBy(() -> request.reject(user(), NOW)).isInstanceOf(DepartmentRequestAlreadyReviewedException.class);
        assertThat(request.getStatus()).isEqualTo(DepartmentRequestStatus.REJECTED);
    }

    private static User user() {
        return new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash", mock(Department.class));
    }
}
