package com.delvin.loan.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PaginationUtil {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private PaginationUtil() {}

    public static Pageable build(Integer page, Integer size, String sortBy, String sortDir) {

        int safePage = (page == null || page < 0) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        if (sortBy == null || sortBy.isBlank()) {
            return PageRequest.of(safePage, safeSize);
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortBy));
    }

    public static Pageable build(Integer page,
                                 Integer size,
                                 String sortBy,
                                 String sortDir,
                                 Set<String> allowedSortFields,
                                 String defaultSortBy) {

        String safeSortBy = (sortBy != null && allowedSortFields.contains(sortBy))
                ? sortBy
                : defaultSortBy;

        return build(page, size, safeSortBy, sortDir);
    }
}