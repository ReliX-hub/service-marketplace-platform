package com.relix.marketplace.payment.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private final StripeClient stripeClient;

    @Override
    public boolean webhookDriven() {
        return true;
    }

    @Override
    public PaymentIntentResult createIntent(PaymentIntentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment intent request is required");
        }
        GatewayRequestValidator.validateIdempotencyKey(request.idempotencyKey());
        MinorUnitConverter.MinorUnitAmount amount =
                MinorUnitConverter.convert(request.amount(), request.currency());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.value())
                .setCurrency(amount.currency())
                .putAllMetadata(request.metadata())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().create(
                    params,
                    requestOptions(request.idempotencyKey()));
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException exception) {
            throw new PaymentGatewayException("Stripe payment intent creation failed", exception);
        }
    }

    @Override
    public PaymentIntentResult retrieveIntent(String providerRef) {
        GatewayRequestValidator.validatePaymentIntentId(providerRef);

        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().retrieve(providerRef);
            return new PaymentIntentResult(
                    intent.getId(),
                    intent.getClientSecret(),
                    intent.getStatus());
        } catch (StripeException exception) {
            throw new PaymentGatewayException("Stripe payment intent retrieval failed", exception);
        }
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

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(request.paymentIntentId())
                .setAmount(amount.value())
                .putAllMetadata(request.metadata())
                .build();

        try {
            Refund refund = stripeClient.v1().refunds().create(
                    params,
                    requestOptions(request.idempotencyKey()));
            return toRefundResult(refund);
        } catch (StripeException exception) {
            throw new PaymentGatewayException("Stripe refund creation failed", exception);
        }
    }

    @Override
    public RefundResult retrieveRefund(String providerRef) {
        GatewayRequestValidator.validateRefundId(providerRef);

        try {
            return toRefundResult(stripeClient.v1().refunds().retrieve(providerRef));
        } catch (StripeException exception) {
            throw new PaymentGatewayException("Stripe refund retrieval failed", exception);
        }
    }

    private RefundResult toRefundResult(Refund refund) {
        return new RefundResult(
                refund.getId(),
                refund.getStatus(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getPaymentIntent(),
                refund.getCharge(),
                refund.getFailureReason());
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        return RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
    }
}
