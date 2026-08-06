package com.relix.marketplace.settlement.scheduler;

import com.relix.marketplace.settlement.dto.BatchResponse;
import com.relix.marketplace.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementBatchService settlementBatchService;

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/Chicago")
    public void processDailySettlements() {
        log.info("Starting daily settlement batch processing...");
        try {
            BatchResponse result = settlementBatchService.processBatch();
            log.info("Daily settlement batch completed: batchId={}, success={}, failed={}",
                    result.getBatchId(), result.getSuccessCount(), result.getFailedCount());
        } catch (Exception e) {
            log.error("Daily settlement batch processing failed: {}", e.getMessage(), e);
        }
    }
}
