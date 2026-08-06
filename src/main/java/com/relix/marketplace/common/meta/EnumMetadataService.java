package com.relix.marketplace.common.meta;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnumMetadataService {

    private static final EnumMetadataResponse METADATA = new EnumMetadataResponse(
            List.of(
                    option("OFFER", "Service offer", "A worker advertises a service that clients can request", "blue"),
                    option("REQUEST", "Task request", "A client publishes work that workers can apply for", "violet")),
            List.of(
                    option("DRAFT", "Draft", "Saved by the author but not visible on the public board", "slate"),
                    option("OPEN", "Open", "Published and accepting applications", "emerald"),
                    option("MATCHED", "Matched", "An application was accepted and an engagement was created", "blue"),
                    option("CLOSED", "Closed", "Closed by the author after publication", "slate"),
                    option("CANCELLED", "Cancelled", "Cancelled before matching was completed", "red"),
                    option("EXPIRED", "Expired", "No longer available because its publication window ended", "amber")),
            List.of(
                    option("FIXED", "Fixed price", "The transaction amount is fixed by the ticket", "blue"),
                    option("BUDGET_RANGE", "Budget range", "Applications must propose an amount within the stated range", "violet"),
                    option("OPEN_BID", "Open bid", "Applicants may propose any amount", "amber")),
            List.of(
                    option("ON_SITE", "On-site", "The service is performed at a physical location", "teal"),
                    option("REMOTE", "Remote", "The service is performed remotely", "indigo"),
                    option("HYBRID", "Hybrid", "The service can combine on-site and remote work", "violet")),
            List.of(
                    option("ACCEPTED", "Accepted", "The match is confirmed and awaiting funding", "blue"),
                    option("FUNDED", "Funded", "Payment is confirmed and held for the engagement", "emerald"),
                    option("IN_PROGRESS", "In progress", "The worker has started delivery", "indigo"),
                    option("DELIVERED", "Delivered", "The worker marked the service as delivered for client review", "violet"),
                    option("COMPLETED", "Completed", "The client approved delivery and settlement can proceed", "emerald"),
                    option("DISPUTED", "Disputed", "Delivery is under administrative review", "amber"),
                    option("CANCELLED", "Cancelled", "The engagement ended before completion", "red"),
                    option("REFUNDED", "Refunded", "Funds were returned to the client", "slate")),
            List.of(
                    option("PENDING", "Pending", "Awaiting a decision from the ticket author", "amber"),
                    option("ACCEPTED", "Accepted", "Selected by the ticket author", "emerald"),
                    option("REJECTED", "Rejected", "Declined by the ticket author or superseded by another match", "red"),
                    option("WITHDRAWN", "Withdrawn", "Withdrawn by the applicant before acceptance", "slate"),
                    option("EXPIRED", "Expired", "The application deadline or service window ended before selection", "slate")),
            List.of(
                    option("PENDING", "Pending", "Refund request is awaiting provider processing", "amber"),
                    option("PROCESSING", "Processing", "Provider requires further action before completing the refund", "indigo"),
                    option("COMPLETED", "Completed", "Funds were returned to the client", "emerald"),
                    option("FAILED", "Failed", "The provider could not complete the refund", "red")),
            List.of(
                    option("PENDING", "Pending review", "Submitted and awaiting administrator review", "amber"),
                    option("VERIFIED", "Verified", "Approved and currently valid", "emerald"),
                    option("REJECTED", "Rejected", "Reviewed but not approved", "red"),
                    option("EXPIRED", "Expired", "Previously issued but no longer current", "slate")),
            List.of(
                    option("ELECTRICAL_LICENSE", "Electrical license", "Required for regulated electrical services", "amber"),
                    option("DRIVER_LICENSE", "Driver license", "Required for driving and delivery services", "blue"),
                    option("BACKGROUND_CHECK", "Background check", "Required for trust-sensitive service categories", "violet")),
            List.of(
                    mediaError("IMAGE_EMPTY", "The upload did not contain image bytes"),
                    mediaError("IMAGE_TOO_LARGE", "The uploaded image exceeds the configured byte-size limit"),
                    mediaError("IMAGE_TYPE_UNSUPPORTED", "The uploaded bytes are not a supported JPEG, PNG, or WebP image"),
                    mediaError("IMAGE_DIMENSIONS_REJECTED", "The image dimensions or decoded pixel count exceed the configured safety limit"),
                    mediaError("IMAGE_CORRUPT", "The image header or pixel data could not be decoded safely"),
                    mediaError("IMAGE_LIMIT_EXCEEDED", "The ticket or engagement already has the maximum number of images"),
                    mediaError("IMAGE_CAPTION_TOO_LONG", "The ticket image caption exceeds 160 characters"),
                    mediaError("INVALID_IMAGE_ORDER", "The requested order is not an exact permutation of the ticket's images"),
                    mediaError("TICKET_IMAGE_NOT_FOUND", "The requested image does not belong to the ticket"),
                    mediaError("TICKET_IMAGES_IMMUTABLE", "Ticket images can no longer be changed in the ticket's current state"),
                    mediaError("FILE_NOT_FOUND", "The requested stored file does not exist"),
                    mediaError("FILE_ACCESS_DENIED", "The authenticated user is not allowed to read the private file"),
                    mediaError("DELIVERABLE_REQUIRED", "At least one delivery image is required before delivery when the policy is enabled"),
                    mediaError("DELIVERABLE_IMMUTABLE", "Delivery evidence cannot be deleted after the engagement is delivered"),
                    mediaError("DELIVERABLE_STATE_INVALID", "Delivery evidence can only be changed while the engagement is in progress"),
                    mediaError("DELIVERABLE_NOTE_TOO_LONG", "The delivery evidence note exceeds 300 characters"),
                    mediaError("CREDENTIAL_DOCUMENT_REQUIRED", "A credential document is required before administrator verification"),
                    mediaError("CREDENTIAL_DOCUMENT_ACCESS_DENIED", "Only the credential owner can upload its document"),
                    mediaError("CREDENTIAL_DOCUMENT_IMMUTABLE", "A credential document can only be changed while review is pending")));

    public EnumMetadataResponse getMetadata() {
        return METADATA;
    }

    private static EnumOptionResponse option(
            String value,
            String label,
            String description,
            String colorHint) {
        return new EnumOptionResponse(value, label, description, colorHint);
    }

    private static MediaErrorCodeResponse mediaError(String code, String description) {
        return new MediaErrorCodeResponse(code, description);
    }
}
