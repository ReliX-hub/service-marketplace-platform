-- Stripe client secrets are short-lived browser confirmation credentials.
-- Persist only the PaymentIntent ID and retrieve its secret from Stripe when
-- the owning client needs to recover a pending payment session.

ALTER TABLE payments DROP COLUMN client_secret;
