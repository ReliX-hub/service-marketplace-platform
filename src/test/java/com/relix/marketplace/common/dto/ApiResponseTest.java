package com.relix.marketplace.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void legacyErrorFactoryRemainsCompatible() throws Exception {
        ApiResponse<Void> response = ApiResponse.error("Denied", "FORBIDDEN");

        assertFalse(response.isSuccess());
        assertEquals("FORBIDDEN", response.getError().getCode());
        assertNull(response.getError().getField());
        assertNull(response.getError().getDetails());

        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("\"field\""));
        assertFalse(json.contains("\"details\""));
    }

    @Test
    void errorFactoryCarriesMachineReadableFieldAndDetails() {
        ApiResponse<Void> response = ApiResponse.error(
                "A verified credential is required",
                "CREDENTIAL_REQUIRED",
                "categoryId",
                Map.of("required", "DRIVER_LICENSE"));

        assertEquals("categoryId", response.getError().getField());
        assertEquals(
                "DRIVER_LICENSE",
                ((Map<?, ?>) response.getError().getDetails()).get("required"));
        assertTrue(response.getTimestamp() != null);
    }
}
