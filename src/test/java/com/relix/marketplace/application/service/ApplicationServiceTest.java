package com.relix.marketplace.application.service;

import com.relix.marketplace.application.dto.ApplicationCreateRequest;
import com.relix.marketplace.application.dto.ApplicationResponse;
import com.relix.marketplace.application.entity.Application;
import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import com.relix.marketplace.worker.service.EligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private EngagementRepository engagementRepository;
    @Mock private WorkerProfileRepository workerProfileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private EligibilityService eligibilityService;
    @Mock private AuditService auditService;

    @InjectMocks private ApplicationService applicationService;

    private User author;
    private User applicant;
    private Category category;
    private WorkerProfile authorWorker;

    @BeforeEach
    void setUp() {
        author = user(1L, "Author");
        applicant = user(2L, "Applicant");
        category = Category.builder().code("GENERAL").name("General").build();
        category.setId(10L);
        authorWorker = WorkerProfile.builder().user(author).displayName("Author Worker").build();
        authorWorker.setId(20L);
    }

    @Test
    @DisplayName("OFFER application uses the applicant as client and requires the fixed amount")
    void apply_offerFixed_succeedsWithoutWorkerEligibility() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        ticket.setWorker(authorWorker);
        ApplicationCreateRequest request = request("40.00");

        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            saved.setId(300L);
            return saved;
        });
        when(ticketRepository.incrementApplicationCount(100L)).thenReturn(1);

        ApplicationResponse response = applicationService.apply(100L, request);

        assertEquals(ApplicationStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("40.00"), response.getProposedAmount());
        verify(currentUserService, never()).requireWorkerProfile();
        verify(eligibilityService, never()).assertCanServe(anyLong(), anyLong());
    }

    @Test
    @DisplayName("REQUEST application lazily creates a worker profile and checks eligibility")
    void apply_request_checksWorkerEligibility() {
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.BUDGET_RANGE);
        ticket.setBudgetMin(new BigDecimal("100.00"));
        ticket.setBudgetMax(new BigDecimal("200.00"));
        WorkerProfile applicantWorker = WorkerProfile.builder()
                .user(applicant)
                .displayName("Applicant Worker")
                .build();
        ApplicationCreateRequest request = request("150.00");

        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);
        when(currentUserService.requireWorkerProfile()).thenReturn(applicantWorker);
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            saved.setId(301L);
            return saved;
        });
        when(ticketRepository.incrementApplicationCount(100L)).thenReturn(1);

        applicationService.apply(100L, request);

        verify(currentUserService).requireWorkerProfile();
        verify(eligibilityService).assertCanServe(2L, 10L);
    }

    @Test
    @DisplayName("A ticket author cannot apply to their own ticket")
    void apply_selfApplication_rejected() {
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.OPEN_BID);
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(author);

        assertThrows(BusinessException.class,
                () -> applicationService.apply(100L, request("10.00")));

        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("FIXED pricing rejects an amount different from the ticket price")
    void apply_fixedAmountMismatch_rejected() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        assertThrows(BusinessException.class,
                () -> applicationService.apply(100L, request("39.99")));
    }

    @Test
    @DisplayName("BUDGET_RANGE pricing rejects an out-of-range amount")
    void apply_budgetAmountOutOfRange_rejected() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.BUDGET_RANGE);
        ticket.setBudgetMin(new BigDecimal("100.00"));
        ticket.setBudgetMax(new BigDecimal("200.00"));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        assertThrows(BusinessException.class,
                () -> applicationService.apply(100L, request("250.00")));
    }

    @Test
    void apply_rejectsExpiredOpenTicketBeforeSweep() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        ticket.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> applicationService.apply(100L, request("40.00")));

        assertEquals("TICKET_EXPIRED", exception.getCode());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_usesSameExpiredErrorAfterSweep() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setStatus(TicketStatus.EXPIRED);
        ticket.setPrice(new BigDecimal("40.00"));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> applicationService.apply(100L, request("40.00")));

        assertEquals("TICKET_EXPIRED", exception.getCode());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_rejectsTicketWhoseServiceWindowEndedBeforeSweep() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        ticket.setServiceWindowEnd(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> applicationService.apply(100L, request("40.00")));

        assertEquals("TICKET_EXPIRED", exception.getCode());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_rejectsPastAndOutOfWindowSchedules() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        Instant windowStart = Instant.now().plus(2, ChronoUnit.DAYS);
        ticket.setServiceWindowStart(windowStart);
        ticket.setServiceWindowEnd(windowStart.plus(2, ChronoUnit.DAYS));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        ApplicationCreateRequest past = request("40.00");
        past.setProposedStart(Instant.now().minus(1, ChronoUnit.HOURS));
        past.setProposedEnd(windowStart.plus(1, ChronoUnit.HOURS));
        BusinessException pastException = assertThrows(
                BusinessException.class,
                () -> applicationService.apply(100L, past));
        assertEquals("APPLICATION_SCHEDULE_IN_PAST", pastException.getCode());

        ApplicationCreateRequest outside = request("40.00");
        outside.setProposedStart(windowStart.minus(1, ChronoUnit.HOURS));
        outside.setProposedEnd(windowStart.plus(1, ChronoUnit.HOURS));
        BusinessException outsideException = assertThrows(
                BusinessException.class,
                () -> applicationService.apply(100L, outside));
        assertEquals("APPLICATION_SCHEDULE_OUTSIDE_WINDOW", outsideException.getCode());
    }

    @Test
    void apply_rejectsScheduleShorterThanEstimatedDuration() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        ticket.setEstimatedDurationMinutes(120);
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        ApplicationCreateRequest request = request("40.00");
        request.setProposedStart(start);
        request.setProposedEnd(start.plus(119, ChronoUnit.MINUTES));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> applicationService.apply(100L, request));

        assertEquals("APPLICATION_DURATION_TOO_SHORT", exception.getCode());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_requiresCompleteProposalWhenTicketHasEitherWindowBound() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setPrice(new BigDecimal("40.00"));
        ticket.setServiceWindowStart(Instant.now().plus(1, ChronoUnit.DAYS));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(applicant);
        ApplicationCreateRequest request = request("40.00");
        request.setProposedStart(Instant.now().plus(2, ChronoUnit.DAYS));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> applicationService.apply(100L, request));

        assertEquals("APPLICATION_SCHEDULE_REQUIRED", exception.getCode());
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Accepting a REQUEST atomically matches the ticket and creates an engagement")
    void accept_request_createsEngagementAndRejectsOthers() {
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.OPEN_BID);
        Application application = application(300L, ticket, applicant);
        Instant windowStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant proposedStart = windowStart.plus(1, ChronoUnit.DAYS);
        Instant proposedEnd = proposedStart.plus(2, ChronoUnit.HOURS);
        ticket.setServiceWindowStart(windowStart);
        ticket.setServiceWindowEnd(windowStart.plus(4, ChronoUnit.DAYS));
        application.setProposedStart(proposedStart);
        application.setProposedEnd(proposedEnd);
        WorkerProfile applicantWorker = WorkerProfile.builder()
                .user(applicant)
                .displayName("Applicant Worker")
                .build();
        applicantWorker.setId(21L);

        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(author);
        when(workerProfileRepository.findByUser_Id(2L)).thenReturn(Optional.of(applicantWorker));
        when(engagementRepository.save(any(Engagement.class))).thenAnswer(invocation -> {
            Engagement saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });

        ApplicationResponse response = applicationService.accept(300L);

        assertEquals(ApplicationStatus.ACCEPTED, application.getStatus());
        assertEquals(TicketStatus.MATCHED, ticket.getStatus());
        assertEquals(500L, response.getEngagementId());
        verify(eligibilityService).assertCanServe(2L, 10L);
        verify(applicationRepository).rejectOtherPendingApplications(100L, 300L);

        ArgumentCaptor<Engagement> engagementCaptor = ArgumentCaptor.forClass(Engagement.class);
        verify(engagementRepository).save(engagementCaptor.capture());
        Engagement engagement = engagementCaptor.getValue();
        assertSame(author, engagement.getClient());
        assertSame(applicantWorker, engagement.getWorker());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        assertEquals(new BigDecimal("125.00"), engagement.getAmount());
        assertEquals(proposedStart, engagement.getScheduledStart());
        assertEquals(proposedEnd, engagement.getScheduledEnd());
    }

    @Test
    @DisplayName("Accepting an OFFER uses the applicant as client and ticket owner as worker")
    void accept_offer_assignsPartiesByDirection() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.FIXED);
        ticket.setWorker(authorWorker);
        Application application = application(300L, ticket, applicant);
        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(author);
        when(engagementRepository.save(any(Engagement.class))).thenAnswer(invocation -> {
            Engagement saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        applicationService.accept(300L);

        ArgumentCaptor<Engagement> captor = ArgumentCaptor.forClass(Engagement.class);
        verify(engagementRepository).save(captor.capture());
        assertSame(applicant, captor.getValue().getClient());
        assertSame(authorWorker, captor.getValue().getWorker());
        verify(eligibilityService, never()).assertCanServe(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Only the ticket author can accept an application")
    void accept_nonAuthor_rejected() {
        User stranger = user(3L, "Stranger");
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.OPEN_BID);
        Application application = application(300L, ticket, applicant);
        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        assertThrows(ForbiddenException.class, () -> applicationService.accept(300L));

        verify(engagementRepository, never()).save(any());
    }

    @Test
    void accept_rejectsTicketWhoseServiceWindowEndedBeforeSweep() {
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.OPEN_BID);
        ticket.setServiceWindowEnd(Instant.now().minus(1, ChronoUnit.MINUTES));
        Application application = application(300L, ticket, applicant);
        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(author);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> applicationService.accept(300L));

        assertEquals("TICKET_EXPIRED", exception.getCode());
        verify(engagementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ticket author can reject a pending application")
    void reject_byAuthor_changesStatus() {
        Ticket ticket = ticket(TicketKind.REQUEST, PricingMode.OPEN_BID);
        Application application = application(300L, ticket, applicant);
        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(author);

        applicationService.reject(300L);

        assertEquals(ApplicationStatus.REJECTED, application.getStatus());
    }

    @Test
    @DisplayName("Applicant can withdraw a pending application")
    void withdraw_byApplicant_changesStatus() {
        Ticket ticket = ticket(TicketKind.OFFER, PricingMode.OPEN_BID);
        Application application = application(300L, ticket, applicant);
        mockLockedApplication(ticket, application);
        when(currentUserService.getCurrentUser()).thenReturn(applicant);

        applicationService.withdraw(300L);

        assertEquals(ApplicationStatus.WITHDRAWN, application.getStatus());
    }

    private void mockLockedApplication(Ticket ticket, Application application) {
        when(applicationRepository.findTicketIdById(300L)).thenReturn(Optional.of(100L));
        when(ticketRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(applicationRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(application));
    }

    private Ticket ticket(TicketKind kind, PricingMode pricingMode) {
        Ticket ticket = Ticket.builder()
                .kind(kind)
                .author(author)
                .category(category)
                .title("Ticket")
                .pricingMode(pricingMode)
                .locationMode(LocationMode.REMOTE)
                .status(TicketStatus.OPEN)
                .build();
        ticket.setId(100L);
        return ticket;
    }

    private Application application(Long id, Ticket ticket, User user) {
        Application application = Application.builder()
                .ticket(ticket)
                .applicant(user)
                .proposedAmount(new BigDecimal("125.00"))
                .status(ApplicationStatus.PENDING)
                .build();
        application.setId(id);
        return application;
    }

    private ApplicationCreateRequest request(String amount) {
        return ApplicationCreateRequest.builder()
                .proposedAmount(new BigDecimal(amount))
                .message("Ready to help")
                .build();
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
