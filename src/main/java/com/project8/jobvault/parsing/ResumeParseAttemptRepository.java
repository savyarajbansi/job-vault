package com.project8.jobvault.parsing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeParseAttemptRepository extends JpaRepository<ResumeParseAttempt, UUID> {
    interface ErrorCodeCount {
        String getErrorCode();
        long getTotal();
    }

    long countByStatus(ResumeParseAttemptStatus status);

    @Query("""
            select attempt.errorCode as errorCode, count(attempt) as total
            from ResumeParseAttempt attempt
            where attempt.status = :status and attempt.errorCode is not null
            group by attempt.errorCode
            """)
    List<ErrorCodeCount> countByStatusAndErrorCode(@Param("status") ResumeParseAttemptStatus status);

    @Query("select max(attempt.createdAt) from ResumeParseAttempt attempt")
    Instant findLatestAttemptAt();
}
