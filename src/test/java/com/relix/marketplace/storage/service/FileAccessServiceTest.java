package com.relix.marketplace.storage.service;

import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.common.exception.UnauthorizedException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.Credential;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.CredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAccessServiceTest {

    private static final String KEY = "test-image.jpg";

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EngagementRepository engagementRepository;
    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private FileAccessService service;

    @Test
    void publicAttachedFileAllowsAnonymousRead() {
        StoredFile file = file(FileOwnerType.TICKET_IMAGE, 10L, FileVisibility.PUBLIC);
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));

        assertThat(service.requireReadable(KEY, null)).isSameAs(file);
        verifyNoInteractions(ticketRepository, engagementRepository, credentialRepository);
    }

    @Test
    void unattachedReservationIsHiddenAsNotFoundEvenWhenMarkedPublic() {
        StoredFile file = file(FileOwnerType.TICKET_IMAGE, null, FileVisibility.PUBLIC);
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.requireReadable(KEY, authenticated(11L)))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void privateFileRequiresAuthentication() {
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(
                file(FileOwnerType.ENGAGEMENT_DELIVERABLE, 20L, FileVisibility.PRIVATE)));

        assertThatThrownBy(() -> service.requireReadable(KEY, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("UNAUTHORIZED"));
    }

    @Test
    void ticketAuthorCanReadPrivateDraftImageButAnotherUserCannot() {
        StoredFile file = file(FileOwnerType.TICKET_IMAGE, 10L, FileVisibility.PRIVATE);
        User author = user(11L);
        Ticket ticket = Ticket.builder().author(author).build();
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThat(service.requireReadable(KEY, authenticated(11L))).isSameAs(file);
        assertThatThrownBy(() -> service.requireReadable(KEY, authenticated(12L)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_ACCESS_DENIED"));
    }

    @Test
    void bothEngagementParticipantsCanReadDeliverable() {
        StoredFile file = file(FileOwnerType.ENGAGEMENT_DELIVERABLE, 20L, FileVisibility.PRIVATE);
        Engagement engagement = Engagement.builder()
                .client(user(11L))
                .worker(worker(12L))
                .build();
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));
        when(engagementRepository.findDetailedById(20L)).thenReturn(Optional.of(engagement));

        assertThat(service.requireReadable(KEY, authenticated(11L))).isSameAs(file);
        assertThat(service.requireReadable(KEY, authenticated(12L))).isSameAs(file);
        assertThatThrownBy(() -> service.requireReadable(KEY, authenticated(13L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void credentialOwnerAndAdminCanReadCredentialDocument() {
        StoredFile file = file(FileOwnerType.CREDENTIAL_DOCUMENT, 30L, FileVisibility.PRIVATE);
        Credential credential = Credential.builder().worker(worker(12L)).build();
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));
        when(credentialRepository.findById(30L)).thenReturn(Optional.of(credential));

        assertThat(service.requireReadable(KEY, authenticated(12L))).isSameAs(file);
        assertThat(service.requireReadable(KEY, authenticated(99L, "ROLE_ADMIN"))).isSameAs(file);
        assertThatThrownBy(() -> service.requireReadable(KEY, authenticated(13L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void privateAvatarAllowsOnlyItsUserOrAdmin() {
        StoredFile file = file(FileOwnerType.USER_AVATAR, 12L, FileVisibility.PRIVATE);
        when(storedFileRepository.findDetailedByStorageKey(KEY)).thenReturn(Optional.of(file));

        assertThat(service.requireReadable(KEY, authenticated(12L))).isSameAs(file);
        assertThat(service.requireReadable(KEY, authenticated(99L, "ROLE_ADMIN"))).isSameAs(file);
        assertThatThrownBy(() -> service.requireReadable(KEY, authenticated(13L)))
                .isInstanceOf(ForbiddenException.class);
    }

    private StoredFile file(FileOwnerType ownerType, Long ownerId, FileVisibility visibility) {
        return StoredFile.builder()
                .storageKey(KEY)
                .variant(FileVariant.LARGE)
                .visibility(visibility)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .contentType("image/jpeg")
                .byteSize(3L)
                .width(1)
                .height(1)
                .build();
    }

    private User user(Long id) {
        User user = User.builder().email(id + "@example.com").name("User " + id).build();
        user.setId(id);
        return user;
    }

    private WorkerProfile worker(Long userId) {
        WorkerProfile worker = WorkerProfile.builder().user(user(userId)).displayName("Worker").build();
        worker.setId(userId + 100);
        return worker;
    }

    private Authentication authenticated(Long userId, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(userId, null, granted);
    }
}
