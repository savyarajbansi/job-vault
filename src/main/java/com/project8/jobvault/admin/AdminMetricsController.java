package com.project8.jobvault.admin;

import com.project8.jobvault.matching.MatchAttemptRepository;
import com.project8.jobvault.matching.MatchAttemptStatus;
import com.project8.jobvault.parsing.ResumeParseAttemptRepository;
import com.project8.jobvault.parsing.ResumeParseAttemptStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {
    private final ObjectProvider<ResumeParseAttemptRepository> resumeParseAttemptRepositoryProvider;
    private final ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider;

    public AdminMetricsController(
            ObjectProvider<ResumeParseAttemptRepository> resumeParseAttemptRepositoryProvider,
            ObjectProvider<MatchAttemptRepository> matchAttemptRepositoryProvider) {
        this.resumeParseAttemptRepositoryProvider = resumeParseAttemptRepositoryProvider;
        this.matchAttemptRepositoryProvider = matchAttemptRepositoryProvider;
    }

    @GetMapping
    public AdminMetricsResponse getMetrics() {
        ResumeParseAttemptRepository parseRepository = resumeParseAttemptRepositoryProvider.getIfAvailable();
        MatchAttemptRepository matchRepository = matchAttemptRepositoryProvider.getIfAvailable();
        if (parseRepository == null || matchRepository == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metrics unavailable");
        }

        long parseTotal = parseRepository.count();
        long parseSuccess = parseRepository.countByStatus(ResumeParseAttemptStatus.SUCCESS);
        long parseFailure = parseRepository.countByStatus(ResumeParseAttemptStatus.FAILED);
        Instant parseLatest = parseRepository.findLatestAttemptAt();
        Map<String, Long> parseFailures = toParseErrorMap(parseRepository);

        long matchTotal = matchRepository.count();
        long matchSuccess = matchRepository.countByStatus(MatchAttemptStatus.SUCCESS);
        long matchFailure = matchRepository.countByStatus(MatchAttemptStatus.FAILED);
        Instant matchLatest = matchRepository.findLatestAttemptAt();
        Map<String, Long> matchFailures = toMatchErrorMap(matchRepository);

        return new AdminMetricsResponse(
                new AdminMetricsResponse.ParseMetrics(
                        parseTotal,
                        parseSuccess,
                        parseFailure,
                        parseLatest,
                        parseFailures),
                new AdminMetricsResponse.MatchMetrics(
                        matchTotal,
                        matchSuccess,
                        matchFailure,
                        matchLatest,
                        matchFailures));
    }

    private Map<String, Long> toParseErrorMap(ResumeParseAttemptRepository parseRepository) {
        return parseRepository.countByStatusAndErrorCode(ResumeParseAttemptStatus.FAILED).stream()
                .collect(Collectors.toMap(
                        ResumeParseAttemptRepository.ErrorCodeCount::getErrorCode,
                        ResumeParseAttemptRepository.ErrorCodeCount::getTotal,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, Long> toMatchErrorMap(MatchAttemptRepository matchRepository) {
        return matchRepository.countByStatusAndErrorCode(MatchAttemptStatus.FAILED).stream()
                .collect(Collectors.toMap(
                        MatchAttemptRepository.ErrorCodeCount::getErrorCode,
                        MatchAttemptRepository.ErrorCodeCount::getTotal,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }
}
