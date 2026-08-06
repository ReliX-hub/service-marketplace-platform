package com.relix.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JsonTest
@Import(JacksonConfig.class)
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesBigDecimalAsJsonStringWithoutChangingItsScale() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("amount", new BigDecimal("150.00")));

        assertEquals("{\"amount\":\"150.00\"}", json);
    }
}
