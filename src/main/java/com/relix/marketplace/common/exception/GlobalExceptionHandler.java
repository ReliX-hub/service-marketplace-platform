package com.relix.marketplace.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.storage.StorageException;
import com.relix.marketplace.storage.service.InvalidImageException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
        log.warn("Domain error: {} ({})", e.getMessage(), e.getCode());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(
                        e.getMessage(),
                        e.getCode(),
                        e.getField(),
                        e.getDetails()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "FORBIDDEN"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found", "NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<Map<String, String>> violations = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", defaultMessage(error.getDefaultMessage())))
                .toList();

        String field = violations.isEmpty() ? null : violations.get(0).get("field");
        String message = violations.isEmpty()
                ? "Validation failed"
                : violations.get(0).get("message");

        log.warn("Validation error on field {}: {}", field, message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        message,
                        "VALIDATION_ERROR",
                        field,
                        Map.of("violations", violations)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<Map<String, String>> violations = e.getConstraintViolations().stream()
                .map(this::toViolation)
                .toList();

        String field = violations.isEmpty() ? null : violations.get(0).get("field");
        String message = violations.isEmpty()
                ? "Validation failed"
                : violations.get(0).get("message");

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        message,
                        "VALIDATION_ERROR",
                        field,
                        Map.of("violations", violations)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        boolean enumType = requiredType != null && requiredType.isEnum();
        String code = enumType ? "INVALID_ENUM_VALUE" : "TYPE_MISMATCH";
        String message = enumType
                ? "Invalid value for " + e.getName()
                : "Invalid value type for " + e.getName();

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        message,
                        code,
                        e.getName(),
                        typeMismatchDetails(e.getValue(), requiredType)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        InvalidFormatException invalidFormat = findCause(e, InvalidFormatException.class);
        if (invalidFormat != null) {
            String field = invalidFormat.getPath().isEmpty()
                    ? null
                    : invalidFormat.getPath().get(invalidFormat.getPath().size() - 1).getFieldName();
            Class<?> targetType = invalidFormat.getTargetType();
            boolean enumType = targetType != null && targetType.isEnum();

            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            field == null ? "Invalid request value" : "Invalid value for " + field,
                            enumType ? "INVALID_ENUM_VALUE" : "INVALID_VALUE",
                            field,
                            typeMismatchDetails(invalidFormat.getValue(), targetType)));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Malformed JSON request", "MALFORMED_JSON"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        "Required parameter is missing: " + e.getParameterName(),
                        "MISSING_PARAMETER",
                        e.getParameterName(),
                        Map.of("expectedType", e.getParameterType())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "The request conflicts with existing data",
                        "DATA_INTEGRITY_VIOLATION"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        long maximum = e.getMaxUploadSize();
        Object details = maximum > 0 ? Map.of("maxBytes", maximum) : Map.of();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(
                        "Image exceeds the maximum upload size",
                        "IMAGE_TOO_LARGE",
                        null,
                        details));
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidImage(InvalidImageException e) {
        HttpStatus status = "IMAGE_TOO_LARGE".equals(e.getCode())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getMessage(), e.getCode(), null, e.getDetails()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageException(StorageException e) {
        HttpStatus status = switch (e.getCode()) {
            case "STORAGE_OBJECT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "STORAGE_KEY_INVALID" -> HttpStatus.BAD_REQUEST;
            case "STORAGE_OBJECT_EXISTS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            log.error("Storage failure: {} ({})", e.getMessage(), e.getCode(), e);
        } else {
            log.warn("Storage request failed: {} ({})", e.getMessage(), e.getCode());
        }
        String message = status.is5xxServerError()
                ? "File storage operation failed"
                : e.getMessage();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message, e.getCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid request argument: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid request value", "INVALID_ARGUMENT"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", "INTERNAL_ERROR"));
    }

    private Map<String, String> toViolation(ConstraintViolation<?> violation) {
        return Map.of(
                "field", lastPathSegment(violation.getPropertyPath().toString()),
                "message", defaultMessage(violation.getMessage()));
    }

    private static String lastPathSegment(String path) {
        int separator = path.lastIndexOf('.');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static String defaultMessage(String message) {
        return message == null || message.isBlank() ? "Validation failed" : message;
    }

    private static Map<String, Object> typeMismatchDetails(Object rejectedValue, Class<?> requiredType) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (rejectedValue != null) {
            details.put("rejectedValue", String.valueOf(rejectedValue));
        }
        if (requiredType != null) {
            details.put("expectedType", requiredType.getSimpleName());
            if (requiredType.isEnum()) {
                details.put("allowedValues", Arrays.stream(requiredType.getEnumConstants())
                        .map(String::valueOf)
                        .toList());
            }
        }
        return details;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
