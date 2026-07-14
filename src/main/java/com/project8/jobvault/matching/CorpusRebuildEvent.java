package com.project8.jobvault.matching;

/**
 * Application event published when a job mutation commits and the TF-IDF
 * corpus needs to be refreshed.
 *
 * Publishing this event rather than calling
 * CorpusIdfService#rebuildFromRepository() directly has two benefits:
 * - The rebuild runs after the transaction commits, so newly persisted data
 *   is visible when the corpus is re-read from the database.
 * - The rebuild runs on a separate async thread, so the HTTP response is
 *   returned to the client before the corpus recomputation begins.
 *
 * @param reason a short human-readable label for log/debug output,
 *               e.g. "job-published" or "job-approved".
 */
public record CorpusRebuildEvent(String reason) {
}