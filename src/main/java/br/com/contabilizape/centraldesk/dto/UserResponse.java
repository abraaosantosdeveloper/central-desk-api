package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String fullName,
    String email,
    UserStatus status,
    UUID departmentId,
    String departmentName,
    boolean departmentApprovalPending,
    OffsetDateTime createdAt
) {

    public static UserResponse from(User user, boolean departmentApprovalPending){
        return new UserResponse(
            user.getId(), 
            user.getFullName(), 
            user.getEmail(), 
            user.getStatus(), 
            user.getDepartment().getId(), 
            user.getDepartment().getName(), 
            departmentApprovalPending, 
            user.getCreatedAt()
        );
    }

}
