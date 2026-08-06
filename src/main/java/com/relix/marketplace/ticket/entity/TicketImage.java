package com.relix.marketplace.ticket.entity;

import com.relix.marketplace.common.entity.BaseEntity;
import com.relix.marketplace.storage.entity.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thumb_id", nullable = false)
    private StoredFile thumb;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "large_id", nullable = false)
    private StoredFile large;

    @Column(length = 160)
    private String caption;

    @Column(nullable = false)
    private Short position;
}
