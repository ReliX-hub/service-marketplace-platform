package com.relix.marketplace.common.exception;

import com.relix.marketplace.config.JwtAuthenticationFilter;
import com.relix.marketplace.storage.StorageException;
import com.relix.marketplace.storage.service.InvalidImageException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GlobalExceptionHandlerWebMvcTest.TestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerWebMvcTest.TestController.class)
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsDomainCodeFieldAndDetails() throws Exception {
        mockMvc.perform(get("/test/credential"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CREDENTIAL_REQUIRED"))
                .andExpect(jsonPath("$.error.field").value("categoryId"))
                .andExpect(jsonPath("$.error.details.required").value("DRIVER_LICENSE"));
    }

    @Test
    void returnsStructuredBeanValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("name"))
                .andExpect(jsonPath("$.error.details.violations[0].field").value("name"));
    }

    @Test
    void mapsEnumQueryMismatchWithoutFallingThroughTo500() throws Exception {
        mockMvc.perform(get("/test/query-enum").param("mode", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.field").value("mode"))
                .andExpect(jsonPath("$.error.details.allowedValues[0]").value("LOCAL"));
    }

    @Test
    void mapsEnumJsonMismatchWithoutFallingThroughTo500() throws Exception {
        mockMvc.perform(post("/test/body-enum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.field").value("mode"))
                .andExpect(jsonPath("$.error.details.allowedValues[1]").value("REMOTE"));
    }

    @Test
    void mapsDataIntegrityViolationToConflictWithoutLeakingDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data"));
    }

    @Test
    void mapsLegacyIllegalEnumParsingToBadRequest() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void mapsMultipartLimitToMachineReadablePayloadTooLarge() throws Exception {
        mockMvc.perform(get("/test/upload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"))
                .andExpect(jsonPath("$.error.details.maxBytes").value(8));
    }

    @Test
    void mapsStorageReadFailureWithoutLeakingInternalDetails() throws Exception {
        mockMvc.perform(get("/test/storage-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("STORAGE_READ_FAILED"))
                .andExpect(jsonPath("$.message").value("File storage operation failed"));
    }

    @Test
    void mapsImageValidationFailureWithActionableDetails() throws Exception {
        mockMvc.perform(get("/test/image-unsupported"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TYPE_UNSUPPORTED"))
                .andExpect(jsonPath("$.error.details.accepted[0]").value("JPEG"));
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @GetMapping("/credential")
        void credentialRequired() {
            throw new ForbiddenException(
                    "A verified credential is required",
                    "CREDENTIAL_REQUIRED",
                    "categoryId",
                    Map.of("required", "DRIVER_LICENSE"));
        }

        @PostMapping("/validation")
        void validateBody(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/query-enum")
        void queryEnum(@RequestParam("mode") Mode mode) {
        }

        @PostMapping("/body-enum")
        void bodyEnum(@RequestBody EnumRequest request) {
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("sensitive constraint details");
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("No enum constant internal.Type.UNKNOWN");
        }

        @GetMapping("/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(8L);
        }

        @GetMapping("/storage-failure")
        void storageFailure() {
            throw new StorageException("sensitive disk path", "STORAGE_READ_FAILED");
        }

        @GetMapping("/image-unsupported")
        void imageUnsupported() {
            throw new InvalidImageException(
                    "Unsupported image format",
                    "IMAGE_TYPE_UNSUPPORTED",
                    Map.of("accepted", List.of("JPEG", "PNG", "WebP")));
        }
    }

    record ValidationRequest(@NotBlank(message = "Name is required") String name) {
    }

    record EnumRequest(Mode mode) {
    }

    enum Mode {
        LOCAL,
        REMOTE
    }
}
