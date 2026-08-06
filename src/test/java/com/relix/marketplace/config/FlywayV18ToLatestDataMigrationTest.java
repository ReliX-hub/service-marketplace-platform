package com.relix.marketplace.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayV18ToLatestDataMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flyway_upgrade_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v18ThroughLatestPreserveHistoricalScheduleAndRemoveLegacySecretsAndSlots() throws Exception {
        migrateTo("17");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, email, password_hash, name, role, status)
                    VALUES
                        (9001, 'migration-client@example.com', 'hash', 'Migration Client', 'USER', 'ACTIVE'),
                        (9002, 'migration-worker@example.com', 'hash', 'Migration Worker', 'USER', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO worker_profiles (
                        id, user_id, display_name, rating, review_count, verified, completed_jobs)
                    VALUES (9001, 9002, 'Migration Worker', 0.00, 0, TRUE, 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO tickets (
                        id, kind, author_id, worker_id, category_id, title,
                        pricing_mode, price, currency, location_mode, status, expires_at)
                    VALUES
                        (9001, 'OFFER', 9002, 9001,
                            (SELECT id FROM categories WHERE code = 'MOVING'),
                            'Historical migration offer', 'FIXED', 100.00, 'USD', 'ON_SITE',
                            'MATCHED', NULL),
                        (9002, 'REQUEST', 9001, NULL,
                            (SELECT id FROM categories WHERE code = 'MOVING'),
                            'Expired migration request', 'FIXED', 50.00, 'USD', 'ON_SITE',
                            'OPEN', '2020-01-01T00:00:00Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO time_slots (id, worker_id, start_time, end_time, status)
                    VALUES
                        (9001, 9001, '2030-01-10T10:00:00Z', '2030-01-10T12:00:00Z', 'BOOKED'),
                        (9002, 9001, '2030-01-11T10:00:00Z', '2030-01-11T13:00:00Z', 'BOOKED')
                    """);
            statement.executeUpdate("""
                    INSERT INTO engagements (
                        id, client_id, worker_id, ticket_id, time_slot_id,
                        status, amount, scheduled_start, scheduled_end)
                    VALUES
                        (9001, 9001, 9001, 9001, 9001, 'ACCEPTED', 100.00, NULL, NULL),
                        (9002, 9001, 9001, 9001, 9002, 'ACCEPTED', 100.00,
                            '2030-01-20T10:00:00Z', NULL)
                    """);
            statement.executeUpdate("""
                    INSERT INTO payments (
                        id, engagement_id, request_id, amount, status, currency, client_secret)
                    VALUES (9001, 9001, 'migration-payment', 100.00, 'PENDING', 'USD', 'pi_secret_legacy')
                    """);
            statement.executeUpdate("""
                    INSERT INTO applications (
                        id, ticket_id, applicant_id, proposed_amount, status)
                    VALUES (9001, 9002, 9002, 50.00, 'PENDING')
                    """);
        }

        migrateToLatest();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT id, scheduled_start, scheduled_end
                    FROM engagements
                    WHERE id IN (9001, 9002)
                    ORDER BY id
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("id")).isEqualTo(9001L);
                assertThat(result.getTimestamp("scheduled_start").toInstant())
                        .isEqualTo(Instant.parse("2030-01-10T10:00:00Z"));
                assertThat(result.getTimestamp("scheduled_end").toInstant())
                        .isEqualTo(Instant.parse("2030-01-10T12:00:00Z"));

                assertThat(result.next()).isTrue();
                assertThat(result.getLong("id")).isEqualTo(9002L);
                assertThat(result.getTimestamp("scheduled_start").toInstant())
                        .isEqualTo(Instant.parse("2030-01-20T10:00:00Z"));
                assertThat(result.getTimestamp("scheduled_end")).isNull();
                assertThat(result.next()).isFalse();
            }

            assertThat(columnExists(connection, "payments", "client_secret")).isFalse();
            assertThat(columnExists(connection, "refunds", "provider_status")).isTrue();
            assertThat(columnExists(connection, "refunds", "failure_message")).isTrue();
            assertThat(columnExists(connection, "engagements", "time_slot_id")).isFalse();
            assertThat(tableExists(connection, "time_slots")).isFalse();
            assertThat(tableExists(connection, "superseded_payment_intents")).isTrue();
            assertThat(singleString(statement, "SELECT status FROM tickets WHERE id = 9002"))
                    .isEqualTo("EXPIRED");
            assertThat(singleString(statement, "SELECT status FROM applications WHERE id = 9001"))
                    .isEqualTo("EXPIRED");
        }
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?)
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = ?)
                """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private String singleString(Statement statement, String query) throws Exception {
        try (ResultSet result = statement.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
