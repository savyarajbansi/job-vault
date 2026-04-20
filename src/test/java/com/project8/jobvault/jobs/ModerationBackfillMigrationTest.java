package com.project8.jobvault.jobs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModerationBackfillMigrationTest {

    @Test
    void backfillUpdatesOnlyActiveRowsWithNullModerationActionAndIsIdempotent()
            throws SQLException {
        String databaseUrl = "jdbc:h2:mem:jobvault-backfill-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ"
                + " AS TIMESTAMP WITH TIME ZONE";

        migrateThroughVersion(databaseUrl, "3");

        UUID employerId = UUID.randomUUID();
        UUID activeNullJobId = UUID.randomUUID();
        UUID activeApprovedJobId = UUID.randomUUID();
        UUID draftNullJobId = UUID.randomUUID();
        UUID disabledNullJobId = UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "")) {
            insertUser(connection, employerId);
            insertJob(connection, activeNullJobId, employerId, "ACTIVE", null);
            insertJob(connection, activeApprovedJobId, employerId, "ACTIVE", "APPROVED");
            insertJob(connection, draftNullJobId, employerId, "DRAFT", null);
            insertJob(connection, disabledNullJobId, employerId, "DISABLED", null);
        }

        migrateToLatest(databaseUrl);

        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "")) {
            assertEquals("APPROVED", moderationActionFor(connection, activeNullJobId));
            assertEquals("APPROVED", moderationActionFor(connection, activeApprovedJobId));
            assertNull(moderationActionFor(connection, draftNullJobId));
            assertNull(moderationActionFor(connection, disabledNullJobId));

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V4__backfill_active_moderation_action.sql"));

            assertEquals("APPROVED", moderationActionFor(connection, activeNullJobId));
            assertEquals("APPROVED", moderationActionFor(connection, activeApprovedJobId));
            assertNull(moderationActionFor(connection, draftNullJobId));
            assertNull(moderationActionFor(connection, disabledNullJobId));
        }
    }

    private void migrateThroughVersion(String databaseUrl, String targetVersion) {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private void migrateToLatest(String databaseUrl) {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertUser(Connection connection, UUID userId) throws SQLException {
        String sql = """
                INSERT INTO users (id, email, password_hash, enabled)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setString(2, "employer-" + userId + "@example.com");
            statement.setString(3, "hash");
            statement.setBoolean(4, true);
            statement.executeUpdate();
        }
    }

    private void insertJob(
            Connection connection,
            UUID jobId,
            UUID employerId,
            String status,
            String moderationAction) throws SQLException {
        String sql = """
                INSERT INTO jobs (id, employer_id, title, description, status, moderation_action)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, jobId);
            statement.setObject(2, employerId);
            statement.setString(3, "title");
            statement.setString(4, "description");
            statement.setString(5, status);
            statement.setString(6, moderationAction);
            statement.executeUpdate();
        }
    }

    private String moderationActionFor(Connection connection, UUID jobId) throws SQLException {
        String sql = "SELECT moderation_action FROM jobs WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString(1);
            }
        }
    }
}