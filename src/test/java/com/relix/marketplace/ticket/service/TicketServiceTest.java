package com.relix.marketplace.ticket.service;

import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.ticket.dto.TicketCreateRequest;
import com.relix.marketplace.ticket.dto.TicketDetailResponse;
import com.relix.marketplace.ticket.dto.TicketSearchCriteria;
import com.relix.marketplace.ticket.dto.TicketUpdateRequest;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.service.EligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private EligibilityService eligibilityService;
    @Mock
    private TicketImageService ticketImageService;
    @Mock
    private FileUrlService fileUrlService;

    private TicketService ticketService;
    private User author;
    private Category category;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository,
                applicationRepository,
                categoryRepository,
                currentUserService,
                eligibilityService,
                ticketImageService,
                fileUrlService);
        author = user(10L, "Alex Client");
        category = category(20L, "MOVING");
    }

    @Test
    void requestTicketDoesNotCreateWorkerProfileOrRunEligibility() {
        TicketCreateRequest request = fixedRequest(TicketKind.REQUEST, new BigDecimal("80.00"));
        stubCreateDependencies();
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });

        TicketDetailResponse response = ticketService.createTicket(request);

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getKind()).isEqualTo(TicketKind.REQUEST);
        assertThat(response.getStatus()).isEqualTo(TicketStatus.DRAFT);
        assertThat(response.getWorker()).isNull();
        verify(currentUserService, never()).requireWorkerProfile();
        verify(eligibilityService, never()).assertCanServe(anyLong(), anyLong());
    }

    @Test
    void offerTicketLazilyCreatesWorkerAndChecksCategoryEligibility() {
        TicketCreateRequest request = fixedRequest(TicketKind.OFFER, new BigDecimal("80.00"));
        WorkerProfile worker = worker(40L, author);
        stubCreateDependencies();
        when(currentUserService.requireWorkerProfile()).thenReturn(worker);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketDetailResponse response = ticketService.createTicket(request);

        assertThat(response.getWorker().getId()).isEqualTo(40L);
        verify(currentUserService).requireWorkerProfile();
        verify(eligibilityService).assertCanServe(10L, 20L);
    }

    @Test
    void allPricingModesRejectInvalidFieldCombinations() {
        stubCreateDependencies();

        TicketCreateRequest missingFixedPrice = fixedRequest(TicketKind.REQUEST, null);
        TicketCreateRequest reversedRange = baseRequest(TicketKind.REQUEST, PricingMode.BUDGET_RANGE);
        reversedRange.setBudgetMin(new BigDecimal("100.00"));
        reversedRange.setBudgetMax(new BigDecimal("50.00"));
        TicketCreateRequest pricedOpenBid = baseRequest(TicketKind.REQUEST, PricingMode.OPEN_BID);
        pricedOpenBid.setPrice(BigDecimal.ONE);

        assertInvalidPricing(missingFixedPrice);
        assertInvalidPricing(reversedRange);
        assertInvalidPricing(pricedOpenBid);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createRejectsReversedServiceWindow() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        TicketCreateRequest request = fixedRequest(TicketKind.REQUEST, new BigDecimal("80.00"));
        request.setServiceWindowStart(start);
        request.setServiceWindowEnd(start.minus(1, ChronoUnit.HOURS));
        stubCreateDependencies();

        assertThatThrownBy(() -> ticketService.createTicket(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_SERVICE_WINDOW");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createRejectsServiceWindowShorterThanEstimatedDuration() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        TicketCreateRequest request = fixedRequest(TicketKind.REQUEST, new BigDecimal("80.00"));
        request.setEstimatedDurationMinutes(121);
        request.setServiceWindowStart(start);
        request.setServiceWindowEnd(start.plus(120, ChronoUnit.MINUTES));
        stubCreateDependencies();

        assertThatThrownBy(() -> ticketService.createTicket(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SERVICE_WINDOW_TOO_SHORT");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void matchedTicketFreezesCategoryAndPricingTerms() {
        Ticket matched = ticket(50L, TicketStatus.MATCHED, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(matched));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        TicketUpdateRequest request = TicketUpdateRequest.builder()
                .price(new BigDecimal("81.00"))
                .build();

        assertThatThrownBy(() -> ticketService.updateTicket(50L, request))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("MATCHED_TICKET_TERMS_FROZEN");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void matchedTicketStillAllowsNonCommercialDescriptionUpdate() {
        Ticket matched = ticket(50L, TicketStatus.MATCHED, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(matched));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        when(ticketRepository.save(matched)).thenReturn(matched);

        TicketDetailResponse response = ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder().description("Updated access notes").build());

        assertThat(response.getDescription()).isEqualTo("Updated access notes");
        verify(ticketRepository).save(matched);
    }

    @Test
    void matchedTicketFreezesServiceWindowAndExpiration() {
        Ticket matched = ticket(50L, TicketStatus.MATCHED, TicketKind.REQUEST);
        matched.setServiceWindowStart(Instant.now().plus(2, ChronoUnit.DAYS));
        matched.setServiceWindowEnd(Instant.now().plus(3, ChronoUnit.DAYS));
        matched.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(matched));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder().clearServiceWindow(true).build()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("MATCHED_TICKET_TERMS_FROZEN");

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder().clearExpiresAt(true).build()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("MATCHED_TICKET_TERMS_FROZEN");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void draftUpdateCanExplicitlyClearWindowAndExpiration() {
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        draft.setServiceWindowStart(Instant.now().plus(2, ChronoUnit.DAYS));
        draft.setServiceWindowEnd(Instant.now().plus(3, ChronoUnit.DAYS));
        draft.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        when(ticketRepository.save(draft)).thenReturn(draft);

        TicketDetailResponse response = ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder()
                        .clearServiceWindow(true)
                        .clearExpiresAt(true)
                        .build());

        assertThat(response.getServiceWindowStart()).isNull();
        assertThat(response.getServiceWindowEnd()).isNull();
        assertThat(response.getExpiresAt()).isNull();
        verify(ticketRepository).findByIdForUpdate(50L);
        verify(ticketRepository, never()).findDetailedById(50L);
    }

    @Test
    void updateRejectsEstimatedDurationLongerThanExistingServiceWindow() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        draft.setServiceWindowStart(start);
        draft.setServiceWindowEnd(start.plus(60, ChronoUnit.MINUTES));
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder().estimatedDurationMinutes(61).build()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SERVICE_WINDOW_TOO_SHORT");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void publishAcquiresTheTicketWriteLock() {
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketDetailResponse published = ticketService.publishTicket(50L);

        assertThat(published.getStatus()).isEqualTo(TicketStatus.OPEN);
        verify(ticketRepository).findByIdForUpdate(50L);
        verify(ticketImageService).promoteForPublish(draft);
        verify(ticketRepository, never()).findDetailedById(50L);
    }

    @Test
    void closeRejectsPendingApplicationsAfterAcquiringTheTicketWriteLock() {
        Ticket open = ticket(51L, TicketStatus.OPEN, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(open));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        when(ticketRepository.save(open)).thenReturn(open);
        when(applicationRepository.rejectPendingApplications(51L)).thenReturn(2);

        TicketDetailResponse closed = ticketService.closeTicket(51L);

        assertThat(closed.getStatus()).isEqualTo(TicketStatus.CLOSED);
        var lockOrder = inOrder(ticketRepository, applicationRepository);
        lockOrder.verify(ticketRepository).findByIdForUpdate(51L);
        lockOrder.verify(ticketRepository).save(open);
        lockOrder.verify(applicationRepository).rejectPendingApplications(51L);
        verify(ticketRepository, never()).findDetailedById(51L);
    }

    @Test
    void closeCannotOverrideEffectiveExpirationBeforeTheSweepRuns() {
        Ticket expiredOpen = ticket(51L, TicketStatus.OPEN, TicketKind.REQUEST);
        expiredOpen.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(expiredOpen));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> ticketService.closeTicket(51L))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");

        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(applicationRepository, never()).rejectPendingApplications(anyLong());
    }

    @Test
    void clearFlagsCannotBeCombinedWithReplacementValues() {
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder()
                        .clearServiceWindow(true)
                        .serviceWindowStart(Instant.now().plus(2, ChronoUnit.DAYS))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CONFLICTING_TICKET_UPDATE");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void publicDetailHidesDraftWithoutIncrementingViewCount() {
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        when(ticketRepository.findDetailedById(50L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> ticketService.getPublicTicket(50L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketRepository, never()).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void publicDetailAtomicallyIncrementsAndReturnsRefreshedViewCount() {
        Ticket initial = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        initial.setViewCount(4L);
        Ticket refreshed = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        refreshed.setViewCount(5L);
        when(ticketRepository.findDetailedById(50L))
                .thenReturn(Optional.of(initial), Optional.of(refreshed));
        when(ticketRepository.incrementActivePublicViewCount(anyLong(), any(Instant.class))).thenReturn(1);

        TicketDetailResponse response = ticketService.getPublicTicket(50L);

        assertThat(response.getViewCount()).isEqualTo(5L);
        verify(ticketRepository).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void publicDetailRejectsExpiredOpenTicketWithoutIncrementingViewCount() {
        Ticket expired = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findDetailedById(50L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> ticketService.getPublicTicket(50L))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");
        verify(ticketRepository, never()).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void publicDetailRejectsOpenTicketWhoseServiceWindowEnded() {
        Ticket expired = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        expired.setServiceWindowEnd(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findDetailedById(50L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> ticketService.getPublicTicket(50L))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");
        verify(ticketRepository, never()).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void conditionalViewIncrementClosesExpirationRace() {
        Ticket initiallyOpen = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        initiallyOpen.setServiceWindowEnd(Instant.now().plus(1, ChronoUnit.DAYS));
        Ticket expiredBySweep = ticket(50L, TicketStatus.EXPIRED, TicketKind.REQUEST);
        when(ticketRepository.findDetailedById(50L))
                .thenReturn(Optional.of(initiallyOpen), Optional.of(expiredBySweep));
        when(ticketRepository.incrementActivePublicViewCount(anyLong(), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> ticketService.getPublicTicket(50L))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");
    }

    @Test
    void publicDetailUsesSameExpiredErrorAfterExpirationSweep() {
        Ticket expired = ticket(50L, TicketStatus.EXPIRED, TicketKind.REQUEST);
        when(ticketRepository.findDetailedById(50L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> ticketService.getPublicTicket(50L))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");
        verify(ticketRepository, never()).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void authorCanReadPrivateDraftWithoutIncrementingViewCount() {
        Ticket draft = ticket(50L, TicketStatus.DRAFT, TicketKind.REQUEST);
        when(ticketRepository.findDetailedById(50L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        TicketDetailResponse response = ticketService.getMyTicket(50L);

        assertThat(response.getStatus()).isEqualTo(TicketStatus.DRAFT);
        verify(ticketRepository, never()).incrementActivePublicViewCount(anyLong(), any(Instant.class));
    }

    @Test
    void paginationIsClampedAndSortAlwaysHasIdTieBreaker() {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        TicketSearchCriteria criteria = TicketSearchCriteria.builder()
                .page(-7)
                .size(500)
                .sort("viewCount,asc")
                .build();

        ticketService.listPublicTickets(criteria);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("viewCount").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void reversedPublicPriceRangeIsRejectedBeforeQuerying() {
        TicketSearchCriteria criteria = TicketSearchCriteria.builder()
                .minPrice(new BigDecimal("200.00"))
                .maxPrice(new BigDecimal("100.00"))
                .build();

        assertThatThrownBy(() -> ticketService.listPublicTickets(criteria))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_PRICE_RANGE");
        verify(ticketRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void reversedServiceWindowFilterIsRejectedBeforeQuerying() {
        Instant serviceFrom = Instant.now().plus(3, ChronoUnit.DAYS);
        TicketSearchCriteria criteria = TicketSearchCriteria.builder()
                .serviceFrom(serviceFrom)
                .serviceTo(serviceFrom.minus(1, ChronoUnit.HOURS))
                .build();

        assertThatThrownBy(() -> ticketService.listPublicTickets(criteria))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_SERVICE_WINDOW_FILTER");
        verify(ticketRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void nonAuthorCannotUpdateTicket() {
        Ticket open = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(open));
        when(currentUserService.getCurrentUserId()).thenReturn(999L);

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder().title("Not mine").build()))
                .extracting("code")
                .isEqualTo("TICKET_AUTHOR_REQUIRED");
    }

    @Test
    void endedOpenTicketCannotBeRevivedByUpdateBeforeSweep() {
        Ticket ended = ticket(50L, TicketStatus.OPEN, TicketKind.REQUEST);
        ended.setServiceWindowEnd(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(ticketRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(ended));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> ticketService.updateTicket(
                50L,
                TicketUpdateRequest.builder()
                        .serviceWindowEnd(Instant.now().plus(2, ChronoUnit.DAYS))
                        .build()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_EXPIRED");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    private void assertInvalidPricing(TicketCreateRequest request) {
        assertThatThrownBy(() -> ticketService.createTicket(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_TICKET_PRICING");
    }

    private void stubCreateDependencies() {
        when(currentUserService.getCurrentUser()).thenReturn(author);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    }

    private TicketCreateRequest fixedRequest(TicketKind kind, BigDecimal price) {
        TicketCreateRequest request = baseRequest(kind, PricingMode.FIXED);
        request.setPrice(price);
        return request;
    }

    private TicketCreateRequest baseRequest(TicketKind kind, PricingMode pricingMode) {
        return TicketCreateRequest.builder()
                .kind(kind)
                .categoryId(category.getId())
                .title("Move a sofa")
                .pricingMode(pricingMode)
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .build();
    }

    private Ticket ticket(Long id, TicketStatus status, TicketKind kind) {
        Ticket ticket = Ticket.builder()
                .kind(kind)
                .author(author)
                .worker(null)
                .category(category)
                .title("Move a sofa")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("80.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(status)
                .build();
        ticket.setId(id);
        return ticket;
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .email("alex@example.com")
                .passwordHash("hash")
                .name(name)
                .build();
        user.setId(id);
        return user;
    }

    private Category category(Long id, String code) {
        Category category = Category.builder()
                .code(code)
                .name("Moving")
                .active(true)
                .build();
        category.setId(id);
        return category;
    }

    private WorkerProfile worker(Long id, User user) {
        WorkerProfile worker = WorkerProfile.builder()
                .user(user)
                .displayName("Alex Services")
                .build();
        worker.setId(id);
        return worker;
    }
}
