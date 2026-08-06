package com.relix.marketplace.common.meta;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Stable frontend metadata for marketplace enums and media workflow errors")
public record EnumMetadataResponse(
        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Ticket direction options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> ticketKind,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Ticket lifecycle options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> ticketStatus,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Ticket pricing options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> pricingMode,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Service location options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> locationMode,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Engagement lifecycle options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> engagementStatus,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Application lifecycle options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> applicationStatus,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Refund lifecycle options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> refundStatus,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Credential review options",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> credentialStatus,

        @ArraySchema(schema = @Schema(implementation = EnumOptionResponse.class),
                arraySchema = @Schema(description = "Supported credential types",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<EnumOptionResponse> credentialType,

        @ArraySchema(schema = @Schema(implementation = MediaErrorCodeResponse.class),
                arraySchema = @Schema(description = "Stable machine-readable errors used by media workflows; these are not enum values",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        List<MediaErrorCodeResponse> mediaErrorCodes) {

    public EnumMetadataResponse {
        ticketKind = List.copyOf(ticketKind);
        ticketStatus = List.copyOf(ticketStatus);
        pricingMode = List.copyOf(pricingMode);
        locationMode = List.copyOf(locationMode);
        engagementStatus = List.copyOf(engagementStatus);
        applicationStatus = List.copyOf(applicationStatus);
        refundStatus = List.copyOf(refundStatus);
        credentialStatus = List.copyOf(credentialStatus);
        credentialType = List.copyOf(credentialType);
        mediaErrorCodes = List.copyOf(mediaErrorCodes);
    }
}
