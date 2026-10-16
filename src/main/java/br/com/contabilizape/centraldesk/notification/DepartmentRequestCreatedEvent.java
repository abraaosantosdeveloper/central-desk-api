package br.com.contabilizape.centraldesk.notification;

import java.util.UUID;

public record DepartmentRequestCreatedEvent(
        UUID requestId,
        String requesterName,
        String departmentName
) {}