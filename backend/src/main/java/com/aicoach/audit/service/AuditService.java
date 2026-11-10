package com.aicoach.audit.service;

import com.aicoach.audit.domain.AuditLog;
import com.aicoach.audit.repository.AuditLogRepository;
import com.aicoach.common.web.RequestIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records administrative changes. Joins the caller's transaction so the audit row commits atomically with the change. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, UUID entityId,
            Map<String, ?> before, Map<String, ?> after) {
        repository.save(new AuditLog(actorId, action, entityType, entityId, json(before), json(after),
                MDC.get(RequestIdFilter.MDC_KEY)));
    }

    private String json(Map<String, ?> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit payload is not serialisable", e);
        }
    }
}
