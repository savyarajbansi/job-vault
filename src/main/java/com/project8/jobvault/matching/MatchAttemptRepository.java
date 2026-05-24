package com.project8.jobvault.matching;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchAttemptRepository extends JpaRepository<MatchAttempt, UUID> {
    interface ErrorCodeCount {
        String getErrorCode();
        long getTotal();
    }

    long countByStatus(MatchAttemptStatus status);

    @Query("""
            select attempt.errorCode as errorCode, count(attempt) as total
            from MatchAttempt attempt
            where attempt.status = :status and attempt.errorCode is not null
            group by attempt.errorCode
            """)
    List<ErrorCodeCount> countByStatusAndErrorCode(@Param("status") MatchAttemptStatus status);

    @Query("select max(attempt.createdAt) from MatchAttempt attempt")
    Instant findLatestAttemptAt();
}
