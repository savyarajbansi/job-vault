package com.project8.jobvault.ratelimit;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final long WINDOW_MILLIS = 60_000L;

    private final int uploadPerMinute;
    private final int matchPerMinute;
    private final Clock clock;
    private final ConcurrentMap<BucketKey, Window> windows = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${jobvault.ratelimit.upload-per-minute:5}") int uploadPerMinute,
            @Value("${jobvault.ratelimit.match-per-minute:30}") int matchPerMinute,
            Clock clock) {
        if (uploadPerMinute <= 0 || matchPerMinute <= 0) {
            throw new IllegalArgumentException("Rate limits must be positive");
        }
        this.uploadPerMinute = uploadPerMinute;
        this.matchPerMinute = matchPerMinute;
        this.clock = clock;
    }

    public void checkUpload(UUID userId) {
        check(userId, Bucket.UPLOAD, uploadPerMinute);
    }

    public void checkMatch(UUID userId) {
        check(userId, Bucket.MATCH, matchPerMinute);
    }

    private void check(UUID userId, Bucket bucket, int maximum) {
        if (userId == null) {
            return;
        }
        long now = clock.millis();
        BucketKey key = new BucketKey(userId, bucket);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= maximum) {
                long retryAfter = Math.max(1L, (WINDOW_MILLIS - (now - window.startedAt) + 999L) / 1000L);
                throw new RateLimitException(retryAfter);
            }
            window.count++;
        }
    }

    private enum Bucket {
        UPLOAD,
        MATCH
    }

    private record BucketKey(UUID userId, Bucket bucket) {
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
