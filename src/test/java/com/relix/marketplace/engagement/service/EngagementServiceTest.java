package com.relix.marketplace.engagement.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.engagement.dto.DisputeResolutionRequest;
import com.relix.marketplace.engagement.dto.EngagementResponse;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.payment.dto.PaymentRequest;
import com.relix.marketplace.payment.dto.PaymentResponse;
import com.relix.marketplace.payment.service.PaymentService;
import com.relix.marketplace.refund.dto.RefundSummaryResponse;
import com.relix.marketplace.refund.entity.Refund;
import com.relix.marketplace.refund.service.RefundService;
import com.relix.marketplace.settlement.service.SettlementService;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock private EngagementRepository engagementRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private PaymentService paymentService;
    @Mock private RefundService refundService;
    @Mock private SettlementService settlementService;
    @Mock private WorkerProfileRepository workerProfileRepository;
    @Mock private AuditService auditService;
    @Mock private ImageProperties imageProperties;

    @InjectMocks private EngagementService engagementService;

    private User client;
    private User workerUser;
    private User stranger;
    private WorkerProfile worker;
    private Engagement engagement;

    @BeforeEach
    void setUp() {
        client = user(1L, "Client");
        workerUser = user(2L, "Worker");
        stranger = user(3L, "Stranger");
        worker = WorkerProfile.builder()
                .user(workerUser)
                .displayName("Worker Profile")
                .completedJobs(4)
                .build();
        worker.setId(20L);

        Ticket ticket = Ticket.builder()
                .kind(TicketKind.REQUEST)
                .author(client)
                .title("Repair a sink")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("80.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(TicketStatus.MATCHED)
                .build();
        ticket.setId(100L);

        engagement = Engagement.builder()
                .client(client)
                .worker(worker)
                .ticket(ticket)
                .status(EngagementStatus.ACCEPTED)
                .amount(new BigDecimal("80.00"))
                .build();
        engagement.setId(500L);
    }

    @Test
    void payDelegatesOnlyForTheClient() {
        PaymentRequest request = PaymentRequest.builder().requestId("req-1").build();
        PaymentResponse expected = PaymentResponse.builder()
                .paymentId(900L)
                .engagementId(500L)
                .status("PENDING")
                .build();
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(paymentService.pay(engagement, request)).thenReturn(expected);

        PaymentResponse actual = engagementService.pay(500L, request);

        assertEquals(expected, actual);
        verify(paymentService).pay(engagement, request);
    }

    @Test
    void nonClientCannotPay() {
        PaymentRequest request = PaymentRequest.builder().requestId("req-1").build();
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);

        assertThrows(ForbiddenException.class, () -> engagementService.pay(500L, request));

        verify(paymentService, never()).pay(any(), any());
    }

    @Test
    void workerCanStartAFundedEngagement() {
        engagement.setStatus(EngagementStatus.FUNDED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);

        EngagementResponse response = engagementService.start(500L);

        assertEquals(EngagementStatus.IN_PROGRESS, response.getStatus());
        assertNotNull(engagement.getStartedAt());
        verify(auditService).log(
                eq("ENGAGEMENT"), eq(500L), eq("ENGAGEMENT_STARTED"),
                eq("WORKER"), eq(2L), any());
    }

    @Test
    void onlyTheAssignedWorkerCanStart() {
        engagement.setStatus(EngagementStatus.FUNDED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        assertThrows(ForbiddenException.class, () -> engagementService.start(500L));

        assertEquals(EngagementStatus.FUNDED, engagement.getStatus());
    }

    @Test
    void workerCanDeliverAnInProgressEngagement() {
        engagement.setStatus(EngagementStatus.IN_PROGRESS);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);

        EngagementResponse response = engagementService.deliver(500L);

        assertEquals(EngagementStatus.DELIVERED, response.getStatus());
        assertNotNull(engagement.getDeliveredAt());
    }

    @Test
    void configuredEvidenceRequirementBlocksAnEmptyDelivery() {
        engagement.setStatus(EngagementStatus.IN_PROGRESS);
        engagement.setDeliverableCount((short) 0);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        when(imageProperties.isRequireDeliverableOnDeliver()).thenReturn(true);

        var exception = assertThrows(
                com.relix.marketplace.common.exception.ConflictException.class,
                () -> engagementService.deliver(500L));

        assertEquals("DELIVERABLE_REQUIRED", exception.getCode());
        assertEquals(EngagementStatus.IN_PROGRESS, engagement.getStatus());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void configuredEvidenceRequirementAllowsDeliveryWithEvidence() {
        engagement.setStatus(EngagementStatus.IN_PROGRESS);
        engagement.setDeliverableCount((short) 1);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        when(imageProperties.isRequireDeliverableOnDeliver()).thenReturn(true);

        EngagementResponse response = engagementService.deliver(500L);

        assertEquals(EngagementStatus.DELIVERED, response.getStatus());
        assertEquals(1, response.getDeliverableCount());
    }

    @Test
    void clientApprovalCompletesSettlesAndCreditsTheWorker() {
        engagement.setStatus(EngagementStatus.DELIVERED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);

        EngagementResponse response = engagementService.approve(500L);

        assertEquals(EngagementStatus.COMPLETED, response.getStatus());
        assertNotNull(engagement.getApprovedAt());
        assertNotNull(engagement.getCompletedAt());
        assertEquals(5, worker.getCompletedJobs());
        verify(settlementService).createSettlement(engagement);
        verify(workerProfileRepository).save(worker);
    }

    @Test
    void clientCanDisputeADeliveredEngagement() {
        engagement.setStatus(EngagementStatus.DELIVERED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);

        EngagementResponse response = engagementService.dispute(500L, " Work was incomplete ");

        assertEquals(EngagementStatus.DISPUTED, response.getStatus());
        assertEquals("Work was incomplete", engagement.getDisputeReason());
        assertNotNull(engagement.getDisputedAt());
    }

    @Test
    void disputeRequiresAReason() {
        engagement.setStatus(EngagementStatus.DELIVERED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> engagementService.dispute(500L, "  "));

        assertEquals("DISPUTE_REASON_REQUIRED", exception.getCode());
        assertEquals(EngagementStatus.DELIVERED, engagement.getStatus());
    }

    @Test
    void cancellingAFundedEngagementInitiatesARefund() {
        engagement.setStatus(EngagementStatus.FUNDED);
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(paymentService.hasSucceededPayment(500L)).thenReturn(true);

        EngagementResponse response = engagementService.cancel(500L, "No longer needed");

        assertEquals(EngagementStatus.CANCELLED, response.getStatus());
        assertNotNull(engagement.getCancelledAt());
        verify(refundService).createRefund(engagement, "No longer needed");
    }

    @Test
    void pendingPaymentBlocksCancellation() {
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(paymentService.hasPendingPayment(500L)).thenReturn(true);
        when(paymentService.hasSucceededPayment(500L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> engagementService.cancel(500L, null));

        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verify(refundService, never()).createRefund(any(), any());
    }

    @Test
    void nonParticipantCannotCancel() {
        lockEngagement();
        when(currentUserService.getCurrentUser()).thenReturn(stranger);
        when(currentUserService.isAdmin()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> engagementService.cancel(500L, null));
    }

    @Test
    void adminCanResolveADisputeAsCompleted() {
        User admin = user(9L, "Admin");
        admin.setRole(User.UserRole.ADMIN);
        engagement.setStatus(EngagementStatus.DISPUTED);
        lockEngagement();
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        DisputeResolutionRequest request = DisputeResolutionRequest.builder()
                .resolution(DisputeResolutionRequest.Resolution.COMPLETED)
                .reason("Evidence supports completion")
                .build();

        EngagementResponse response = engagementService.resolveDispute(500L, request);

        assertEquals(EngagementStatus.COMPLETED, response.getStatus());
        assertTrue(engagement.getCompletedAt() != null);
        verify(settlementService).createSettlement(engagement);
    }

    @Test
    void adminCanResolveADisputeWithARefund() {
        User admin = user(9L, "Admin");
        admin.setRole(User.UserRole.ADMIN);
        engagement.setStatus(EngagementStatus.DISPUTED);
        lockEngagement();
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        DisputeResolutionRequest request = DisputeResolutionRequest.builder()
                .resolution(DisputeResolutionRequest.Resolution.REFUNDED)
                .reason("Client claim upheld")
                .build();

        engagementService.resolveDispute(500L, request);

        verify(refundService).createRefund(engagement, "Client claim upheld");
        verify(settlementService, never()).createSettlement(any());
    }

    @Test
    void engagementWithoutARefundReturnsANullRefundSummary() {
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(refundService.getSummaryByEngagementId(500L)).thenReturn(Optional.empty());

        EngagementResponse response = engagementService.getEngagement(500L);

        assertNull(response.getRefundSummary());
        verify(refundService).getSummaryByEngagementId(500L);
    }

    @ParameterizedTest
    @EnumSource(Refund.RefundStatus.class)
    void engagementMapsEveryRefundLifecycleStatus(Refund.RefundStatus status) {
        Instant refundedAt = Instant.parse("2026-08-05T13:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-05T13:01:00Z");
        RefundSummaryResponse summary = new RefundSummaryResponse(
                81L,
                new BigDecimal("80.00"),
                status,
                refundedAt,
                status == Refund.RefundStatus.FAILED ? "provider rejected refund" : null,
                updatedAt);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(refundService.getSummaryByEngagementId(500L)).thenReturn(Optional.of(summary));

        EngagementResponse response = engagementService.getEngagement(500L);

        assertNotNull(response.getRefundSummary());
        assertEquals(81L, response.getRefundSummary().id());
        assertEquals(new BigDecimal("80.00"), response.getRefundSummary().amount());
        assertEquals(status, response.getRefundSummary().status());
        assertEquals(status == Refund.RefundStatus.FAILED ? "provider rejected refund" : null,
                response.getRefundSummary().failureMessage());
        assertEquals(refundedAt, response.getRefundSummary().refundedAt());
        assertEquals(updatedAt, response.getRefundSummary().updatedAt());
    }

    @Test
    void bothClientAndWorkerCanSeeTheSameRefundSummaryButANonParticipantCannot() {
        RefundSummaryResponse summary = refundSummary(Refund.RefundStatus.PROCESSING);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(refundService.getSummaryByEngagementId(500L)).thenReturn(Optional.of(summary));

        when(currentUserService.getCurrentUser()).thenReturn(client);
        EngagementResponse clientView = engagementService.getEngagement(500L);

        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        EngagementResponse workerView = engagementService.getEngagement(500L);

        when(currentUserService.getCurrentUser()).thenReturn(stranger);
        assertThrows(ForbiddenException.class, () -> engagementService.getEngagement(500L));

        assertEquals(summary, clientView.getRefundSummary());
        assertEquals(summary, workerView.getRefundSummary());
        verify(refundService, times(2)).getSummaryByEngagementId(500L);
    }

    @Test
    void engagementListLoadsRefundSummariesInOneBatchInsteadOfOneQueryPerEngagement() {
        Engagement secondEngagement = secondEngagement();
        RefundSummaryResponse pending = refundSummary(Refund.RefundStatus.PENDING);
        RefundSummaryResponse completed = refundSummary(Refund.RefundStatus.COMPLETED);
        PageRequest pageable = PageRequest.of(0, 20);
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(engagementRepository.findByClient_Id(1L, pageable)).thenReturn(
                new PageImpl<>(List.of(engagement, secondEngagement), pageable, 2));
        when(refundService.getSummariesByEngagementIds(argThat(ids ->
                Set.copyOf(ids).equals(Set.of(500L, 501L))))).thenReturn(
                Map.of(500L, pending, 501L, completed));

        PageResponse<EngagementResponse> response = engagementService.getMyEngagements(
                "client", null, pageable);

        assertEquals(2, response.getItems().size());
        assertEquals(pending, response.getItems().get(0).getRefundSummary());
        assertEquals(completed, response.getItems().get(1).getRefundSummary());
        verify(refundService).getSummariesByEngagementIds(argThat(ids ->
                Set.copyOf(ids).equals(Set.of(500L, 501L))));
        verify(refundService, never()).getSummaryByEngagementId(anyLong());
    }

    private void lockEngagement() {
        when(engagementRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(engagement));
    }

    private Engagement secondEngagement() {
        Ticket ticket = Ticket.builder()
                .kind(TicketKind.OFFER)
                .author(workerUser)
                .title("Move boxes")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("70.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(TicketStatus.MATCHED)
                .build();
        ticket.setId(101L);

        Engagement second = Engagement.builder()
                .client(client)
                .worker(worker)
                .ticket(ticket)
                .status(EngagementStatus.CANCELLED)
                .amount(new BigDecimal("70.00"))
                .build();
        second.setId(501L);
        return second;
    }

    private RefundSummaryResponse refundSummary(Refund.RefundStatus status) {
        return new RefundSummaryResponse(
                81L,
                new BigDecimal("80.00"),
                status,
                Instant.parse("2026-08-05T13:00:00Z"),
                status == Refund.RefundStatus.FAILED ? "provider rejected refund" : null,
                Instant.parse("2026-08-05T13:01:00Z"));
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .email(name.toLowerCase() + "@test.com")
                .passwordHash("hash")
                .name(name)
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }
}
