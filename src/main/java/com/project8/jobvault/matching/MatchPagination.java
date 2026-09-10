package com.project8.jobvault.matching;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MatchPagination {
    public static final int MAX_PAGE_SIZE = 100;
    /**
     * Matching is ranked in memory, so an unbounded offset is also an
     * unbounded heap allocation. Keep deep paging useful without allowing a
     * request to reserve an arbitrary amount of memory.
     */
    public static final int MAX_OFFSET = 10_000;

    private MatchPagination() {
    }

    public static void validate(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE || offset < 0 || offset > MAX_OFFSET) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100 and offset must be between 0 and 10000");
        }
        if ((long) offset + limit > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagination range is too large");
        }
    }
}
