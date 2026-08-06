package com.relix.marketplace.application.service;

import com.relix.marketplace.application.dto.ApplicationCreateRequest;
import com.relix.marketplace.application.dto.ApplicationResponse;
import com.relix.marketplace.application.entity.Application;
import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import com.relix.marketplace.worker.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final TicketRepository ticketRepository;
    private final EngagementRepository engagementRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final CurrentUserService currentUserService;
    private final EligibilityService eligibilityService;
    private final AuditService auditService;

    @Transactional
    public ApplicationResponse apply(Long ticketId, ApplicationCreateRequest request) {
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        User applicant = currentUserService.getCurrentUser();

        assertTicketOpen(ticket);
        if (ticket.getAuthor().getId().equals(applicant.getId())) {
            throw new BusinessException(
                    "You cannot apply to your own ticket",
                    "SELF_APPLICATION_NOT_ALLOWED");
        }

        if (ticket.getKind() == TicketKind.REQUEST) {
            currentUserService.requireWorkerProfile();
            eligibilityService.assertCanServe(applicant.getId(), ticket.getCategory().getId());
        }

        validateProposedAmount(ticket, request.getProposedAmount());
        validateSchedule(ticket, request.getProposedStart(), request.getProposedEnd());

        Application application = Application.builder()
                .ticket(ticket)
                .applicant(applicant)
                .proposedAmount(request.getProposedAmount())
                .message(request.getMessage())
                .proposedStart(request.getProposedStart())
                .proposedEnd(request.getProposedEnd())
                .status(ApplicationStatus.PENDING)
                .build();

        application = applicationRepository.saveAndFlush(application);
        if (ticketRepository.incrementApplicationCount(ticketId) != 1) {
            throw new ResourceNotFoundException("Ticket", ticketId);
        }

        auditService.log(
                "APPLICATION",
                application.getId(),
                "APPLICATION_CREATED",
                applicantActorType(ticket),
                applicant.getId(),
                Map.of(
                        "ticketId", ticketId,
                        "proposedAmount", request.getProposedAmount()));

        return toResponse(application, null);
    }

    public PageResponse<ApplicationResponse> getTicketApplications(
            Long ticketId,
            ApplicationStatus status,
            Pageable pageable) {
        Ticket ticket = ticketRepository.findDetailedById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        User currentUser = currentUserService.getCurrentUser();
        assertTicketAuthor(ticket, currentUser.getId());

        Page<Application> applications = status == null
                ? applicationRepository.findByTicket_Id(ticketId, pageable)
                : applicationRepository.findByTicket_IdAndStatus(ticketId, status, pageable);
        return PageResponse.of(applications, application -> toResponse(application, null));
    }

    public PageResponse<ApplicationResponse> getMyApplications(
            ApplicationStatus status,
            Pageable pageable) {
        Long userId = currentUserService.getCurrentUser().getId();
        Page<Application> applications = status == null
                ? applicationRepository.findByApplicant_Id(userId, pageable)
                : applicationRepository.findByApplicant_IdAndStatus(userId, status, pageable);
        return PageResponse.of(applications, application -> toResponse(application, null));
    }

    @Transactional
    public ApplicationResponse accept(Long applicationId) {
        LockedApplication locked = lockApplicationAndTicket(applicationId);
        Ticket ticket = locked.ticket();
        Application application = locked.application();
        User actor = currentUserService.getCurrentUser();

        assertTicketAuthor(ticket, actor.getId());
        assertTicketOpen(ticket);
        assertPending(application);
        validateSchedule(ticket, application.getProposedStart(), application.getProposedEnd());

        User client;
        WorkerProfile worker;
        if (ticket.getKind() == TicketKind.REQUEST) {
            eligibilityService.assertCanServe(
                    application.getApplicant().getId(),
                    ticket.getCategory().getId());
            worker = workerProfileRepository.findByUser_Id(application.getApplicant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Worker profile is missing for the selected applicant",
                            "WORKER_PROFILE_NOT_FOUND",
                            Map.of("userId", application.getApplicant().getId())));
            client = ticket.getAuthor();
        } else {
            worker = ticket.getWorker();
            if (worker == null) {
                throw new ConflictException(
                        "Offer ticket has no worker profile",
                        "TICKET_WORKER_MISSING");
            }
            client = application.getApplicant();
        }

        application.setStatus(ApplicationStatus.ACCEPTED);
        applicationRepository.rejectOtherPendingApplications(ticket.getId(), application.getId());
        ticket.markMatched();

        Engagement engagement = Engagement.builder()
                .client(client)
                .worker(worker)
                .ticket(ticket)
                .application(application)
                .status(EngagementStatus.ACCEPTED)
                .amount(application.getProposedAmount())
                .notes(application.getMessage())
                .acceptedAt(Instant.now())
                .scheduledStart(application.getProposedStart())
                .scheduledEnd(application.getProposedEnd())
                .build();
        engagement = engagementRepository.save(engagement);

        auditService.log(
                "APPLICATION",
                application.getId(),
                "APPLICATION_ACCEPTED",
                authorActorType(ticket),
                actor.getId(),
                Map.of(
                        "ticketId", ticket.getId(),
                        "engagementId", engagement.getId()));

        return toResponse(application, engagement.getId());
    }

    @Transactional
    public ApplicationResponse reject(Long applicationId) {
        LockedApplication locked = lockApplicationAndTicket(applicationId);
        Ticket ticket = locked.ticket();
        Application application = locked.application();
        User actor = currentUserService.getCurrentUser();

        assertTicketAuthor(ticket, actor.getId());
        assertPending(application);
        application.setStatus(ApplicationStatus.REJECTED);

        auditService.log(
                "APPLICATION",
                application.getId(),
                "APPLICATION_REJECTED",
                authorActorType(ticket),
                actor.getId(),
                Map.of("ticketId", ticket.getId()));
        return toResponse(application, null);
    }

    @Transactional
    public ApplicationResponse withdraw(Long applicationId) {
        LockedApplication locked = lockApplicationAndTicket(applicationId);
        Ticket ticket = locked.ticket();
        Application application = locked.application();
        User actor = currentUserService.getCurrentUser();

        if (!application.getApplicant().getId().equals(actor.getId())) {
            throw new ForbiddenException(
                    "Only the applicant can withdraw this application",
                    "APPLICATION_APPLICANT_REQUIRED");
        }
        assertPending(application);
        application.setStatus(ApplicationStatus.WITHDRAWN);

        auditService.log(
                "APPLICATION",
                application.getId(),
                "APPLICATION_WITHDRAWN",
                applicantActorType(ticket),
                actor.getId(),
                Map.of("ticketId", ticket.getId()));
        return toResponse(application, null);
    }

    private LockedApplication lockApplicationAndTicket(Long applicationId) {
        Long ticketId = applicationRepository.findTicketIdById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        return new LockedApplication(ticket, application);
    }

    private void assertTicketAuthor(Ticket ticket, Long userId) {
        if (!ticket.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Only the ticket author can manage its applications",
                    "TICKET_AUTHOR_REQUIRED");
        }
    }

    private void assertTicketOpen(Ticket ticket) {
        if (ticket.isEffectivelyExpiredAt(Instant.now())) {
            throw new ConflictException(
                    "This ticket is no longer accepting applications",
                    "TICKET_EXPIRED");
        }
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException(
                    "Applications can only be managed while the ticket is open",
                    "TICKET_NOT_OPEN");
        }
    }

    private void assertPending(Application application) {
        if (!application.isPending()) {
            throw new ConflictException(
                    "Only a pending application can be changed",
                    "APPLICATION_NOT_PENDING");
        }
    }

    private void validateProposedAmount(Ticket ticket, BigDecimal proposedAmount) {
        if (proposedAmount == null || proposedAmount.signum() < 0) {
            throw new BusinessException(
                    "Proposed amount must be non-negative",
                    "INVALID_PROPOSED_AMOUNT",
                    "proposedAmount",
                    null);
        }

        PricingMode mode = ticket.getPricingMode();
        if (mode == null) {
            throw new ConflictException(
                    "Ticket has no pricing mode",
                    "INVALID_TICKET_PRICING");
        }
        if (mode == PricingMode.FIXED) {
            if (ticket.getPrice() == null) {
                throw new ConflictException(
                        "Fixed-price ticket has no price",
                        "INVALID_TICKET_PRICING");
            }
            if (proposedAmount.compareTo(ticket.getPrice()) != 0) {
                throw new BusinessException(
                        "Proposed amount must equal the fixed ticket price",
                        "PROPOSED_AMOUNT_MISMATCH",
                        Map.of("required", ticket.getPrice()));
            }
        } else if (mode == PricingMode.BUDGET_RANGE) {
            if (ticket.getBudgetMin() == null || ticket.getBudgetMax() == null) {
                throw new ConflictException(
                        "Budget-range ticket has an incomplete budget",
                        "INVALID_TICKET_PRICING");
            }
            if (proposedAmount.compareTo(ticket.getBudgetMin()) < 0
                    || proposedAmount.compareTo(ticket.getBudgetMax()) > 0) {
                throw new BusinessException(
                        "Proposed amount must be within the ticket budget",
                        "PROPOSED_AMOUNT_OUT_OF_RANGE",
                        Map.of(
                                "minimum", ticket.getBudgetMin(),
                                "maximum", ticket.getBudgetMax()));
            }
        }
    }

    private void validateSchedule(Ticket ticket, Instant proposedStart, Instant proposedEnd) {
        if ((ticket.getServiceWindowStart() != null || ticket.getServiceWindowEnd() != null)
                && (proposedStart == null || proposedEnd == null)) {
            throw new BusinessException(
                    "A complete proposed start and end are required for a ticket with a service window",
                    "APPLICATION_SCHEDULE_REQUIRED",
                    Map.of("required", new String[]{"proposedStart", "proposedEnd"}));
        }

        Instant now = Instant.now();
        if (proposedStart != null && !proposedStart.isAfter(now)) {
            throw new BusinessException(
                    "Proposed schedule must be in the future",
                    "APPLICATION_SCHEDULE_IN_PAST",
                    "proposedStart",
                    proposedStart);
        }
        if (proposedEnd != null && !proposedEnd.isAfter(now)) {
            throw new BusinessException(
                    "Proposed schedule must be in the future",
                    "APPLICATION_SCHEDULE_IN_PAST",
                    "proposedEnd",
                    proposedEnd);
        }
        if (proposedStart != null && proposedEnd != null && !proposedEnd.isAfter(proposedStart)) {
            throw new BusinessException(
                    "Proposed end must be after proposed start",
                    "INVALID_APPLICATION_SCHEDULE");
        }
        if (proposedStart != null && proposedEnd != null
                && ticket.getEstimatedDurationMinutes() != null) {
            Duration proposedDuration = Duration.between(proposedStart, proposedEnd);
            Duration minimumDuration = Duration.ofMinutes(ticket.getEstimatedDurationMinutes());
            if (proposedDuration.compareTo(minimumDuration) < 0) {
                throw new BusinessException(
                        "Proposed schedule is shorter than the ticket's estimated duration",
                        "APPLICATION_DURATION_TOO_SHORT",
                        Map.of(
                                "requiredMinutes", ticket.getEstimatedDurationMinutes(),
                                "proposedMinutes", proposedDuration.toMinutes()));
            }
        }
        if (ticket.getServiceWindowStart() != null
                && proposedStart.isBefore(ticket.getServiceWindowStart())) {
            throw new BusinessException(
                    "Proposed schedule starts before the ticket service window",
                    "APPLICATION_SCHEDULE_OUTSIDE_WINDOW",
                    Map.of("earliestStart", ticket.getServiceWindowStart()));
        }
        if (ticket.getServiceWindowEnd() != null
                && proposedEnd.isAfter(ticket.getServiceWindowEnd())) {
            throw new BusinessException(
                    "Proposed schedule ends after the ticket service window",
                    "APPLICATION_SCHEDULE_OUTSIDE_WINDOW",
                    Map.of("latestEnd", ticket.getServiceWindowEnd()));
        }
    }

    private String applicantActorType(Ticket ticket) {
        return ticket.getKind() == TicketKind.REQUEST ? "WORKER" : "CLIENT";
    }

    private String authorActorType(Ticket ticket) {
        return ticket.getKind() == TicketKind.REQUEST ? "CLIENT" : "WORKER";
    }

    private ApplicationResponse toResponse(Application application, Long engagementId) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .ticketId(application.getTicket().getId())
                .ticketKind(application.getTicket().getKind())
                .ticketTitle(application.getTicket().getTitle())
                .applicantId(application.getApplicant().getId())
                .applicantName(application.getApplicant().getName())
                .applicantAvatarUrl(application.getApplicant().getAvatarUrl())
                .proposedAmount(application.getProposedAmount())
                .message(application.getMessage())
                .proposedStart(application.getProposedStart())
                .proposedEnd(application.getProposedEnd())
                .status(application.getStatus())
                .engagementId(engagementId)
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    private record LockedApplication(Ticket ticket, Application application) {
    }
}
