package com.relix.marketplace.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final Map<String, RefundResult> refunds = new ConcurrentHashMap<>();

    @Override
    public boolean webhookDriven() {
        return false;
    }

    @Override
    public PaymentIntentResult createIntent(PaymentIntentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment intent request is required");
        }
        GatewayRequestValidator.validateIdempotencyKey(request.idempotencyKey());
        MinorUnitConverter.convert(request.amount(), request.currency());

        String providerRef = "pi_mock_" + digest("intent:" + request.idempotencyKey());
        return new PaymentIntentResult(
                providerRef,
                providerRef + "_secret_mock",
                "succeeded");
    }

    @Override
    public PaymentIntentResult retrieveIntent(String providerRef) {
        GatewayRequestValidator.validatePaymentIntentId(providerRef);
        return new PaymentIntentResult(
                providerRef,
                providerRef + "_secret_mock",
                "succeeded");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Refund request is required");
        }
        GatewayRequestValidator.validatePaymentIntentId(request.paymentIntentId());
        GatewayRequestValidator.validateIdempotencyKey(request.idempotencyKey());
        MinorUnitConverter.MinorUnitAmount amount =
                MinorUnitConverter.convert(request.amount(), request.currency());

        String providerRef = "re_mock_" + digest("refund:" + request.idempotencyKey());
        RefundResult result = new RefundResult(
                providerRef,
                "succeeded",
                amount.value(),
                amount.currency(),
                request.paymentIntentId(),
                null,
                null);
        RefundResult existing = refunds.putIfAbsent(providerRef, result);
        return existing == null ? result : existing;
    }

    @Override
    public RefundResult retrieveRefund(String providerRef) {
        GatewayRequestValidator.validateRefundId(providerRef);
        RefundResult result = refunds.get(providerRef);
        if (result == null) {
            throw new PaymentGatewayException("Mock refund does not exist: " + providerRef);
        }
        return result;
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
