package com.relix.marketplace.ticket.repository;

import com.relix.marketplace.ticket.entity.TicketImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketImageRepository extends JpaRepository<TicketImage, Long> {

    @EntityGraph(attributePaths = {"thumb", "large"})
    List<TicketImage> findByTicket_IdOrderByPositionAscIdAsc(Long ticketId);

    @EntityGraph(attributePaths = {"ticket", "thumb", "large"})
    Optional<TicketImage> findByIdAndTicket_Id(Long imageId, Long ticketId);

    long countByTicket_Id(Long ticketId);

    @Query("""
            select ticket.id as ticketId,
                   ticket.title as ticketTitle,
                   image.id as imageId,
                   image.position as position,
                   thumb.storageKey as thumbStorageKey,
                   large.storageKey as largeStorageKey
              from TicketImage image
              join image.ticket ticket
              join image.thumb thumb
              join image.large large
             where ticket.worker.id = :workerId
               and ticket.kind = com.relix.marketplace.ticket.entity.TicketKind.OFFER
               and ticket.status in (
                   com.relix.marketplace.ticket.entity.TicketStatus.OPEN,
                   com.relix.marketplace.ticket.entity.TicketStatus.MATCHED,
                   com.relix.marketplace.ticket.entity.TicketStatus.CLOSED)
             order by ticket.createdAt desc,
                      ticket.id desc,
                      image.position asc,
                      image.id asc
            """)
    List<RecentWorkProjection> findRecentWorkCandidates(
            @Param("workerId") Long workerId,
            Pageable pageable);

    interface RecentWorkProjection {
        Long getTicketId();

        String getTicketTitle();

        Long getImageId();

        Short getPosition();

        String getThumbStorageKey();

        String getLargeStorageKey();
    }
}
