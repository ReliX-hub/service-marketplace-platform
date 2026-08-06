package com.relix.marketplace.worker.service;

import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.ticket.repository.TicketImageRepository;
import com.relix.marketplace.worker.dto.WorkerProfileUpsertRequest;
import com.relix.marketplace.worker.dto.WorkerProfileResponse;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import com.relix.marketplace.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerProfileServiceTest {

    @Mock private WorkerProfileRepository workerRepository;
    @Mock private TicketImageRepository ticketImageRepository;
    @Mock private FileUrlService fileUrlService;
    @Mock private ImageProperties imageProperties;

    @InjectMocks private WorkerProfileService workerService;

    private User createUser(Long id) {
        User u = User.builder().email("p@test.com").name("P").passwordHash("h")
                .role(User.UserRole.USER).status(User.UserStatus.ACTIVE).build();
        u.setId(id);
        return u;
    }

    private WorkerProfile createWorker(Long id, User user) {
        WorkerProfile p = WorkerProfile.builder().user(user).displayName("Biz")
                .description("Desc").address("123 St").build();
        p.setId(id);
        return p;
    }

    @Test
    @DisplayName("getWorkerById returns response for existing worker")
    void getById_success() {
        User user = createUser(1L);
        WorkerProfile worker = createWorker(10L, user);

        when(workerRepository.findById(10L)).thenReturn(Optional.of(worker));
        when(ticketImageRepository.findRecentWorkCandidates(eq(10L), any(Pageable.class)))
                .thenReturn(List.of());

        WorkerProfileResponse resp = workerService.getWorkerById(10L);

        assertEquals(10L, resp.getId());
        assertEquals("Biz", resp.getDisplayName());
        assertEquals(1L, resp.getUserId());
        assertTrue(resp.getRecentWork().isEmpty());
    }

    @Test
    @DisplayName("getWorkerById caps recent work per ticket and overall")
    void getById_recentWork_isBalancedAcrossTickets() {
        User user = createUser(1L);
        WorkerProfile worker = createWorker(10L, user);
        TicketImageRepository.RecentWorkProjection first = recentWork(101L, "First", "t1", "l1");
        TicketImageRepository.RecentWorkProjection firstExtra =
                mock(TicketImageRepository.RecentWorkProjection.class);
        when(firstExtra.getTicketId()).thenReturn(101L);
        TicketImageRepository.RecentWorkProjection second = recentWork(102L, "Second", "t3", "l3");
        TicketImageRepository.RecentWorkProjection third = recentWork(103L, "Third", "t4", "l4");

        when(workerRepository.findById(10L)).thenReturn(Optional.of(worker));
        when(ticketImageRepository.findRecentWorkCandidates(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(first, firstExtra, second, third));
        when(imageProperties.getRecentWorkPerTicket()).thenReturn(1);
        when(imageProperties.getRecentWorkLimit()).thenReturn(3);
        when(fileUrlService.pathForKey(anyString())).thenAnswer(invocation -> "/api/files/" + invocation.getArgument(0));
        when(fileUrlService.toExternalUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkerProfileResponse response = workerService.getWorkerById(10L);

        assertEquals(List.of(101L, 102L, 103L), response.getRecentWork().stream()
                .map(item -> item.ticketId())
                .toList());
        assertEquals("/api/files/t1", response.getRecentWork().get(0).image().thumb());
        verify(ticketImageRepository).findRecentWorkCandidates(eq(10L), eq(PageRequest.of(0, 24)));
    }

    @Test
    @DisplayName("getWorkerById throws when not found")
    void getById_notFound_throws() {
        when(workerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workerService.getWorkerById(999L));
    }

    @Test
    @DisplayName("upsertWorkerProfile creates new profile when none exists")
    void upsert_createsNew() {
        User user = createUser(1L);

        when(workerRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(workerRepository.save(any(WorkerProfile.class))).thenAnswer(inv -> {
            WorkerProfile p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        WorkerProfileUpsertRequest request = new WorkerProfileUpsertRequest();
        request.setDisplayName("New Biz");
        request.setDescription("New Desc");
        request.setAddress("456 Ave");

        WorkerProfileResponse resp = workerService.upsertWorkerProfile(user, request);

        assertEquals("New Biz", resp.getDisplayName());
    }

    @Test
    @DisplayName("upsertWorkerProfile updates existing profile")
    void upsert_updatesExisting() {
        User user = createUser(1L);
        WorkerProfile existing = createWorker(10L, user);

        when(workerRepository.findByUser_Id(1L)).thenReturn(Optional.of(existing));
        when(workerRepository.save(any(WorkerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkerProfileUpsertRequest request = new WorkerProfileUpsertRequest();
        request.setDisplayName("Updated Biz");
        request.setDescription("Updated Desc");
        request.setAddress("789 Blvd");

        WorkerProfileResponse resp = workerService.upsertWorkerProfile(user, request);

        assertEquals("Updated Biz", resp.getDisplayName());
        assertEquals("Updated Desc", resp.getDescription());
    }

    @Test
    @DisplayName("getAllWorkers returns the common pagination envelope")
    void getAll_returnsPageResponse() {
        User u1 = createUser(1L);
        User u2 = createUser(2L);
        u2.setEmail("p2@test.com");
        Pageable pageable = PageRequest.of(1, 2);

        when(workerRepository.findAll(pageable)).thenReturn(new PageImpl<>(
                java.util.List.of(createWorker(10L, u1), createWorker(11L, u2)),
                pageable,
                5));

        PageResponse<WorkerProfileResponse> result = workerService.getAllWorkers(pageable);

        assertEquals(2, result.getItems().size());
        assertEquals(1, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertTrue(result.isHasNext());
    }

    private TicketImageRepository.RecentWorkProjection recentWork(
            Long ticketId,
            String title,
            String thumbKey,
            String largeKey) {
        TicketImageRepository.RecentWorkProjection projection =
                mock(TicketImageRepository.RecentWorkProjection.class);
        when(projection.getTicketId()).thenReturn(ticketId);
        when(projection.getTicketTitle()).thenReturn(title);
        when(projection.getThumbStorageKey()).thenReturn(thumbKey);
        when(projection.getLargeStorageKey()).thenReturn(largeKey);
        return projection;
    }
}
