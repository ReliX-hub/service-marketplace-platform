package com.relix.marketplace.settlement.service;

import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.settlement.dto.SettlementResponse;
import com.relix.marketplace.settlement.dto.SettlementSummaryResponse;
import com.relix.marketplace.settlement.entity.Settlement;
import com.relix.marketplace.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final SettlementRepository settlementRepository;

    public SettlementResponse getSettlementByEngagementId(Long engagementId) {
        Settlement settlement = settlementRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement for engagement", engagementId));
        return toResponse(settlement);
    }

    public SettlementResponse getSettlementById(Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", id));
        return toResponse(settlement);
    }

    public PageResponse<SettlementResponse> getSettlementsByWorkerId(
            Long workerId,
            Pageable pageable) {
        return PageResponse.of(
                settlementRepository.findByWorkerId(workerId, pageable),
                this::toResponse);
    }

    public PageResponse<SettlementResponse> getAllSettlements(Pageable pageable) {
        return PageResponse.of(settlementRepository.findAllBy(pageable), this::toResponse);
    }

    public SettlementSummaryResponse getSettlementSummary(Long workerId) {
        BigDecimal completedAmount = settlementRepository.sumWorkerPayoutByWorkerIdAndStatus(
                workerId, Settlement.SettlementStatus.COMPLETED);
        BigDecimal pendingAmount = settlementRepository.sumWorkerPayoutByWorkerIdAndStatus(
                workerId, Settlement.SettlementStatus.PENDING);

        long completedCount = settlementRepository.countByWorkerIdAndStatus(
                workerId, Settlement.SettlementStatus.COMPLETED);
        long pendingCount = settlementRepository.countByWorkerIdAndStatus(
                workerId, Settlement.SettlementStatus.PENDING);
        long failedCount = settlementRepository.countByWorkerIdAndStatus(
                workerId, Settlement.SettlementStatus.FAILED);

        long totalCount = completedCount + pendingCount + failedCount;
        BigDecimal totalEarnings = completedAmount.add(pendingAmount);

        return SettlementSummaryResponse.builder()
                .totalEarnings(totalEarnings)
                .completedAmount(completedAmount)
                .pendingAmount(pendingAmount)
                .totalCount(totalCount)
                .completedCount(completedCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .build();
    }

    public SettlementSummaryResponse getOverallSettlementSummary() {
        BigDecimal completedAmount = settlementRepository.sumWorkerPayoutByStatus(Settlement.SettlementStatus.COMPLETED);
        BigDecimal pendingAmount = settlementRepository.sumWorkerPayoutByStatus(Settlement.SettlementStatus.PENDING);

        long completedCount = settlementRepository.countByStatus(Settlement.SettlementStatus.COMPLETED);
        long pendingCount = settlementRepository.countByStatus(Settlement.SettlementStatus.PENDING);
        long failedCount = settlementRepository.countByStatus(Settlement.SettlementStatus.FAILED);

        return SettlementSummaryResponse.builder()
                .totalEarnings(completedAmount.add(pendingAmount))
                .completedAmount(completedAmount)
                .pendingAmount(pendingAmount)
                .totalCount(completedCount + pendingCount + failedCount)
                .completedCount(completedCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .build();
    }

    public SettlementResponse getSettlementByIdWithAccess(Long id, Long currentUserId, boolean isAdmin) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", id));

        if (!isAdmin && !settlement.getEngagement().getWorker().getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this settlement");
        }

        return toResponse(settlement);
    }

    public SettlementResponse getSettlementByEngagementIdWithAccess(Long engagementId, Long currentUserId, boolean isAdmin) {
        Settlement settlement = settlementRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement for engagement", engagementId));

        if (!isAdmin && !settlement.getEngagement().getWorker().getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this settlement");
        }

        return toResponse(settlement);
    }

    @Transactional
    public Settlement createSettlement(Engagement engagement) {
        if (settlementRepository.existsByEngagementId(engagement.getId())) {
            log.info("Settlement already exists for engagement: {}", engagement.getId());
            return settlementRepository.findByEngagementId(engagement.getId()).orElseThrow();
        }

        if (engagement.getStatus() != EngagementStatus.COMPLETED) {
            throw new BusinessException(
                    "Cannot create settlement for a non-completed engagement",
                    "INVALID_ENGAGEMENT_STATUS");
        }

        BigDecimal totalAmount = engagement.getAmount();
        BigDecimal platformFee = totalAmount.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal workerPayout = totalAmount.subtract(platformFee);

        Settlement settlement = Settlement.builder()
                .engagement(engagement)
                .totalAmount(totalAmount)
                .platformFee(platformFee)
                .workerPayout(workerPayout)
                .status(Settlement.SettlementStatus.PENDING)
                .build();

        settlement = settlementRepository.save(settlement);
        log.info("Settlement created: id={}, engagementId={}, total={}, fee={}, payout={}, status=PENDING",
                settlement.getId(), engagement.getId(), totalAmount, platformFee, workerPayout);

        return settlement;
    }

    public SettlementResponse toResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .engagementId(settlement.getEngagement().getId())
                .totalAmount(settlement.getTotalAmount())
                .platformFee(settlement.getPlatformFee())
                .workerPayout(settlement.getWorkerPayout())
                .status(settlement.getStatus().name())
                .settledAt(settlement.getSettledAt())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
