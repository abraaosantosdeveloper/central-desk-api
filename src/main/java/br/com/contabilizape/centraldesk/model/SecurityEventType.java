package br.com.contabilizape.centraldesk.model;

/** O que a trilha de auditoria registra (security_events, V20). */
public enum SecurityEventType {
    LOGIN_FAILED,
    LOGIN_UNKNOWN_EMAIL,
    LOGIN_BLOCKED_ACCOUNT,
    ACCOUNT_BLOCKED,
    TFA_FAILED,
    NEW_TRUSTED_BROWSER,
    TWO_FACTOR_ENABLED,
    TWO_FACTOR_DISABLED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_BLOCKED_ACCOUNT,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_RESET_BY_ADMIN,
    ADMIN_RECOVERY,
    EMAIL_VERIFIED
}
