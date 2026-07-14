package com.project8.jobvault.ratelimit;

public final class RateLimitErrorCodes {
    public static final String RATE_LIMITED = "ERR_RATE_LIMIT_001";
    public static final String MESSAGE_RATE_LIMITED = "Too many requests. Please try again later.";

    private RateLimitErrorCodes() {
    }
}
