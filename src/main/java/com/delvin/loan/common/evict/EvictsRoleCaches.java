package com.delvin.loan.common.evict;

import com.delvin.loan.common.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Clears every role cache. Goes on any method that changes a role.
 *
 * allEntries because the paged listing holds one entry per filter, page, size
 * and sort: a single renamed role can change what appears on any of them, and
 * there is no way to know which.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.ROLE_PAGE, allEntries = true),
})
public @interface EvictsRoleCaches {
}
