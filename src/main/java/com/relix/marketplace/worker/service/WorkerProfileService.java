package com.relix.marketplace.worker.service;

import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.dto.ImageVariants;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.ticket.repository.TicketImageRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.dto.RecentWorkItem;
import com.relix.marketplace.worker.dto.WorkerProfileResponse;
import com.relix.marketplace.worker.dto.WorkerProfileUpsertRequest;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerProfileService {

    private static final int RECENT_WORK_CANDIDATE_LIMIT = 24;

    private final WorkerProfileRepository workerProfileRepository;
    private final TicketImageRepository ticketImageRepository;
    private final FileUrlService fileUrlService;
    private final ImageProperties imageProperties;

    public PageResponse<WorkerProfileResponse> getAllWorkers(Pageable pageable) {
        return PageResponse.of(workerProfileRepository.findAll(pageable), this::toResponse);
    }

    public WorkerProfileResponse getWorkerById(Long id) {
        WorkerProfile workerProfile = workerProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerProfile", id));
        WorkerProfileResponse response = toResponse(workerProfile);
        response.setRecentWork(findRecentWork(id));
        return response;
    }

    public PageResponse<WorkerProfileResponse> getVerifiedWorkers(Pageable pageable) {
        return PageResponse.of(
                workerProfileRepository.findByVerifiedTrue(pageable),
                this::toResponse);
    }


    @Transactional
    public WorkerProfileResponse upsertWorkerProfile(User user, WorkerProfileUpsertRequest request) {
        WorkerProfile workerProfile = workerProfileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> WorkerProfile.builder().user(user).verified(false).build());

        workerProfile.setDisplayName(request.getDisplayName());
        workerProfile.setHeadline(request.getHeadline());
        workerProfile.setDescription(request.getDescription());
        workerProfile.setAddress(request.getAddress());
        workerProfile.setServiceRadiusKm(request.getServiceRadiusKm());

        workerProfile = workerProfileRepository.save(workerProfile);
        return toResponse(workerProfile);
    }

    private WorkerProfileResponse toResponse(WorkerProfile workerProfile) {
        return WorkerProfileResponse.builder()
                .id(workerProfile.getId())
                .userId(workerProfile.getUser().getId())
                .displayName(workerProfile.getDisplayName())
                .headline(workerProfile.getHeadline())
                .description(workerProfile.getDescription())
                .address(workerProfile.getAddress())
                .rating(workerProfile.getRating())
                .reviewCount(workerProfile.getReviewCount())
                .verified(workerProfile.getVerified())
                .completedJobs(workerProfile.getCompletedJobs())
                .serviceRadiusKm(workerProfile.getServiceRadiusKm())
                .createdAt(workerProfile.getCreatedAt())
                .build();
    }

    private java.util.List<RecentWorkItem> findRecentWork(Long workerId) {
        java.util.Map<Long, Integer> imagesPerTicket = new java.util.HashMap<>();
        java.util.List<RecentWorkItem> result = new java.util.ArrayList<>();

        for (TicketImageRepository.RecentWorkProjection candidate
                : ticketImageRepository.findRecentWorkCandidates(
                        workerId,
                        PageRequest.of(0, RECENT_WORK_CANDIDATE_LIMIT))) {
            int currentCount = imagesPerTicket.getOrDefault(candidate.getTicketId(), 0);
            if (currentCount >= imageProperties.getRecentWorkPerTicket()) {
                continue;
            }

            result.add(new RecentWorkItem(
                    candidate.getTicketId(),
                    candidate.getTicketTitle(),
                    new ImageVariants(
                            fileUrlService.toExternalUrl(fileUrlService.pathForKey(candidate.getThumbStorageKey())),
                            fileUrlService.toExternalUrl(fileUrlService.pathForKey(candidate.getLargeStorageKey())))));
            imagesPerTicket.put(candidate.getTicketId(), currentCount + 1);

            if (result.size() >= imageProperties.getRecentWorkLimit()) {
                break;
            }
        }

        return java.util.List.copyOf(result);
    }
}
