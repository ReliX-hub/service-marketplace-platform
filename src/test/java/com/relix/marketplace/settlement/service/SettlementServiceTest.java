package com.relix.marketplace.settlement.service;

import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.settlement.dto.SettlementResponse;
import com.relix.marketplace.settlement.entity.Settlement;
import com.relix.marketplace.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock private SettlementRepository settlementRepository;
    @InjectMocks private SettlementService settlementService;

    @Test
    void completedEngagementCreatesTenPercentFeeSettlement() {
        Engagement engagement = engagement(1L, "100.00", EngagementStatus.COMPLETED);
        when(settlementRepository.existsByEngagementId(1L)).thenReturn(false);
        when(settlementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Settlement result = settlementService.createSettlement(engagement);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getPlatformFee()));
        assertEquals(0, new BigDecimal("90.00").compareTo(result.getWorkerPayout()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTotalAmount()));
    }

    @Test
    void nonCompletedEngagementCannotSettle() {
        Engagement engagement = engagement(2L, "50.00", EngagementStatus.DELIVERED);
        when(settlementRepository.existsByEngagementId(2L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> settlementService.createSettlement(engagement));
    }

    @Test
    void settlementCreationIsIdempotent() {
        Engagement engagement = engagement(3L, "50.00", EngagementStatus.COMPLETED);
        Settlement existing = Settlement.builder()
                .engagement(engagement)
                .totalAmount(new BigDecimal("50.00"))
                .platformFee(new BigDecimal("5.00"))
                .workerPayout(new BigDecimal("45.00"))
                .build();
        existing.setId(30L);
        when(settlementRepository.existsByEngagementId(3L)).thenReturn(true);
        when(settlementRepository.findByEngagementId(3L)).thenReturn(Optional.of(existing));

        Settlement result = settlementService.createSettlement(engagement);

        assertEquals(30L, result.getId());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void workerSettlementListUsesTheCommonPaginationEnvelope() {
        Pageable pageable = PageRequest.of(1, 2);
        Settlement first = settlement(10L, engagement(1L, "100.00", EngagementStatus.COMPLETED));
        Settlement second = settlement(11L, engagement(2L, "50.00", EngagementStatus.COMPLETED));
        when(settlementRepository.findByWorkerId(20L, pageable)).thenReturn(
                new PageImpl<>(List.of(first, second), pageable, 5));

        PageResponse<SettlementResponse> response =
                settlementService.getSettlementsByWorkerId(20L, pageable);

        assertEquals(List.of(10L, 11L), response.getItems().stream()
                .map(SettlementResponse::getId)
                .toList());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals(true, response.isHasNext());
    }

    @Test
    void adminSettlementListUsesTheCommonPaginationEnvelope() {
        Pageable pageable = PageRequest.of(0, 20);
        Settlement settlement = settlement(10L, engagement(1L, "100.00", EngagementStatus.COMPLETED));
        when(settlementRepository.findAllBy(pageable)).thenReturn(
                new PageImpl<>(List.of(settlement), pageable, 1));

        PageResponse<SettlementResponse> response = settlementService.getAllSettlements(pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getTotalElements());
        assertEquals(false, response.isHasNext());
    }

    private Engagement engagement(Long id, String amount, EngagementStatus status) {
        Engagement engagement = Engagement.builder()
                .amount(new BigDecimal(amount))
                .status(status)
                .build();
        engagement.setId(id);
        return engagement;
    }

    private Settlement settlement(Long id, Engagement engagement) {
        Settlement settlement = Settlement.builder()
                .engagement(engagement)
                .totalAmount(engagement.getAmount())
                .platformFee(BigDecimal.ZERO)
                .workerPayout(engagement.getAmount())
                .status(Settlement.SettlementStatus.PENDING)
                .build();
        settlement.setId(id);
        return settlement;
    }
}
