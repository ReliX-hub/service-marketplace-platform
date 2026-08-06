package com.relix.marketplace.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.audit.dto.AuditLogResponse;
import com.relix.marketplace.audit.entity.AuditLog;
import com.relix.marketplace.audit.repository.AuditLogRepository;
import com.relix.marketplace.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(String entityType, Long entityId, String action,
                    String actorType, Long actorId, Map<String, Object> details) {
        String detailsJson = null;
        if (details != null) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit details", e);
            }
        }

        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorType(actorType)
                .actorId(actorId)
                .details(detailsJson)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit logged: {} {} {} by {}:{}", entityType, entityId, action, actorType, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByEntity(
            String entityType,
            Long entityId,
            Pageable pageable) {
        return PageResponse.of(
                auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable),
                this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .actorType(log.getActorType())
                .actorId(log.getActorId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
