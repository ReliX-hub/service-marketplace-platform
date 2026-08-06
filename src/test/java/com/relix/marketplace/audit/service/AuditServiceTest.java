package com.relix.marketplace.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.audit.dto.AuditLogResponse;
import com.relix.marketplace.audit.entity.AuditLog;
import com.relix.marketplace.audit.repository.AuditLogRepository;
import com.relix.marketplace.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditService auditService;

    @Test
    void getByEntityReturnsPaginationEnvelope() {
        Pageable pageable = PageRequest.of(1, 2);
        AuditLog auditLog = AuditLog.builder()
                .id(7L)
                .entityType("ENGAGEMENT")
                .entityId(42L)
                .action("ENGAGEMENT_FUNDED")
                .actorType("USER")
                .actorId(3L)
                .details("{\"paymentId\":9}")
                .createdAt(Instant.parse("2026-08-05T00:00:00Z"))
                .build();
        when(auditLogRepository.findByEntityTypeAndEntityId(
                "ENGAGEMENT", 42L, pageable))
                .thenReturn(new PageImpl<>(List.of(auditLog), pageable, 5));

        PageResponse<AuditLogResponse> response =
                auditService.getByEntity("ENGAGEMENT", 42L, pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(7L, response.getItems().get(0).getId());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
    }
}
