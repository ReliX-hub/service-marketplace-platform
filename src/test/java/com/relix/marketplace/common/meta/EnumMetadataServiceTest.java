package com.relix.marketplace.common.meta;

import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.refund.entity.Refund;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.worker.entity.Credential;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumMetadataServiceTest {

    private final EnumMetadataService service = new EnumMetadataService();

    @Test
    void exposesEveryDomainEnumInDeclarationOrder() {
        EnumMetadataResponse metadata = service.getMetadata();

        assertValues(metadata.ticketKind(), TicketKind.values());
        assertValues(metadata.ticketStatus(), TicketStatus.values());
        assertValues(metadata.pricingMode(), PricingMode.values());
        assertValues(metadata.locationMode(), LocationMode.values());
        assertValues(metadata.engagementStatus(), EngagementStatus.values());
        assertValues(metadata.applicationStatus(), ApplicationStatus.values());
        assertValues(metadata.refundStatus(), Refund.RefundStatus.values());
        assertValues(metadata.credentialStatus(), Credential.Status.values());
        assertValues(metadata.credentialType(), Credential.Type.values());
    }

    @Test
    void providesCompleteFrontendPresentationMetadata() {
        EnumMetadataResponse metadata = service.getMetadata();

        List<List<EnumOptionResponse>> groups = List.of(
                metadata.ticketKind(),
                metadata.ticketStatus(),
                metadata.pricingMode(),
                metadata.locationMode(),
                metadata.engagementStatus(),
                metadata.applicationStatus(),
                metadata.refundStatus(),
                metadata.credentialStatus(),
                metadata.credentialType());

        assertThat(groups).allSatisfy(options -> {
            assertThat(options).isNotEmpty();
            assertThat(options)
                    .extracting(EnumOptionResponse::value)
                    .doesNotHaveDuplicates();
            assertThat(options).allSatisfy(option -> {
                assertThat(option.value()).isNotBlank();
                assertThat(option.label()).isNotBlank();
                assertThat(option.description()).isNotBlank();
                assertThat(option.colorHint()).isNotBlank();
            });
        });

        assertThat(metadata.mediaErrorCodes()).isNotEmpty();
        assertThat(metadata.mediaErrorCodes())
                .extracting(MediaErrorCodeResponse::code)
                .containsExactly(
                        "IMAGE_EMPTY",
                        "IMAGE_TOO_LARGE",
                        "IMAGE_TYPE_UNSUPPORTED",
                        "IMAGE_DIMENSIONS_REJECTED",
                        "IMAGE_CORRUPT",
                        "IMAGE_LIMIT_EXCEEDED",
                        "IMAGE_CAPTION_TOO_LONG",
                        "INVALID_IMAGE_ORDER",
                        "TICKET_IMAGE_NOT_FOUND",
                        "TICKET_IMAGES_IMMUTABLE",
                        "FILE_NOT_FOUND",
                        "FILE_ACCESS_DENIED",
                        "DELIVERABLE_REQUIRED",
                        "DELIVERABLE_IMMUTABLE",
                        "DELIVERABLE_STATE_INVALID",
                        "DELIVERABLE_NOTE_TOO_LONG",
                        "CREDENTIAL_DOCUMENT_REQUIRED",
                        "CREDENTIAL_DOCUMENT_ACCESS_DENIED",
                        "CREDENTIAL_DOCUMENT_IMMUTABLE");
        assertThat(metadata.mediaErrorCodes()).allSatisfy(error -> {
            assertThat(error.code()).isNotBlank();
            assertThat(error.description()).isNotBlank();
        });
    }

    @Test
    void returnsImmutableStableMetadata() {
        EnumMetadataResponse first = service.getMetadata();
        EnumMetadataResponse second = service.getMetadata();

        assertThat(second).isSameAs(first);
        assertThatThrownBy(() -> first.ticketKind().add(
                new EnumOptionResponse("NEW", "New", "New option", "blue")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.mediaErrorCodes().add(
                new MediaErrorCodeResponse("NEW_ERROR", "New error")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertValues(
            List<EnumOptionResponse> options,
            Enum<?>[] enumValues) {
        List<String> expected = Arrays.stream(enumValues)
                .map(Enum::name)
                .toList();

        assertThat(options)
                .extracting(EnumOptionResponse::value)
                .containsExactlyElementsOf(expected);
    }
}
