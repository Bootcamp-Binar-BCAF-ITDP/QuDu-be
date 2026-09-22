package com.delvin.loan.common.evict;

import com.delvin.loan.common.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.BRANCH_OPTIONS, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.BRANCH_BY_ID, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.BRANCH_PAGE, allEntries = true),
})
public @interface EvictsBranchCaches {
}