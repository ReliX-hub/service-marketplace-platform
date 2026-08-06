package com.relix.marketplace.ticket.scheduler;

import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketExpirationSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ApplicationRepository applicationRepository;

    @Test
    void sweepExpiresOpenTicketsUsingCurrentInstant() {
        TicketExpirationScheduler scheduler = new TicketExpirationScheduler(
                ticketRepository,
                applicationRepository);
        Instant before = Instant.now();
        when(applicationRepository.expirePendingForExpiredTickets(
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(7);
        when(ticketRepository.expireOpenTickets(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(3);

        int count = scheduler.expireTickets();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> applicationNow = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> ticketNow = ArgumentCaptor.forClass(Instant.class);
        var lockOrder = inOrder(ticketRepository, applicationRepository);
        lockOrder.verify(ticketRepository).expireOpenTickets(ticketNow.capture());
        lockOrder.verify(applicationRepository).expirePendingForExpiredTickets(applicationNow.capture());
        assertThat(applicationNow.getAllValues()).allSatisfy(value ->
                assertThat(value).isBetween(before, after));
        assertThat(applicationNow.getAllValues()).containsOnly(ticketNow.getValue());
        assertThat(count).isEqualTo(3);
    }
}
