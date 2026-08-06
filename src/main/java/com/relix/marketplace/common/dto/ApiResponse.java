package com.relix.marketplace.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Common API success or error envelope")
public class ApiResponse<T> {

    @Schema(description = "Whether the request completed successfully", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean success;

    @Schema(description = "Human-readable result message", example = "Request completed",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String message;

    @Schema(description = "Typed success payload", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private T data;

    @Schema(description = "Machine-readable error payload; absent on success",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ErrorDetails error;

    @Builder.Default
    @Schema(description = "UTC response creation time", type = "string", format = "date-time",
            example = "2026-08-05T12:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return error(message, code, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String code, Object details) {
        return error(message, code, null, details);
    }

    public static <T> ApiResponse<T> error(
            String message,
            String code,
            String field,
            Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorDetails.builder()
                        .code(code)
                        .field(field)
                        .details(details)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Machine-readable API error details")
    public static class ErrorDetails {

        @Schema(description = "Stable error code for client-side branching",
                example = "CREDENTIAL_REQUIRED", requiredMode = Schema.RequiredMode.REQUIRED)
        private String code;

        @Schema(description = "Invalid request field, when applicable", example = "proposedAmount",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private String field;

        @Schema(description = "Structured error-specific context",
                implementation = java.util.Map.class,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        private Object details;
    }
}
