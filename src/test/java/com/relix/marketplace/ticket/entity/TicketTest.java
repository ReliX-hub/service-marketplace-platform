package com.relix.marketplace.ticket.entity;

import com.relix.marketplace.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    @Test
    void markMatchedOnlyAllowsOpenTicket() {
        Ticket ticket = Ticket.builder().status(TicketStatus.OPEN).build();

        ticket.markMatched();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.MATCHED);
    }

    @Test
    void markMatchedRejectsEveryNonOpenState() {
        for (TicketStatus status : TicketStatus.values()) {
            if (status == TicketStatus.OPEN) {
                continue;
            }
            Ticket ticket = Ticket.builder().status(status).build();
            assertThatThrownBy(ticket::markMatched)
                    .as("status %s", status)
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("TICKET_NOT_OPEN");
        }
    }
}
