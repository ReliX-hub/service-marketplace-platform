package com.relix.marketplace.payment.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.payment.webhook.InvalidStripeWebhookException;
import com.relix.marketplace.payment.webhook.StripeWebhookService;
import com.relix.marketplace.payment.webhook.WebhookProcessingResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe Webhooks", description = "Signed asynchronous payment-provider events")
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Receive a Stripe webhook",
            description = "JWT-free provider endpoint protected by Stripe-Signature verification and event-id idempotency")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Event processed or an already-processed event acknowledged"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Missing or invalid Stripe signature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Retryable local processing failure")
    })
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestBody String rawPayload,
            @Parameter(description = "Stripe-generated signature; never persisted or logged")
            @RequestHeader(name = "Stripe-Signature", required = false) String stripeSignature) {
        try {
            WebhookProcessingResult result = stripeWebhookService.process(rawPayload, stripeSignature);
            String message = result.duplicate()
                    ? "Webhook already processed"
                    : "Webhook processed";
            return ResponseEntity.ok(ApiResponse.success(null, message));
        } catch (InvalidStripeWebhookException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "Invalid Stripe webhook signature or payload",
                            "INVALID_WEBHOOK_SIGNATURE"));
        }
    }
}
