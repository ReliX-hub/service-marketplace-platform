-- Align every transaction ledger with the marketplace ticket/application precision.
-- Existing V1/V5/V8 columns were DECIMAL(10,2); marketplace prices use DECIMAL(12,2).

ALTER TABLE engagements
    ALTER COLUMN amount TYPE DECIMAL(12, 2);

ALTER TABLE payments
    ALTER COLUMN amount TYPE DECIMAL(12, 2);

ALTER TABLE refunds
    ALTER COLUMN amount TYPE DECIMAL(12, 2);

ALTER TABLE settlements
    ALTER COLUMN total_price TYPE DECIMAL(12, 2),
    ALTER COLUMN platform_fee TYPE DECIMAL(12, 2),
    ALTER COLUMN provider_payout TYPE DECIMAL(12, 2);
