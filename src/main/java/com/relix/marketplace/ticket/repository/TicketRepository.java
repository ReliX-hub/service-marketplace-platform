package com.relix.marketplace.ticket.repository;

import com.relix.marketplace.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @Override
    @EntityGraph(attributePaths = {"author", "category", "worker", "worker.user"})
    Page<Ticket> findAll(@Nullable Specification<Ticket> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "worker", "worker.user"})
    @Query("select ticket from Ticket ticket where ticket.id = :id")
    Optional<Ticket> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"author", "category", "worker", "worker.user"})
    @Query("select ticket from Ticket ticket where ticket.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Ticket ticket
               set ticket.viewCount = ticket.viewCount + 1
             where ticket.id = :id
               and (
                   ticket.status in (
                       com.relix.marketplace.ticket.entity.TicketStatus.MATCHED,
                       com.relix.marketplace.ticket.entity.TicketStatus.CLOSED)
                   or (
                       ticket.status = com.relix.marketplace.ticket.entity.TicketStatus.OPEN
                       and (ticket.expiresAt is null or ticket.expiresAt > :now)
                       and (ticket.serviceWindowEnd is null or ticket.serviceWindowEnd > :now)
                   )
               )
            """)
    int incrementActivePublicViewCount(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Ticket ticket set ticket.applicationCount = ticket.applicationCount + 1 where ticket.id = :id")
    int incrementApplicationCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Ticket ticket
               set ticket.status = com.relix.marketplace.ticket.entity.TicketStatus.EXPIRED,
                   ticket.updatedAt = :now
             where ticket.status = com.relix.marketplace.ticket.entity.TicketStatus.OPEN
               and (
                   (ticket.expiresAt is not null and ticket.expiresAt <= :now)
                   or (ticket.serviceWindowEnd is not null and ticket.serviceWindowEnd <= :now)
               )
            """)
    int expireOpenTickets(@Param("now") Instant now);
}
