package com.majordomo.domain.model;

/**
 * Identifies the type of domain entity for audit logging and attachments.
 */
public enum EntityType {
    PROPERTY,
    CONTACT,
    SERVICE_RECORD,
    USER,
    MAINTENANCE_SCHEDULE,
    ATTACHMENT,
    API_KEY,
    JOB_POSTING
}
