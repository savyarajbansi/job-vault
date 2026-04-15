package com.project8.jobvault.schema;

import com.project8.jobvault.applications.ApplicationStatus;
import com.project8.jobvault.jobs.JobModerationAction;
import com.project8.jobvault.jobs.JobStatus;
import com.project8.jobvault.resumes.ResumeProcessingStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatusEnumsTest {

    @Test
    void jobStatusIncludesLifecycleStates() {
        for (String name : List.of("DRAFT", "ACTIVE", "DISABLED")) {
            assertNotNull(JobStatus.valueOf(name));
        }
    }

    @Test
    void applicationStatusIncludesStateMachineStates() {
        for (String name : List.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "REJECTED", "ACCEPTED", "WITHDRAWN")) {
            assertNotNull(ApplicationStatus.valueOf(name));
        }
    }

    @Test
    void resumeProcessingStatusIncludesStateMachineStates() {
        for (String name : List.of("UPLOADED", "PARSING", "PARSED", "FAILED")) {
            assertNotNull(ResumeProcessingStatus.valueOf(name));
        }
    }

    @Test
    void jobModerationActionIncludesExpectedActions() {
        for (String name : List.of("APPROVED", "REJECTED", "DISABLED")) {
            assertNotNull(JobModerationAction.valueOf(name));
        }
    }
}
