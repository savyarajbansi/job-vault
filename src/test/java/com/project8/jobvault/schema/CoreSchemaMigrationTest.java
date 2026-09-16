package com.project8.jobvault.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreSchemaMigrationTest {

    @Test
    void latestMigrationIncludesCoreMatchingTables() throws SQLException {
        String databaseUrl = "jdbc:h2:mem:jobvault-schema-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ"
                + " AS TIMESTAMP WITH TIME ZONE";

        migrateToLatest(databaseUrl);

        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "")) {
            assertColumnExists(connection, "resumes", "parsed_text");
            assertColumnExists(connection, "resumes", "inferred_skills");
            assertColumnExists(connection, "resumes", "storage_type");
            assertColumnExists(connection, "resumes", "storage_key");

            assertRoleExists(connection, "EMPLOYER");
            assertRoleExists(connection, "JOB_SEEKER");
            assertRoleAbsent(connection, "ADMIN");
            assertTableAbsent(connection, "candidate_match_notifications");
            assertColumnAbsent(connection, "notifications", "candidate_match_notification_id");
        }
    }

    private void migrateToLatest(String databaseUrl) {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void assertColumnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        String[] args = new String[] { tableName, columnName };
        assertEquals(1, countMatches(connection, sql, args));
    }

    private void assertRoleExists(Connection connection, String roleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM roles WHERE name = ?";
        assertEquals(1, countMatches(connection, sql, roleName));
    }

    private void assertColumnAbsent(Connection connection, String tableName, String columnName)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        assertEquals(0, countMatches(connection, sql, tableName, columnName));
    }

    private void assertTableAbsent(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = ?
                """;
        assertEquals(0, countMatches(connection, sql, tableName));
    }

    private void assertRoleAbsent(Connection connection, String roleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM roles WHERE name = ?";
        assertEquals(0, countMatches(connection, sql, roleName));
    }

    private int countMatches(Connection connection, String sql, String... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setString(i + 1, args[i].toUpperCase(Locale.ROOT));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0;
                }
                return resultSet.getInt(1);
            }
        }
    }
}
