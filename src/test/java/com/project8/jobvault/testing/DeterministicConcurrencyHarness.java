package com.project8.jobvault.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeterministicConcurrencyHarness {

    private static final Logger logger = LoggerFactory.getLogger(DeterministicConcurrencyHarness.class);
    private static final int CONTENDER_COUNT = 2;
    private static final Duration START_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DONE_TIMEOUT = Duration.ofSeconds(10);

    private DeterministicConcurrencyHarness() {
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public record ContentionResponse(int statusCode, String body) {

        public static ContentionResponse of(int statusCode, String body) {
            return new ContentionResponse(statusCode, body == null ? "" : body);
        }
    }

    public static final class AttemptOutcome {
        private final String contender;
        private final ContentionResponse response;
        private final Throwable failure;

        private AttemptOutcome(String contender, ContentionResponse response, Throwable failure) {
            this.contender = contender;
            this.response = response;
            this.failure = failure;
        }

        public static AttemptOutcome success(String contender, ContentionResponse response) {
            return new AttemptOutcome(contender, response, null);
        }

        public static AttemptOutcome failure(String contender, Throwable failure) {
            return new AttemptOutcome(contender, ContentionResponse.of(-1, ""), failure);
        }

        public String contender() {
            return contender;
        }

        public int statusCode() {
            return response.statusCode();
        }

        public String body() {
            return response.body();
        }

        public Throwable failure() {
            return failure;
        }

        public boolean isFailure() {
            return failure != null;
        }
    }

    public static final class ContentionResult {
        private final String scenario;
        private final List<AttemptOutcome> outcomes;
        private final AtomicBoolean firstFailureLogged = new AtomicBoolean(false);

        private ContentionResult(String scenario, List<AttemptOutcome> outcomes) {
            this.scenario = scenario;
            this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
        }

        public String scenario() {
            return scenario;
        }

        public List<AttemptOutcome> outcomes() {
            return outcomes;
        }

        boolean markFirstFailureLogged() {
            return firstFailureLogged.compareAndSet(false, true);
        }
    }

    public static <T> T withBootstrapRetry(String scenario, int maxAttempts, CheckedSupplier<T> bootstrapAction)
            throws Exception {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return bootstrapAction.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < maxAttempts) {
                    logger.warn(
                            "Bootstrap attempt {} failed for scenario '{}': {}",
                            attempt,
                            scenario,
                            ex.getMessage());
                }
            }
        }

        throw new IllegalStateException(
                "Bootstrap failed for scenario '" + scenario + "' after " + maxAttempts + " attempts",
                lastException);
    }

    public static CyclicBarrier newBarrier(int parties) {
        return new CyclicBarrier(parties);
    }

    public static CountDownLatch newLatch(int count) {
        return new CountDownLatch(count);
    }

    public static void awaitBarrier(CyclicBarrier barrier, Duration timeout, String scenario)
            throws InterruptedException {
        try {
            barrier.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | BrokenBarrierException ex) {
            throw new IllegalStateException("Barrier orchestration failed for scenario '" + scenario + "'", ex);
        }
    }

    public static void awaitLatch(CountDownLatch latch, Duration timeout, String scenario)
            throws InterruptedException {
        boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            throw new IllegalStateException("Latch orchestration timed out for scenario '" + scenario + "'");
        }
    }

    public static ContentionResult runTwoContenders(
            String scenario,
            String firstName,
            CheckedSupplier<ContentionResponse> firstAction,
            String secondName,
            CheckedSupplier<ContentionResponse> secondAction) throws Exception {

        CyclicBarrier startBarrier = newBarrier(CONTENDER_COUNT + 1);
        CountDownLatch doneLatch = newLatch(CONTENDER_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(CONTENDER_COUNT);
        try {
            Future<AttemptOutcome> firstFuture = executor.submit(
                    contenderCallable(scenario, firstName, firstAction, startBarrier, doneLatch));
            Future<AttemptOutcome> secondFuture = executor.submit(
                    contenderCallable(scenario, secondName, secondAction, startBarrier, doneLatch));

            awaitBarrier(startBarrier, START_TIMEOUT, scenario + "-coordinator");
            awaitLatch(doneLatch, DONE_TIMEOUT, scenario);

            List<AttemptOutcome> outcomes = new ArrayList<>(CONTENDER_COUNT);
            outcomes.add(firstFuture.get());
            outcomes.add(secondFuture.get());
            return new ContentionResult(scenario, outcomes);
        } finally {
            executor.shutdownNow();
        }
    }

    public static void assertSingleWinnerAndConflict(
            String scenario,
            ContentionResult result,
            int... winnerStatusCodes) {

        Set<Integer> winners = new HashSet<>();
        for (int statusCode : winnerStatusCodes) {
            winners.add(statusCode);
        }

        List<AttemptOutcome> outcomes = result.outcomes();
        long failureCount = outcomes.stream().filter(AttemptOutcome::isFailure).count();
        long conflictCount = outcomes.stream().filter(outcome -> outcome.statusCode() == 409).count();
        long winnerCount = outcomes.stream().filter(outcome -> winners.contains(outcome.statusCode())).count();

        if (failureCount == 0 && conflictCount == 1 && winnerCount == 1) {
            return;
        }

        String reason = String.format(
                "Expected exactly one winner and one 409 conflict for scenario '%s' but got statuses %s",
                scenario,
                outcomes.stream().map(outcome -> Integer.toString(outcome.statusCode())).toList());
        logFirstFailureDiagnostics(result, reason);
        throw new AssertionError(reason);
    }

    private static Callable<AttemptOutcome> contenderCallable(
            String scenario,
            String contenderName,
            CheckedSupplier<ContentionResponse> action,
            CyclicBarrier startBarrier,
            CountDownLatch doneLatch) {

        return () -> {
            try {
                awaitBarrier(startBarrier, START_TIMEOUT, scenario + "-" + contenderName);
                ContentionResponse response = action.get();
                return AttemptOutcome.success(contenderName, response);
            } catch (Throwable ex) {
                return AttemptOutcome.failure(contenderName, ex);
            } finally {
                doneLatch.countDown();
            }
        };
    }

    private static void logFirstFailureDiagnostics(ContentionResult result, String reason) {
        if (!result.markFirstFailureLogged()) {
            return;
        }

        logger.error("First-failure contention diagnostics for scenario '{}': {}", result.scenario(), reason);
        for (AttemptOutcome outcome : result.outcomes()) {
            String failureMessage = outcome.failure() == null ? "none" : outcome.failure().toString();
            logger.error(
                    "scenario='{}' contender='{}' status='{}' failure='{}' body='{}'",
                    result.scenario(),
                    outcome.contender(),
                    outcome.statusCode(),
                    failureMessage,
                    truncateBody(outcome.body()));
        }
    }

    private static String truncateBody(String body) {
        int maxLength = 240;
        if (body == null) {
            return "";
        }
        if (body.length() <= maxLength) {
            return body;
        }
        return body.substring(0, maxLength) + "...";
    }
}
