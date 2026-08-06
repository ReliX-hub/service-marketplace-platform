package com.relix.marketplace.settlement.service;

import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.settlement.dto.BatchResponse;
import com.relix.marketplace.settlement.entity.Settlement;
import com.relix.marketplace.settlement.entity.SettlementBatch;
import com.relix.marketplace.settlement.repository.SettlementBatchRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    @InjectMocks
    private SettlementBatchService settlementBatchService;

    @Test
    void processBatch_shouldMarkBatchFailed_whenAnySettlementFails() {
        Settlement settlement = Settlement.builder()
                .workerPayout(new BigDecimal("50.00"))
                .status(Settlement.SettlementStatus.PENDING)
                .build();
        settlement.setId(1L);

        when(settlementBatchRepository.existsByBatchId(anyString())).thenReturn(false);
        when(settlementRepository.findByStatus(Settlement.SettlementStatus.PENDING))
                .thenReturn(List.of(settlement));

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 1st save(PROCESSING) succeeds, 2nd save(COMPLETED) fails, 3rd save(FAILED in catch) succeeds.
        when(settlementRepository.save(any(Settlement.class)))
                .thenReturn(settlement)
                .thenThrow(new RuntimeException("mock payout failure"))
                .thenReturn(settlement);

        BatchResponse response = settlementBatchService.processBatch();

        assertEquals("FAILED", response.getStatus());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
    }

    @Test
    void batchHistoryUsesTheCommonPaginationEnvelope() {
        Pageable pageable = PageRequest.of(1, 2);
        SettlementBatch first = SettlementBatch.builder()
                .batchId("BATCH-1")
                .status(SettlementBatch.BatchStatus.COMPLETED)
                .totalCount(2)
                .successCount(2)
                .failedCount(0)
                .totalAmount(new BigDecimal("90.00"))
                .build();
        first.setId(10L);
        SettlementBatch second = SettlementBatch.builder()
                .batchId("BATCH-2")
                .status(SettlementBatch.BatchStatus.FAILED)
                .totalCount(2)
                .successCount(1)
                .failedCount(1)
                .totalAmount(new BigDecimal("40.00"))
                .build();
        second.setId(11L);
        when(settlementBatchRepository.findAllBy(pageable)).thenReturn(
                new PageImpl<>(List.of(first, second), pageable, 5));

        PageResponse<BatchResponse> response = settlementBatchService.getAllBatches(pageable);

        assertEquals(List.of(10L, 11L), response.getItems().stream()
                .map(BatchResponse::getId)
                .toList());
        assertEquals(1, response.getPage());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
    }
}
