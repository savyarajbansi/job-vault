package com.project8.jobvault.matching;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around ApplicationEventPublisher for posting
 * CorpusRebuildEvent instances.
 *
 * Inject this component instead of MatchingCorpusService in controllers
 * and services that need the matching corpus refreshed asynchronously
 * synchronously. The event is picked up by
 * MatchingCorpusService#onCorpusRebuildEvent after the surrounding transaction
 * commits, on a background thread.
 */
@Component
public class CorpusRebuildEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public CorpusRebuildEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Schedules an asynchronous BM25/embedding corpus rebuild to run after the current
     * transaction commits.
     *
     * @param reason short label written to the debug log, e.g. "job-published"
     */
    public void publishRebuild(String reason) {
        eventPublisher.publishEvent(new CorpusRebuildEvent(reason));
    }
}
