package com.relix.marketplace.ticket.service;

import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.ticket.dto.TicketCreateRequest;
import com.relix.marketplace.ticket.dto.TicketDetailResponse;
import com.relix.marketplace.ticket.dto.TicketSearchCriteria;
import com.relix.marketplace.ticket.dto.TicketSummaryResponse;
import com.relix.marketplace.ticket.dto.TicketUpdateRequest;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.ticket.repository.TicketSpecifications;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Set<TicketStatus> PUBLIC_DETAIL_STATUSES =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.MATCHED, TicketStatus.CLOSED);
    private static final Map<String, String> ALLOWED_SORTS = Map.ofEntries(
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt"),
            Map.entry("price", "price"),
            Map.entry("budgetMin", "budgetMin"),
            Map.entry("budgetMax", "budgetMax"),
            Map.entry("viewCount", "viewCount"),
            Map.entry("applicationCount", "applicationCount"),
            Map.entry("serviceWindowStart", "serviceWindowStart"),
            Map.entry("serviceWindowEnd", "serviceWindowEnd"),
            Map.entry("expiresAt", "expiresAt"),
            Map.entry("title", "title"));

    private final TicketRepository ticketRepository;
    private final ApplicationRepository applicationRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;
    private final EligibilityService eligibilityService;
    private final TicketImageService ticketImageService;
    private final FileUrlService fileUrlService;

    public PageResponse<TicketSummaryResponse> listPublicTickets(TicketSearchCriteria criteria) {
        TicketSearchCriteria safeCriteria = criteria == null ? new TicketSearchCriteria() : criteria;
        validateSearchPrices(safeCriteria.getMinPrice(), safeCriteria.getMaxPrice());
        validateSearchWindow(safeCriteria.getServiceFrom(), safeCriteria.getServiceTo());
        Pageable pageable = pageable(
                safeCriteria.getPage(),
                safeCriteria.getSize(),
                safeCriteria.getSort());
        Page<Ticket> tickets = ticketRepository.findAll(
                TicketSpecifications.publicBoard(safeCriteria, Instant.now()),
                pageable);
        return PageResponse.of(tickets, ticket -> TicketSummaryResponse.from(ticket, fileUrlService));
    }

    @Transactional
    public TicketDetailResponse getPublicTicket(Long ticketId) {
        Ticket ticket = findDetailed(ticketId);
        Instant now = Instant.now();
        assertNotEffectivelyExpired(ticket, now);
        if (!PUBLIC_DETAIL_STATUSES.contains(ticket.getStatus())) {
            // Do not disclose private drafts or cancelled/expired listings.
            throw new ResourceNotFoundException("Ticket", ticketId);
        }

        if (ticketRepository.incrementActivePublicViewCount(ticketId, now) != 1) {
            Ticket current = findDetailed(ticketId);
            assertNotEffectivelyExpired(current, Instant.now());
            throw new ResourceNotFoundException("Ticket", ticketId);
        }
        Ticket refreshed = findDetailed(ticketId);
        return toDetailResponse(refreshed);
    }

    public PageResponse<TicketSummaryResponse> getMyTickets(
            TicketKind kind,
            TicketStatus status,
            Integer page,
            Integer size,
            String sort) {
        Long authorId = currentUserService.getCurrentUserId();
        Page<Ticket> tickets = ticketRepository.findAll(
                TicketSpecifications.ownedBy(authorId, kind, status),
                pageable(page, size, sort));
        return PageResponse.of(tickets, ticket -> TicketSummaryResponse.from(ticket, fileUrlService));
    }

    public TicketDetailResponse getMyTicket(Long ticketId) {
        Ticket ticket = findDetailed(ticketId);
        assertAuthor(ticket);
        return toDetailResponse(ticket);
    }

    @Transactional
    public TicketDetailResponse createTicket(TicketCreateRequest request) {
        User author = currentUserService.getCurrentUser();
        Category category = requireActiveCategory(request.getCategoryId());
        WorkerProfile worker = null;

        if (request.getKind() == TicketKind.OFFER) {
            worker = currentUserService.requireWorkerProfile();
            eligibilityService.assertCanServe(author.getId(), category.getId());
        }

        Ticket ticket = Ticket.builder()
                .kind(request.getKind())
                .author(author)
                .worker(worker)
                .category(category)
                .title(normalizeRequiredText(request.getTitle(), "title"))
                .description(normalizeOptionalText(request.getDescription()))
                .pricingMode(request.getPricingMode())
                .price(request.getPrice())
                .budgetMin(request.getBudgetMin())
                .budgetMax(request.getBudgetMax())
                .currency(normalizeCurrency(request.getCurrency()))
                .locationMode(request.getLocationMode())
                .address(normalizeOptionalText(request.getAddress()))
                .city(normalizeOptionalText(request.getCity()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .serviceWindowStart(request.getServiceWindowStart())
                .serviceWindowEnd(request.getServiceWindowEnd())
                .expiresAt(request.getExpiresAt())
                .status(TicketStatus.DRAFT)
                .build();

        validateTicket(ticket);
        validateFutureServiceWindow(ticket);
        return toDetailResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketDetailResponse updateTicket(Long ticketId, TicketUpdateRequest request) {
        Ticket ticket = findDetailedForUpdate(ticketId);
        assertAuthor(ticket);
        assertNotEffectivelyExpired(ticket, Instant.now());
        assertEditable(ticket);
        validateClearInstructions(request);
        assertMatchedFieldsRemainFrozen(ticket, request);
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(
                    "Ticket expiration must be in the future",
                    "INVALID_TICKET_EXPIRATION",
                    "expiresAt",
                    request.getExpiresAt());
        }
        validateFutureServiceWindowUpdate(request);

        boolean categoryChanged = request.getCategoryId() != null
                && !Objects.equals(ticket.getCategory().getId(), request.getCategoryId());
        if (categoryChanged) {
            Category category = requireActiveCategory(request.getCategoryId());
            if (ticket.getKind() == TicketKind.OFFER) {
                eligibilityService.assertCanServe(ticket.getAuthor().getId(), category.getId());
            }
            ticket.setCategory(category);
        }

        applyUpdate(ticket, request);
        validateTicket(ticket);
        return toDetailResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketDetailResponse publishTicket(Long ticketId) {
        Ticket ticket = findDetailedForUpdate(ticketId);
        assertAuthor(ticket);
        if (ticket.getStatus() != TicketStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft ticket can be published",
                    "TICKET_NOT_PUBLISHABLE");
        }
        if (ticket.getExpiresAt() != null && !ticket.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(
                    "Ticket expiration must be in the future",
                    "INVALID_TICKET_EXPIRATION",
                    "expiresAt",
                    ticket.getExpiresAt());
        }
        validateFutureServiceWindow(ticket);
        requireCategoryStillActive(ticket.getCategory());
        if (ticket.getKind() == TicketKind.OFFER) {
            WorkerProfile worker = currentUserService.requireWorkerProfile();
            if (ticket.getWorker() == null || !Objects.equals(worker.getId(), ticket.getWorker().getId())) {
                throw new ForbiddenException(
                        "The offer is not owned by the current worker profile",
                        "TICKET_WORKER_MISMATCH");
            }
            eligibilityService.assertCanServe(ticket.getAuthor().getId(), ticket.getCategory().getId());
        }
        validateTicket(ticket);
        ticket.setStatus(TicketStatus.OPEN);
        ticketImageService.promoteForPublish(ticket);
        return toDetailResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketDetailResponse closeTicket(Long ticketId) {
        Ticket ticket = findDetailedForUpdate(ticketId);
        assertAuthor(ticket);
        assertNotEffectivelyExpired(ticket, Instant.now());
        if (ticket.getStatus() != TicketStatus.OPEN && ticket.getStatus() != TicketStatus.MATCHED) {
            throw new ConflictException(
                    "Only an open or matched ticket can be closed",
                    "TICKET_NOT_CLOSABLE");
        }
        ticket.setStatus(TicketStatus.CLOSED);
        ticket = ticketRepository.save(ticket);
        applicationRepository.rejectPendingApplications(ticketId);
        return toDetailResponse(ticket);
    }

    private void applyUpdate(Ticket ticket, TicketUpdateRequest request) {
        if (request.getTitle() != null) {
            ticket.setTitle(normalizeRequiredText(request.getTitle(), "title"));
        }
        if (request.getDescription() != null) {
            ticket.setDescription(normalizeOptionalText(request.getDescription()));
        }

        boolean pricingModeChanged = request.getPricingMode() != null
                && request.getPricingMode() != ticket.getPricingMode();
        if (pricingModeChanged) {
            ticket.setPricingMode(request.getPricingMode());
            // Changing modes replaces the complete pricing tuple, including explicit nulls.
            ticket.setPrice(request.getPrice());
            ticket.setBudgetMin(request.getBudgetMin());
            ticket.setBudgetMax(request.getBudgetMax());
        } else {
            if (request.getPrice() != null) {
                ticket.setPrice(request.getPrice());
            }
            if (request.getBudgetMin() != null) {
                ticket.setBudgetMin(request.getBudgetMin());
            }
            if (request.getBudgetMax() != null) {
                ticket.setBudgetMax(request.getBudgetMax());
            }
        }

        if (request.getCurrency() != null) {
            ticket.setCurrency(normalizeCurrency(request.getCurrency()));
        }
        if (request.getLocationMode() != null) {
            ticket.setLocationMode(request.getLocationMode());
        }
        if (request.getAddress() != null) {
            ticket.setAddress(normalizeOptionalText(request.getAddress()));
        }
        if (request.getCity() != null) {
            ticket.setCity(normalizeOptionalText(request.getCity()));
        }
        if (request.getLatitude() != null) {
            ticket.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            ticket.setLongitude(request.getLongitude());
        }
        if (request.getEstimatedDurationMinutes() != null) {
            ticket.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }
        if (Boolean.TRUE.equals(request.getClearServiceWindow())) {
            ticket.setServiceWindowStart(null);
            ticket.setServiceWindowEnd(null);
        } else if (request.getServiceWindowStart() != null) {
            ticket.setServiceWindowStart(request.getServiceWindowStart());
        }
        if (!Boolean.TRUE.equals(request.getClearServiceWindow())
                && request.getServiceWindowEnd() != null) {
            ticket.setServiceWindowEnd(request.getServiceWindowEnd());
        }
        if (Boolean.TRUE.equals(request.getClearExpiresAt())) {
            ticket.setExpiresAt(null);
        } else if (request.getExpiresAt() != null) {
            ticket.setExpiresAt(request.getExpiresAt());
        }
    }

    private void assertMatchedFieldsRemainFrozen(Ticket ticket, TicketUpdateRequest request) {
        if (ticket.getStatus() != TicketStatus.MATCHED) {
            return;
        }

        boolean categoryChanged = request.getCategoryId() != null
                && !Objects.equals(ticket.getCategory().getId(), request.getCategoryId());
        boolean pricingChanged = request.getPricingMode() != null
                && request.getPricingMode() != ticket.getPricingMode();
        boolean priceChanged = request.getPrice() != null
                && differentAmount(ticket.getPrice(), request.getPrice());
        boolean minimumChanged = request.getBudgetMin() != null
                && differentAmount(ticket.getBudgetMin(), request.getBudgetMin());
        boolean maximumChanged = request.getBudgetMax() != null
                && differentAmount(ticket.getBudgetMax(), request.getBudgetMax());
        boolean currencyChanged = request.getCurrency() != null
                && !ticket.getCurrency().equalsIgnoreCase(request.getCurrency());
        boolean serviceWindowChanged = (request.getServiceWindowStart() != null
                && !Objects.equals(ticket.getServiceWindowStart(), request.getServiceWindowStart()))
                || (request.getServiceWindowEnd() != null
                && !Objects.equals(ticket.getServiceWindowEnd(), request.getServiceWindowEnd()))
                || (Boolean.TRUE.equals(request.getClearServiceWindow())
                && (ticket.getServiceWindowStart() != null || ticket.getServiceWindowEnd() != null));
        boolean expirationChanged = (request.getExpiresAt() != null
                && !Objects.equals(ticket.getExpiresAt(), request.getExpiresAt()))
                || (Boolean.TRUE.equals(request.getClearExpiresAt()) && ticket.getExpiresAt() != null);

        if (categoryChanged || pricingChanged || priceChanged || minimumChanged
                || maximumChanged || currencyChanged || serviceWindowChanged || expirationChanged) {
            throw new ConflictException(
                    "Category, pricing, service window, and expiration cannot change after a ticket is matched",
                    "MATCHED_TICKET_TERMS_FROZEN");
        }
    }

    private void validateTicket(Ticket ticket) {
        if (ticket.getKind() == null) {
            throw requiredField("kind");
        }
        if (ticket.getAuthor() == null) {
            throw requiredField("author");
        }
        if (ticket.getCategory() == null) {
            throw requiredField("categoryId");
        }
        if (ticket.getLocationMode() == null) {
            throw requiredField("locationMode");
        }
        if (ticket.getKind() == TicketKind.OFFER && ticket.getWorker() == null) {
            throw new BusinessException(
                    "A service offer requires a worker profile",
                    "WORKER_PROFILE_REQUIRED",
                    "kind",
                    TicketKind.OFFER);
        }
        if (ticket.getKind() == TicketKind.REQUEST && ticket.getWorker() != null) {
            throw new BusinessException(
                    "A client request cannot be attached to a worker profile",
                    "INVALID_TICKET_DIRECTION");
        }
        validatePricing(ticket.getPricingMode(), ticket.getPrice(), ticket.getBudgetMin(), ticket.getBudgetMax());
        if (ticket.getEstimatedDurationMinutes() != null && ticket.getEstimatedDurationMinutes() <= 0) {
            throw new BusinessException(
                    "Estimated duration must be positive",
                    "INVALID_TICKET_DURATION",
                    "estimatedDurationMinutes",
                    ticket.getEstimatedDurationMinutes());
        }
        validateServiceWindowOrder(ticket.getServiceWindowStart(), ticket.getServiceWindowEnd());
        validateServiceWindowCapacity(
                ticket.getServiceWindowStart(),
                ticket.getServiceWindowEnd(),
                ticket.getEstimatedDurationMinutes());
        if (ticket.getExpiresAt() != null && ticket.getStatus() == TicketStatus.DRAFT
                && !ticket.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(
                    "Ticket expiration must be in the future",
                    "INVALID_TICKET_EXPIRATION",
                    "expiresAt",
                    ticket.getExpiresAt());
        }
    }

    private void validatePricing(
            PricingMode mode,
            BigDecimal price,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {
        if (mode == null) {
            throw requiredField("pricingMode");
        }
        switch (mode) {
            case FIXED -> {
                if (price == null || isNegative(price) || budgetMin != null || budgetMax != null) {
                    throw invalidPricing("FIXED requires a non-negative price and no budget range");
                }
            }
            case BUDGET_RANGE -> {
                if (price != null || budgetMin == null || budgetMax == null
                        || isNegative(budgetMin) || budgetMax.compareTo(budgetMin) < 0) {
                    throw invalidPricing("BUDGET_RANGE requires 0 <= budgetMin <= budgetMax and no fixed price");
                }
            }
            case OPEN_BID -> {
                if (price != null || budgetMin != null || budgetMax != null) {
                    throw invalidPricing("OPEN_BID cannot include price or budget values");
                }
            }
        }
    }

    private void validateSearchPrices(BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && isNegative(minimum) || maximum != null && isNegative(maximum)) {
            throw new BusinessException("Price filters cannot be negative", "INVALID_PRICE_FILTER");
        }
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new BusinessException(
                    "minPrice cannot be greater than maxPrice",
                    "INVALID_PRICE_RANGE",
                    Map.of("minPrice", minimum, "maxPrice", maximum));
        }
    }

    private void validateSearchWindow(Instant serviceFrom, Instant serviceTo) {
        if (serviceFrom != null && serviceTo != null && !serviceTo.isAfter(serviceFrom)) {
            throw new BusinessException(
                    "serviceTo must be after serviceFrom",
                    "INVALID_SERVICE_WINDOW_FILTER",
                    Map.of("serviceFrom", serviceFrom, "serviceTo", serviceTo));
        }
    }

    private void validateClearInstructions(TicketUpdateRequest request) {
        if (Boolean.TRUE.equals(request.getClearServiceWindow())
                && (request.getServiceWindowStart() != null || request.getServiceWindowEnd() != null)) {
            throw new BusinessException(
                    "clearServiceWindow cannot be combined with service window values",
                    "CONFLICTING_TICKET_UPDATE",
                    "clearServiceWindow",
                    true);
        }
        if (Boolean.TRUE.equals(request.getClearExpiresAt()) && request.getExpiresAt() != null) {
            throw new BusinessException(
                    "clearExpiresAt cannot be combined with expiresAt",
                    "CONFLICTING_TICKET_UPDATE",
                    "clearExpiresAt",
                    true);
        }
    }

    private void validateServiceWindowOrder(Instant start, Instant end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(
                    "Service window end must be after its start",
                    "INVALID_SERVICE_WINDOW",
                    Map.of("serviceWindowStart", start, "serviceWindowEnd", end));
        }
    }

    private void validateServiceWindowCapacity(Instant start, Instant end, Integer estimatedDurationMinutes) {
        if (start == null || end == null || estimatedDurationMinutes == null) {
            return;
        }

        Duration window = Duration.between(start, end);
        Duration required = Duration.ofMinutes(estimatedDurationMinutes);
        if (window.compareTo(required) < 0) {
            throw new BusinessException(
                    "Service window is shorter than the estimated duration",
                    "SERVICE_WINDOW_TOO_SHORT",
                    Map.of(
                            "requiredMinutes", estimatedDurationMinutes,
                            "windowMinutes", window.toMinutes()));
        }
    }

    private void validateFutureServiceWindow(Ticket ticket) {
        Instant now = Instant.now();
        validateFutureServiceBound(ticket.getServiceWindowStart(), "serviceWindowStart", now);
        validateFutureServiceBound(ticket.getServiceWindowEnd(), "serviceWindowEnd", now);
    }

    private void validateFutureServiceWindowUpdate(TicketUpdateRequest request) {
        Instant now = Instant.now();
        validateFutureServiceBound(request.getServiceWindowStart(), "serviceWindowStart", now);
        validateFutureServiceBound(request.getServiceWindowEnd(), "serviceWindowEnd", now);
    }

    private void validateFutureServiceBound(Instant value, String field, Instant now) {
        if (value != null && !value.isAfter(now)) {
            throw new BusinessException(
                    "Service window values must be in the future",
                    "INVALID_SERVICE_WINDOW",
                    field,
                    value);
        }
    }

    private Pageable pageable(Integer page, Integer size, String requestedSort) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        String rawSort = requestedSort == null || requestedSort.isBlank() ? DEFAULT_SORT : requestedSort.trim();
        String[] parts = rawSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw invalidSort(rawSort);
        }

        String property = ALLOWED_SORTS.get(parts[0]);
        if (property == null) {
            throw invalidSort(rawSort);
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 1 || parts[1].isBlank()
                    ? Sort.Direction.DESC
                    : Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw invalidSort(rawSort);
        }

        Sort resolvedSort = Sort.by(new Sort.Order(direction, property));
        if (!"id".equals(property)) {
            resolvedSort = resolvedSort.and(Sort.by(new Sort.Order(direction, "id")));
        }
        return PageRequest.of(safePage, safeSize, resolvedSort);
    }

    private Category requireActiveCategory(Long categoryId) {
        if (categoryId == null) {
            throw requiredField("categoryId");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        requireCategoryStillActive(category);
        return category;
    }

    private void requireCategoryStillActive(Category category) {
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new BusinessException(
                    "The selected category is inactive",
                    "CATEGORY_INACTIVE",
                    "categoryId",
                    category.getId());
        }
    }

    private Ticket findDetailed(Long ticketId) {
        return ticketRepository.findDetailedById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private Ticket findDetailedForUpdate(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private TicketDetailResponse toDetailResponse(Ticket ticket) {
        return TicketDetailResponse.from(
                ticket,
                ticketImageService.listResponses(ticket.getId()),
                fileUrlService);
    }

    private void assertNotEffectivelyExpired(Ticket ticket, Instant now) {
        if (ticket.isEffectivelyExpiredAt(now)) {
            throw new ConflictException(
                    "This ticket is no longer accepting applications",
                    "TICKET_EXPIRED");
        }
    }

    private void assertAuthor(Ticket ticket) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (!Objects.equals(ticket.getAuthor().getId(), currentUserId)) {
            throw new ForbiddenException(
                    "Only the ticket author can perform this action",
                    "TICKET_AUTHOR_REQUIRED");
        }
    }

    private void assertEditable(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.DRAFT
                && ticket.getStatus() != TicketStatus.OPEN
                && ticket.getStatus() != TicketStatus.MATCHED) {
            throw new ConflictException(
                    "This ticket can no longer be edited",
                    "TICKET_NOT_EDITABLE");
        }
    }

    private String normalizeRequiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw requiredField(field);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = value == null || value.isBlank()
                ? "USD"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BusinessException(
                    "Currency must be a three-letter ISO code",
                    "INVALID_CURRENCY",
                    "currency",
                    normalized);
        }
        return normalized;
    }

    private boolean differentAmount(BigDecimal current, BigDecimal requested) {
        return current == null || current.compareTo(requested) != 0;
    }

    private boolean isNegative(BigDecimal value) {
        return value.signum() < 0;
    }

    private BusinessException requiredField(String field) {
        return new BusinessException(
                field + " is required",
                "REQUIRED_FIELD",
                field,
                null);
    }

    private BusinessException invalidPricing(String message) {
        return new BusinessException(message, "INVALID_TICKET_PRICING");
    }

    private BusinessException invalidSort(String sort) {
        return new BusinessException(
                "Unsupported ticket sort",
                "INVALID_SORT",
                "sort",
                Map.of("requested", sort, "allowed", ALLOWED_SORTS.keySet()));
    }
}
