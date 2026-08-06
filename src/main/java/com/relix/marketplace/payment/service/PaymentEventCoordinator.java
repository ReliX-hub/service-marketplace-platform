package com.relix.marketplace.payment.service;

import com.relix.marketplace.payment.webhook.PaymentEventHandler;
import com.relix.marketplace.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventCoordinator implements PaymentEventHandler {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @Override
    public void onPaymentIntentSucceeded(PaymentIntentSucceeded event) {
        paymentService.handlePaymentSucceeded(event);
    }

    @Override
    public void onPaymentIntentFailed(PaymentIntentFailed event) {
        paymentService.handlePaymentFailed(event);
    }

    @Override
    public void onRefundChanged(RefundChanged event) {
        refundService.handleRefundChanged(event);
    }

    @Override
    public void onChargeRefunded(ChargeRefunded event) {
        refundService.handleChargeRefunded(event);
    }
}
