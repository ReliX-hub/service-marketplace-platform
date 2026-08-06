package com.relix.marketplace.settlement.service;

import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.settlement.dto.BatchResponse;
import com.relix.marketplace.settlement.entity.Settlement;
import com.relix.marketplace.settlement.entity.SettlementBatch;
import com.relix.marketplace.settlement.repository.SettlementBatchRepository;
import com.relix.marketplace.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final SettlementRepository settlementRepository;
    private final SettlementBatchRepository settlementBatchRepository;

    @Transactional
    public BatchResponse processBatch() {
        String batchId = "BATCH-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Check if batch already processed today
        if (settlementBatchRepository.existsByBatchId(batchId)) {
            log.info("Batch already exists for today: {}", batchId);
            SettlementBatch existing = settlementBatchRepository.findByBatchId(batchId).orElseThrow();
            return toBatchResponse(existing);
        }

        List<Settlement> pendingSettlements = settlementRepository.findByStatus(Settlement.SettlementStatus.PENDING);

        if (pendingSettlements.isEmpty()) {
            log.info("No pending settlements to process for batch: {}", batchId);
            SettlementBatch batch = SettlementBatch.builder()
                    .batchId(batchId)
                    .status(SettlementBatch.BatchStatus.COMPLETED)
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .totalAmount(BigDecimal.ZERO)
                    .startedAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();
            batch = settlementBatchRepository.save(batch);
            return toBatchResponse(batch);
        }

        // Create batch record
        SettlementBatch batch = SettlementBatch.builder()
                .batchId(batchId)
                .status(SettlementBatch.BatchStatus.PROCESSING)
                .totalCount(pendingSettlements.size())
                .startedAt(Instant.now())
                .build();
        batch = settlementBatchRepository.save(batch);

        int successCount = 0;
        int failedCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Settlement settlement : pendingSettlements) {
            try {
                settlement.setStatus(Settlement.SettlementStatus.PROCESSING);
                settlementRepository.save(settlement);

                // Simulate payment processing
                settlement.setStatus(Settlement.SettlementStatus.COMPLETED);
                settlement.setBatchId(batchId);
                settlement.setProcessedAt(Instant.now());
                settlement.setSettledAt(Instant.now());
                settlementRepository.save(settlement);

                totalAmount = totalAmount.add(settlement.getWorkerPayout());
                successCount++;

                log.info("Settlement processed: id={}, payout={}", settlement.getId(), settlement.getWorkerPayout());
            } catch (Exception e) {
                settlement.setStatus(Settlement.SettlementStatus.FAILED);
                settlement.setBatchId(batchId);
                settlement.setFailureReason(e.getMessage());
                settlementRepository.save(settlement);
                failedCount++;

                log.error("Settlement processing failed: id={}, error={}", settlement.getId(), e.getMessage());
            }
        }

        // Update batch summary
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setTotalAmount(totalAmount);
        batch.setStatus(failedCount == 0 ? SettlementBatch.BatchStatus.COMPLETED : SettlementBatch.BatchStatus.FAILED);
        batch.setCompletedAt(Instant.now());
        batch = settlementBatchRepository.save(batch);

        log.info("Batch completed: batchId={}, total={}, success={}, failed={}, amount={}",
                batchId, pendingSettlements.size(), successCount, failedCount, totalAmount);

        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> getAllBatches(Pageable pageable) {
        return PageResponse.of(settlementBatchRepository.findAllBy(pageable), this::toBatchResponse);
    }

    private BatchResponse toBatchResponse(SettlementBatch batch) {
        return BatchResponse.builder()
                .id(batch.getId())
                .batchId(batch.getBatchId())
                .status(batch.getStatus().name())
                .totalCount(batch.getTotalCount())
                .successCount(batch.getSuccessCount())
                .failedCount(batch.getFailedCount())
                .totalAmount(batch.getTotalAmount())
                .startedAt(batch.getStartedAt())
                .completedAt(batch.getCompletedAt())
                .createdAt(batch.getCreatedAt())
                .build();
    }
}
