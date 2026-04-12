package com.project8.jobvault.schema;

import com.project8.jobvault.applications.JobApplication;
import com.project8.jobvault.auth.RefreshToken;
import com.project8.jobvault.jobs.Job;
import com.project8.jobvault.resumes.ResumeMetadata;
import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityMappingTest {

    @Test
    void userAccountMapsToUsersTable() {
        assertNotNull(UserAccount.class.getAnnotation(Entity.class));
        Table table = UserAccount.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("users", table.name());
    }

    @Test
    void roleMapsToRolesTable() {
        assertNotNull(Role.class.getAnnotation(Entity.class));
        Table table = Role.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("roles", table.name());
    }

    @Test
    void refreshTokenMapsToRefreshTokensTable() {
        assertNotNull(RefreshToken.class.getAnnotation(Entity.class));
        Table table = RefreshToken.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("refresh_tokens", table.name());
    }

    @Test
    void jobMapsToJobsTable() {
        assertNotNull(Job.class.getAnnotation(Entity.class));
        Table table = Job.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("jobs", table.name());
    }

    @Test
    void jobApplicationMapsToApplicationsTable() {
        assertNotNull(JobApplication.class.getAnnotation(Entity.class));
        Table table = JobApplication.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("applications", table.name());
    }

    @Test
    void resumeMetadataMapsToResumesTable() {
        assertNotNull(ResumeMetadata.class.getAnnotation(Entity.class));
        Table table = ResumeMetadata.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("resumes", table.name());
    }
}
