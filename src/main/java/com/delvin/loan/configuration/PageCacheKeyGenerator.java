package com.delvin.loan.configuration;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Key for a cached page.
 *
 * A page is only the same page when the filter, the page number, the page size
 * AND the sort all match — change the sort and row 1 is a different plafond.
 * Spring's default key would be a SimpleKey built from the arguments, whose
 * toString for a Pageable reads
 * {@code Page request [number: 0, size: 10, sort: level: ASC]}: correct, but it
 * puts spaces and brackets in the Redis key, which then has to be quoted in
 * every redis-cli command.
 *
 * This produces {@code search=*|page=0|size=10|sort=level:ASC} instead, so keys
 * stay greppable:
 *
 * <pre>
 * KEYS qudu::plafond:page*
 * </pre>
 *
 * The filter is lower-cased because the query it feeds is case-insensitive:
 * "Gold" and "gold" return the same rows and should not cost two entries.
 */
@Component("pageKeyGenerator")
public class PageCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {

        List<String> filters = new ArrayList<>();
        Pageable pageable = null;

        for (Object param : params) {
            if (param instanceof Pageable candidate) {
                pageable = candidate;
            } else {
                filters.add(normalise(param));
            }
        }

        String filter = filters.isEmpty() ? "*" : String.join(",", filters);

        return "search=" + filter + "|" + describe(pageable);
    }

    private String normalise(Object param) {

        if (param == null) {
            return "*";
        }

        String value = String.valueOf(param).trim().toLowerCase();

        // A blank filter means "everything", the same as no filter at all;
        // keying them differently would cache the same page twice.
        return value.isEmpty() ? "*" : value;
    }

    private String describe(Pageable pageable) {

        // Pageable.unpaged() throws on getPageNumber(), so it is named, not read.
        if (pageable == null || pageable.isUnpaged()) {
            return "page=all|size=all|sort=" + sortOf(pageable);
        }

        return "page=" + pageable.getPageNumber()
                + "|size=" + pageable.getPageSize()
                + "|sort=" + sortOf(pageable);
    }

    private String sortOf(Pageable pageable) {

        Sort sort = pageable == null ? Sort.unsorted() : pageable.getSort();

        if (sort.isUnsorted()) {
            return "none";
        }

        return sort.stream()
                .map(order -> order.getProperty() + ":" + order.getDirection())
                .collect(Collectors.joining(","));
    }
}
