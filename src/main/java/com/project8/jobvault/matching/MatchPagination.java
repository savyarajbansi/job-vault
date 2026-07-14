package com.project8.jobvault.matching;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MatchPagination {
    public static final int MAX_PAGE_SIZE = 100;

    private MatchPagination() {
    }

    public static void validate(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE || offset < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100 and offset must be non-negative");
        }
        if ((long) offset + limit > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagination range is too large");
        }
    }
}
