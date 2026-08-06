package com.relix.marketplace.application.repository;

import com.relix.marketplace.application.entity.Application;
import com.relix.marketplace.application.entity.ApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query("select application.ticket.id from Application application where application.id = :id")
    Optional<Long> findTicketIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "ticket", "ticket.author", "ticket.category", "ticket.worker", "ticket.worker.user", "applicant"
    })
    @Query("select application from Application application where application.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"ticket", "applicant"})
    Page<Application> findByTicket_Id(Long ticketId, Pageable pageable);

    @EntityGraph(attributePaths = {"ticket", "applicant"})
    Page<Application> findByTicket_IdAndStatus(
            Long ticketId,
            ApplicationStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"ticket", "applicant"})
    Page<Application> findByApplicant_Id(Long applicantId, Pageable pageable);

    @EntityGraph(attributePaths = {"ticket", "applicant"})
    Page<Application> findByApplicant_IdAndStatus(
            Long applicantId,
            ApplicationStatus status,
            Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Application application
               set application.status = com.relix.marketplace.application.entity.ApplicationStatus.REJECTED
             where application.ticket.id = :ticketId
               and application.id <> :acceptedId
               and application.status = com.relix.marketplace.application.entity.ApplicationStatus.PENDING
            """)
    int rejectOtherPendingApplications(
            @Param("ticketId") Long ticketId,
            @Param("acceptedId") Long acceptedId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Application application
               set application.status = com.relix.marketplace.application.entity.ApplicationStatus.REJECTED
             where application.ticket.id = :ticketId
               and application.status = com.relix.marketplace.application.entity.ApplicationStatus.PENDING
            """)
    int rejectPendingApplications(@Param("ticketId") Long ticketId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application application
               set application.status = com.relix.marketplace.application.entity.ApplicationStatus.EXPIRED,
                   application.updatedAt = :now
             where application.status = com.relix.marketplace.application.entity.ApplicationStatus.PENDING
               and (
                   application.ticket.status = com.relix.marketplace.ticket.entity.TicketStatus.EXPIRED
                   or (
                       application.ticket.status = com.relix.marketplace.ticket.entity.TicketStatus.OPEN
                       and (
                           (application.ticket.expiresAt is not null
                               and application.ticket.expiresAt <= :now)
                           or (application.ticket.serviceWindowEnd is not null
                               and application.ticket.serviceWindowEnd <= :now)
                       )
                   )
               )
            """)
    int expirePendingForExpiredTickets(@Param("now") Instant now);
}
