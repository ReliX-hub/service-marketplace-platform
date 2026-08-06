package com.relix.marketplace.payment.controller;

import com.relix.marketplace.common.exception.GlobalExceptionHandler;
import com.relix.marketplace.payment.webhook.InvalidStripeWebhookException;
import com.relix.marketplace.payment.webhook.RetryableWebhookException;
import com.relix.marketplace.payment.webhook.StripeWebhookService;
import com.relix.marketplace.payment.webhook.WebhookProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    private static final String PAYLOAD = "{\"id\":\"evt_123\",\"object\":\"event\"}";
    private static final String SIGNATURE = "t=123,v1=signed";

    @Mock
    private StripeWebhookService stripeWebhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StripeWebhookController(stripeWebhookService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validWebhookReturnsOkAndForwardsExactRawBodyAndSignature() throws Exception {
        when(stripeWebhookService.process(PAYLOAD, SIGNATURE)).thenReturn(
                new WebhookProcessingResult(
                        "evt_123",
                        "payment_intent.succeeded",
                        WebhookProcessingResult.Status.PROCESSED));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Webhook processed"));

        verify(stripeWebhookService).process(PAYLOAD, SIGNATURE);
    }

    @Test
    void forgedSignatureReturnsBadRequestWithMachineReadableCode() throws Exception {
        when(stripeWebhookService.process(PAYLOAD, SIGNATURE))
                .thenThrow(new InvalidStripeWebhookException());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    void processedDuplicateIsAcknowledgedWithOk() throws Exception {
        when(stripeWebhookService.process(PAYLOAD, SIGNATURE)).thenReturn(
                new WebhookProcessingResult(
                        "evt_123",
                        "payment_intent.succeeded",
                        WebhookProcessingResult.Status.DUPLICATE));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Webhook already processed"));
    }

    @Test
    void outOfOrderLocalStateReturnsServerErrorSoStripeRetries() throws Exception {
        when(stripeWebhookService.process(PAYLOAD, SIGNATURE))
                .thenThrow(new RetryableWebhookException("Local payment does not exist yet"));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
