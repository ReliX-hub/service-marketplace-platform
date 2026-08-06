package com.relix.marketplace.payment.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.payment.config.PaymentProperties;
import com.relix.marketplace.payment.config.StripeProperties;
import com.relix.marketplace.payment.dto.PaymentConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/payments/config")
@RequiredArgsConstructor
@Tag(name = "Payment configuration", description = "Browser-safe payment gateway configuration")
public class PaymentConfigController {

    private final PaymentProperties paymentProperties;
    private final StripeProperties stripeProperties;

    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get public payment configuration")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Active gateway configuration")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> getPaymentConfig() {
        String gateway = paymentProperties.getGateway() == null
                ? "mock"
                : paymentProperties.getGateway().trim().toLowerCase(Locale.ROOT);
        String publishableKey = "stripe".equals(gateway)
                ? stripeProperties.getPublishableKey()
                : null;

        return ResponseEntity.ok(ApiResponse.success(
                new PaymentConfigResponse(gateway, publishableKey)));
    }
}
