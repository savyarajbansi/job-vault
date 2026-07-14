package com.project8.jobvault.ratelimit;

public class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(RateLimitErrorCodes.MESSAGE_RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
