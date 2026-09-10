package com.project8.jobvault.matching;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchPaginationTest {

    @Test
    void rejectsOffsetsThatWouldRequireAnUnboundedRankingHeap() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> MatchPagination.validate(20, MatchPagination.MAX_OFFSET + 1));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void acceptsTheConfiguredDeepPagingLimit() {
        assertDoesNotThrow(() -> MatchPagination.validate(100, MatchPagination.MAX_OFFSET));
    }
}
