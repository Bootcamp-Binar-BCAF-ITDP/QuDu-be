package com.delvin.loan.configuration;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

        if (param instanceof Collection<?> values) {
            if (values.isEmpty()) {
                return "*";
            }
            return values.stream()
                    .map(this::normalise)
                    .sorted()
                    .collect(Collectors.joining("+"));
        }

        String value = String.valueOf(param).trim().toLowerCase();

        return value.isEmpty() ? "*" : value;
    }

    private String describe(Pageable pageable) {

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
