package com.project8.jobvault.matching;

import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pageable adapter that preserves an API offset even when it is not page-aligned. */
public record OffsetPageRequest(long offset, int pageSize, Sort sort) implements Pageable {
    public OffsetPageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        sort = Objects.requireNonNull(sort, "sort");
    }

    public OffsetPageRequest(long offset, int pageSize) {
        this(offset, pageSize, Sort.unsorted());
    }

    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / pageSize);
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(offset + pageSize, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious()
                ? new OffsetPageRequest(offset - pageSize, pageSize, sort)
                : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, pageSize, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        return new OffsetPageRequest((long) pageNumber * pageSize, pageSize, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset >= pageSize;
    }
}
