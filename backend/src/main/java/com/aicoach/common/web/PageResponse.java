package com.aicoach.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Pagination envelope used by every list endpoint. {@code page} is 1-based. */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }
}
