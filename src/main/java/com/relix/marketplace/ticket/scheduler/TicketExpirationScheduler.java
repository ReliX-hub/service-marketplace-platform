package com.relix.marketplace.ticket.scheduler;

import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketExpirationScheduler {

    private final TicketRepository ticketRepository;
    private final ApplicationRepository applicationRepository;

    @Scheduled(
            cron = "${marketplace.ticket.expiration-cron:0 * * * * *}",
            zone = "UTC")
    @Transactional
    public int expireTickets() {
        Instant now = Instant.now();
        int expired = ticketRepository.expireOpenTickets(now);
        // Marketplace mutations lock ticket before application. Keep the sweep
        // in that same order to avoid a scheduler/acceptance deadlock. An apply
        // transaction also locks the ticket first, so once this update commits,
        // no new pending application can pass the OPEN/unexpired guard.
        int expiredApplications = applicationRepository.expirePendingForExpiredTickets(now);
        if (expired > 0 || expiredApplications > 0) {
            log.info(
                    "Marked {} marketplace ticket(s) and {} pending application(s) as expired",
                    expired,
                    expiredApplications);
        }
        return expired;
    }
}
