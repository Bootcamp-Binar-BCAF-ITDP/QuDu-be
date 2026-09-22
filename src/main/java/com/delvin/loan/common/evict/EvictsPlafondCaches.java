package com.delvin.loan.common.evict;

import com.delvin.loan.common.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PLAFOND_CATALOG, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PLAFOND_BY_ID, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PLAFOND_BY_LEVEL, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PLAFOND_PAGE, allEntries = true),
})
public @interface EvictsPlafondCaches {
}