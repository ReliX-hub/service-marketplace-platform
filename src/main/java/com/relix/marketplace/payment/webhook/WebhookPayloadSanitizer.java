package com.relix.marketplace.payment.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookPayloadSanitizer {

    private final ObjectMapper objectMapper;

    public JsonNode parse(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            throw new InvalidStripeWebhookException();
        }
    }

    public String sanitize(JsonNode payload) {
        JsonNode copy = payload.deepCopy();
        removeSecrets(copy);
        try {
            return objectMapper.writeValueAsString(copy);
        } catch (JsonProcessingException exception) {
            throw new RetryableWebhookException("Webhook payload could not be sanitized");
        }
    }

    private void removeSecrets(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> sensitiveFields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    sensitiveFields.add(field.getKey());
                } else {
                    removeSecrets(field.getValue());
                }
            }
            object.remove(sensitiveFields);
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::removeSecrets);
        }
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("secret")
                || normalized.equals("apikey")
                || normalized.equals("authorization");
    }
}
