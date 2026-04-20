package com.project8.jobvault.testing;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicConcurrencyHarnessTest {

    @Test
    void bootstrapRetrySucceedsAfterTransientSetupFailures() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = DeterministicConcurrencyHarness.withBootstrapRetry("bootstrap", 3, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient setup failure");
            }
            return "ready";
        });

        assertEquals("ready", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void bootstrapRetryThrowsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DeterministicConcurrencyHarness.withBootstrapRetry("bootstrap", 2, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("always fails");
                }));

        assertEquals(2, attempts.get());
        assertEquals("Bootstrap failed for scenario 'bootstrap' after 2 attempts", error.getMessage());
    }

    @Test
    void concurrentContentionAssertsSingleWinnerAndSingleConflict() throws Exception {
        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "reactivate-race",
                "thread-a",
                () -> DeterministicConcurrencyHarness.ContentionResponse.of(200, "{\"status\":\"ACTIVE\"}"),
                "thread-b",
                () -> DeterministicConcurrencyHarness.ContentionResponse.of(409, "{\"error\":\"conflict\"}"));

        DeterministicConcurrencyHarness.assertSingleWinnerAndConflict("reactivate-race", result, 200);
    }

    @Test
    void contentionAssertionFailsForUnexpectedStatuses() throws Exception {
        DeterministicConcurrencyHarness.ContentionResult result = DeterministicConcurrencyHarness.runTwoContenders(
                "bad-race",
                "thread-a",
                () -> DeterministicConcurrencyHarness.ContentionResponse.of(200, "ok"),
                "thread-b",
                () -> DeterministicConcurrencyHarness.ContentionResponse.of(200, "ok"));

        assertThrows(AssertionError.class,
                () -> DeterministicConcurrencyHarness.assertSingleWinnerAndConflict("bad-race", result, 200));
    }
}
