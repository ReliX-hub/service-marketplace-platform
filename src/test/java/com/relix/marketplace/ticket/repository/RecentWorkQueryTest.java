package com.relix.marketplace.ticket.repository;

import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketImage;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RecentWorkQueryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("recent_work_query_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TicketImageRepository ticketImageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User workerUser;
    private WorkerProfile worker;
    private WorkerProfile otherWorker;
    private Category category;

    @BeforeEach
    void setUp() {
        workerUser = persistUser("recent-work@example.com", "Recent Work");
        worker = persistWorker(workerUser, "Recent Work");
        User otherUser = persistUser("other-recent-work@example.com", "Other Worker");
        otherWorker = persistWorker(otherUser, "Other Worker");
        category = entityManager.createQuery(
                        "select category from Category category where category.code = :code",
                        Category.class)
                .setParameter("code", "MOVING")
                .getSingleResult();
        entityManager.flush();
    }

    @Test
    void returnsOnlyPublicOfferStatusesForWorkerInDeterministicOrder() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '26' and success",
                Integer.class)).isEqualTo(1);

        Ticket open = persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.OPEN, "Open offer");
        Ticket matched = persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.MATCHED, "Matched offer");
        Ticket closed = persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.CLOSED, "Closed offer");

        TicketImage openImage = persistImage(open, 0, "recent-open");
        // Persist position 1 first so the projection must use position before image id.
        TicketImage matchedSecond = persistImage(matched, 1, "recent-matched-second");
        TicketImage matchedFirst = persistImage(matched, 0, "recent-matched-first");
        TicketImage closedImage = persistImage(closed, 0, "recent-closed");

        persistImage(persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.DRAFT, "Draft offer"),
                0, "excluded-draft");
        persistImage(persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.CANCELLED, "Cancelled offer"),
                0, "excluded-cancelled");
        persistImage(persistTicket(worker, workerUser, TicketKind.OFFER, TicketStatus.EXPIRED, "Expired offer"),
                0, "excluded-expired");
        persistImage(persistTicket(null, workerUser, TicketKind.REQUEST, TicketStatus.OPEN, "Open request"),
                0, "excluded-request");
        persistImage(persistTicket(otherWorker, otherWorker.getUser(), TicketKind.OFFER, TicketStatus.OPEN, "Other worker offer"),
                0, "excluded-other-worker");

        entityManager.flush();
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-02-01T00:00:00Z");
        updateCreatedAt(open.getId(), older);
        updateCreatedAt(matched.getId(), newer);
        updateCreatedAt(closed.getId(), newer);
        entityManager.clear();

        List<TicketImageRepository.RecentWorkProjection> result =
                ticketImageRepository.findRecentWorkCandidates(worker.getId(), PageRequest.of(0, 24));

        // CLOSED and MATCHED share a timestamp, so ticket id descending breaks the tie.
        assertThat(result)
                .extracting(TicketImageRepository.RecentWorkProjection::getImageId)
                .containsExactly(
                        closedImage.getId(),
                        matchedFirst.getId(),
                        matchedSecond.getId(),
                        openImage.getId());
        assertThat(result)
                .extracting(TicketImageRepository.RecentWorkProjection::getTicketTitle)
                .containsExactly("Closed offer", "Matched offer", "Matched offer", "Open offer");
        assertThat(result)
                .extracting(TicketImageRepository.RecentWorkProjection::getPosition)
                .containsExactly((short) 0, (short) 0, (short) 1, (short) 0);
        assertThat(result).allSatisfy(item -> {
            assertThat(item.getThumbStorageKey()).endsWith("-thumb.jpg");
            assertThat(item.getLargeStorageKey()).endsWith("-large.jpg");
        });
    }

    private User persistUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .passwordHash("test-hash")
                .name(name)
                .build();
        entityManager.persist(user);
        return user;
    }

    private WorkerProfile persistWorker(User user, String name) {
        WorkerProfile profile = WorkerProfile.builder()
                .user(user)
                .displayName(name)
                .build();
        entityManager.persist(profile);
        return profile;
    }

    private Ticket persistTicket(
            WorkerProfile ticketWorker,
            User author,
            TicketKind kind,
            TicketStatus status,
            String title) {
        Ticket ticket = Ticket.builder()
                .kind(kind)
                .author(author)
                .worker(ticketWorker)
                .category(category)
                .title(title)
                .description("Recent-work projection integration test")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("100.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
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
                .visibility(FileVisibility.PUBLIC)
                .ownerType(FileOwnerType.TICKET_IMAGE)
                .ownerId(ticket.getId())
                .uploader(ticket.getAuthor())
                .contentType("image/jpeg")
                .byteSize(100L)
                .width(100)
                .height(100)
                .build();
        entityManager.persist(file);
        return file;
    }

    private void updateCreatedAt(Long ticketId, Instant createdAt) {
        jdbcTemplate.update(
                "update tickets set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                ticketId);
    }
}
