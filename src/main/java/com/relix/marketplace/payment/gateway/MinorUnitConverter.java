package com.relix.marketplace.payment.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public final class MinorUnitConverter {

    private MinorUnitConverter() {
    }

    public static MinorUnitAmount convert(BigDecimal amount, String currencyCode) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ISO-4217 currency: " + currencyCode, exception);
        }

        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("Currency does not define minor units: " + currency.getCurrencyCode());
        }

        try {
            BigDecimal scaled = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
            long minorUnits = scaled.movePointRight(fractionDigits).longValueExact();
            return new MinorUnitAmount(minorUnits, currency.getCurrencyCode().toLowerCase(Locale.ROOT));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Amount has more than " + fractionDigits + " fractional digits for "
                            + currency.getCurrencyCode() + " or exceeds the supported range",
                    exception);
        }
    }

    public record MinorUnitAmount(long value, String currency) {
    }
}
