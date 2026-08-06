package com.relix.marketplace.ticket.repository;

import com.relix.marketplace.application.entity.Application;
import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.ticket.dto.TicketSearchCriteria;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketImage;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.scheduler.TicketExpirationScheduler;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TicketRepositoryDataJpaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ticket_repository_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;
    private WorkerProfile worker;
    private Category category;

    @BeforeEach
    void setUp() {
        author = User.builder()
                .email("ticket-repository@example.com")
                .passwordHash("test-hash")
                .name("Repository Test User")
                .build();
        entityManager.persist(author);

        worker = WorkerProfile.builder()
                .user(author)
                .displayName("Repository Test Worker")
                .build();
        entityManager.persist(worker);

        category = entityManager.createQuery(
                        "select category from Category category where category.code = :code",
                        Category.class)
                .setParameter("code", "MOVING")
                .getSingleResult();
        entityManager.flush();
    }

    @Test
    void publicBoardUsesIntervalOverlapAndExcludesDraftsAndOpenBidsWhenPriced() {
        Ticket fixedMatch = persistTicket(
                TicketKind.OFFER, TicketStatus.OPEN, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        Ticket rangeMatch = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.BUDGET_RANGE,
                null, new BigDecimal("95.00"), new BigDecimal("150.00"));
        persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.BUDGET_RANGE,
                null, new BigDecimal("120.00"), new BigDecimal("150.00"));
        persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        persistTicket(
                TicketKind.REQUEST, TicketStatus.DRAFT, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        entityManager.flush();
        entityManager.clear();

        TicketSearchCriteria criteria = TicketSearchCriteria.builder()
                .minPrice(new BigDecimal("80.00"))
                .maxPrice(new BigDecimal("100.00"))
                .build();
        Page<Ticket> result = ticketRepository.findAll(
                TicketSpecifications.publicBoard(criteria),
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(Ticket::getId)
                .containsExactlyInAnyOrder(fixedMatch.getId(), rangeMatch.getId());
    }

    @Test
    void listEntityGraphLoadsEverySummaryAssociation() {
        persistTicket(
                TicketKind.OFFER, TicketStatus.OPEN, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        entityManager.flush();
        entityManager.clear();

        Ticket loaded = ticketRepository.findAll(
                        TicketSpecifications.publicBoard(new TicketSearchCriteria()),
                        PageRequest.of(0, 20))
                .getContent()
                .get(0);
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistence.isLoaded(loaded, "author")).isTrue();
        assertThat(persistence.isLoaded(loaded, "category")).isTrue();
        assertThat(persistence.isLoaded(loaded, "worker")).isTrue();
        assertThat(persistence.isLoaded(loaded.getWorker(), "user")).isTrue();
    }

    @Test
    void publicBoardMatchesOverlappingServiceWindowsAndExcludesExpiredOpenTickets() {
        Instant now = Instant.now();
        Ticket overlapping = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        overlapping.setServiceWindowStart(now.plus(2, ChronoUnit.DAYS));
        overlapping.setServiceWindowEnd(now.plus(4, ChronoUnit.DAYS));

        Ticket outside = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        outside.setServiceWindowStart(now.plus(6, ChronoUnit.DAYS));
        outside.setServiceWindowEnd(now.plus(8, ChronoUnit.DAYS));

        Ticket openEnded = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);

        Ticket expired = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        expired.setExpiresAt(now.minus(1, ChronoUnit.MINUTES));

        Ticket endedWindow = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        endedWindow.setServiceWindowStart(now.minus(2, ChronoUnit.DAYS));
        endedWindow.setServiceWindowEnd(now.minus(1, ChronoUnit.MINUTES));

        Instant requestedFrom = now.plus(3, ChronoUnit.DAYS);
        Instant requestedTo = now.plus(5, ChronoUnit.DAYS);
        Ticket touchesRequestedStart = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        touchesRequestedStart.setServiceWindowStart(now.plus(1, ChronoUnit.DAYS));
        touchesRequestedStart.setServiceWindowEnd(requestedFrom);

        Ticket touchesRequestedEnd = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        touchesRequestedEnd.setServiceWindowStart(requestedTo);
        touchesRequestedEnd.setServiceWindowEnd(now.plus(7, ChronoUnit.DAYS));

        entityManager.flush();
        entityManager.clear();

        TicketSearchCriteria criteria = TicketSearchCriteria.builder()
                .serviceFrom(requestedFrom)
                .serviceTo(requestedTo)
                .build();
        Page<Ticket> result = ticketRepository.findAll(
                TicketSpecifications.publicBoard(criteria, now),
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(Ticket::getId)
                .contains(overlapping.getId(), openEnded.getId())
                .doesNotContain(
                        outside.getId(),
                        expired.getId(),
                        endedWindow.getId(),
                        touchesRequestedStart.getId(),
                        touchesRequestedEnd.getId());
    }

    @Test
    void expirationSweepOnlyTransitionsExpiredOpenTickets() {
        Instant now = Instant.now();
        Ticket expiredOpen = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        expiredOpen.setExpiresAt(now.minus(1, ChronoUnit.MINUTES));
        Ticket futureOpen = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        futureOpen.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        Ticket endedWindow = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        endedWindow.setServiceWindowStart(now.minus(1, ChronoUnit.DAYS));
        endedWindow.setServiceWindowEnd(now);
        Ticket alreadyMatched = persistTicket(
                TicketKind.REQUEST, TicketStatus.MATCHED, PricingMode.OPEN_BID,
                null, null, null);
        alreadyMatched.setExpiresAt(now.minus(1, ChronoUnit.MINUTES));
        entityManager.flush();

        assertThat(ticketRepository.expireOpenTickets(now)).isEqualTo(2);

        assertThat(ticketRepository.findById(expiredOpen.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.EXPIRED);
        assertThat(ticketRepository.findById(futureOpen.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.OPEN);
        assertThat(ticketRepository.findById(endedWindow.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.EXPIRED);
        assertThat(ticketRepository.findById(alreadyMatched.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.MATCHED);
    }

    @Test
    void schedulerExpiresPendingApplicationsInTheSameSweepAsTheirTicket() {
        Instant now = Instant.now();
        Ticket ended = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.OPEN_BID,
                null, null, null);
        ended.setServiceWindowStart(now.minus(1, ChronoUnit.DAYS));
        ended.setServiceWindowEnd(now.minus(1, ChronoUnit.MINUTES));
        User applicant = User.builder()
                .email("expired-application@example.com")
                .passwordHash("test-hash")
                .name("Expired Applicant")
                .build();
        entityManager.persist(applicant);
        Application application = Application.builder()
                .ticket(ended)
                .applicant(applicant)
                .proposedAmount(new BigDecimal("90.00"))
                .status(ApplicationStatus.PENDING)
                .build();
        entityManager.persist(application);
        entityManager.flush();
        Long ticketId = ended.getId();
        Long applicationId = application.getId();

        TicketExpirationScheduler scheduler = new TicketExpirationScheduler(
                ticketRepository,
                applicationRepository);
        assertThat(scheduler.expireTickets()).isEqualTo(1);

        assertThat(ticketRepository.findById(ticketId).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.EXPIRED);
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.EXPIRED);
    }

    @Test
    void closingCleanupRejectsOnlyPendingApplicationsForTheTicket() {
        Ticket closing = persistTicket(
                TicketKind.REQUEST, TicketStatus.CLOSED, PricingMode.OPEN_BID,
                null, null, null);
        User pendingApplicant = User.builder()
                .email("closing-pending@example.com")
                .passwordHash("test-hash")
                .name("Pending Applicant")
                .build();
        User withdrawnApplicant = User.builder()
                .email("closing-withdrawn@example.com")
                .passwordHash("test-hash")
                .name("Withdrawn Applicant")
                .build();
        entityManager.persist(pendingApplicant);
        entityManager.persist(withdrawnApplicant);

        Application pending = Application.builder()
                .ticket(closing)
                .applicant(pendingApplicant)
                .proposedAmount(new BigDecimal("90.00"))
                .status(ApplicationStatus.PENDING)
                .build();
        Application withdrawn = Application.builder()
                .ticket(closing)
                .applicant(withdrawnApplicant)
                .proposedAmount(new BigDecimal("90.00"))
                .status(ApplicationStatus.WITHDRAWN)
                .build();
        entityManager.persist(pending);
        entityManager.persist(withdrawn);
        entityManager.flush();
        Long pendingId = pending.getId();
        Long withdrawnId = withdrawn.getId();

        assertThat(applicationRepository.rejectPendingApplications(closing.getId())).isEqualTo(1);
        entityManager.clear();

        assertThat(applicationRepository.findById(pendingId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
        assertThat(applicationRepository.findById(withdrawnId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void countersAreUpdatedAtomicallyAndLockLookupReturnsTicket() {
        Ticket ticket = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(ticketRepository.findByIdForUpdate(ticket.getId())).isPresent();
        assertThat(ticketRepository.incrementActivePublicViewCount(ticket.getId(), Instant.now())).isEqualTo(1);
        assertThat(ticketRepository.incrementApplicationCount(ticket.getId())).isEqualTo(1);
        entityManager.clear();

        Ticket updated = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(updated.getViewCount()).isEqualTo(1L);
        assertThat(updated.getApplicationCount()).isEqualTo(1);
    }

    @Test
    void conditionalViewCounterRejectsEffectivelyExpiredOpenTicket() {
        Instant now = Instant.now();
        Ticket ticket = persistTicket(
                TicketKind.REQUEST, TicketStatus.OPEN, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        ticket.setServiceWindowStart(now.minus(1, ChronoUnit.DAYS));
        ticket.setServiceWindowEnd(now);
        entityManager.flush();

        assertThat(ticketRepository.incrementActivePublicViewCount(ticket.getId(), now)).isZero();
    }

    @Test
    void databaseRejectsRequestTicketWithWorkerProfile() {
        Ticket invalid = Ticket.builder()
                .kind(TicketKind.REQUEST)
                .author(author)
                .worker(worker)
                .category(category)
                .title("Invalid request direction")
                .description("A request must be authored without a worker profile reference")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("90.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(TicketStatus.OPEN)
                .build();
        assertThatThrownBy(() -> {
                    entityManager.persist(invalid);
                    entityManager.flush();
                })
                .rootCause()
                .hasMessageContaining("chk_ticket_direction");
    }

    @Test
    void deferredImagePositionConstraintAllowsWritingFinalReorderDirectly() {
        Ticket ticket = persistTicket(
                TicketKind.REQUEST, TicketStatus.DRAFT, PricingMode.FIXED,
                new BigDecimal("90.00"), null, null);
        entityManager.flush();

        TicketImage first = persistImage(ticket, 0, "reorder-first");
        TicketImage second = persistImage(ticket, 1, "reorder-second");
        entityManager.flush();

        entityManager.createNativeQuery("SET CONSTRAINTS uk_ticket_images_position DEFERRED")
                .executeUpdate();
        first.setPosition((short) 1);
        second.setPosition((short) 0);
        entityManager.flush();
        Long ticketId = ticket.getId();
        entityManager.clear();

        List<TicketImage> reordered = entityManager.createQuery(
                        "select image from TicketImage image where image.ticket.id = :ticketId order by image.position",
                        TicketImage.class)
                .setParameter("ticketId", ticketId)
                .getResultList();
        assertThat(reordered)
                .extracting(TicketImage::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(reordered)
                .extracting(TicketImage::getPosition)
                .containsExactly((short) 0, (short) 1);
    }

    private Ticket persistTicket(
            TicketKind kind,
            TicketStatus status,
            PricingMode pricingMode,
            BigDecimal price,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {
        Ticket ticket = Ticket.builder()
                .kind(kind)
                .author(author)
                .worker(kind == TicketKind.OFFER ? worker : null)
                .category(category)
                .title(kind + " " + pricingMode)
                .description("Repository integration test")
                .pricingMode(pricingMode)
                .price(price)
                .budgetMin(budgetMin)
                .budgetMax(budgetMax)
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .city("Chicago")
                .status(status)
                .build();
        entityManager.persist(ticket);
        return ticket;
    }

    private TicketImage persistImage(Ticket ticket, int position, String keyPrefix) {
        StoredFile thumb = persistFile(ticket, keyPrefix + "-thumb.jpg", FileVariant.THUMB);
        StoredFile large = persistFile(ticket, keyPrefix + "-large.jpg", FileVariant.LARGE);
        TicketImage image = TicketImage.builder()
                .ticket(ticket)
                .thumb(thumb)
                .large(large)
                .position((short) position)
                .build();
        entityManager.persist(image);
        return image;
    }

    private StoredFile persistFile(Ticket ticket, String key, FileVariant variant) {
        StoredFile file = StoredFile.builder()
                .storageKey(key)
                .variant(variant)
                .visibility(FileVisibility.PRIVATE)
                .ownerType(FileOwnerType.TICKET_IMAGE)
                .ownerId(ticket.getId())
                .uploader(author)
                .contentType("image/jpeg")
                .byteSize(100L)
                .width(100)
                .height(100)
                .build();
        entityManager.persist(file);
        return file;
    }
}
