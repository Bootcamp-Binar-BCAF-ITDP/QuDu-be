package com.delvin.loan.configuration;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.dto.response.plafond.CustomerPlafondResponse;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.dto.response.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private static final Duration CATALOG_TTL = Duration.ofMinutes(30);

    private static final Duration PAGE_TTL = Duration.ofMinutes(2);
    private static final Duration VOLATILE_TTL = Duration.ofMinutes(1);

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return baseConfiguration(DEFAULT_TTL);
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtls() {
        return builder -> builder
                //Plafond
                .withCacheConfiguration(CacheNames.PLAFOND_CATALOG,
                        singleEntry(typed(baseConfiguration(CATALOG_TTL), listOf(PlafondResponse.class))))
                .withCacheConfiguration(CacheNames.PLAFOND_BY_ID,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(PlafondResponse.class)))
                .withCacheConfiguration(CacheNames.PLAFOND_BY_LEVEL,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(PlafondResponse.class)))
                //Branch
                .withCacheConfiguration(CacheNames.BRANCH_OPTIONS,
                        singleEntry(typed(baseConfiguration(CATALOG_TTL), listOf(BranchResponse.class))))
                .withCacheConfiguration(CacheNames.BRANCH_BY_ID,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(BranchResponse.class)))
                //Role
                .withCacheConfiguration(CacheNames.ROLE_OPTIONS,
                        singleEntry(typed(baseConfiguration(CATALOG_TTL), listOf(RoleResponse.class))))
                .withCacheConfiguration(CacheNames.ROLE_BY_ID,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(RoleResponse.class)))
                // Menu
                .withCacheConfiguration(CacheNames.MENU_OPTIONS,
                        singleEntry(typed(baseConfiguration(CATALOG_TTL), listOf(MenuResponse.class))))
                .withCacheConfiguration(CacheNames.MENU_BY_ID,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(MenuResponse.class)))
                // User
                .withCacheConfiguration(CacheNames.USER_BY_ID,
                        typed(baseConfiguration(DEFAULT_TTL), typeOf(UserResponse.class)))
                // Paged listings
                .withCacheConfiguration(CacheNames.PLAFOND_PAGE,
                        typed(baseConfiguration(PAGE_TTL), pageOf(PlafondResponse.class)))
                .withCacheConfiguration(CacheNames.ROLE_PAGE,
                        typed(baseConfiguration(PAGE_TTL), pageOf(RoleResponse.class)))
                .withCacheConfiguration(CacheNames.BRANCH_PAGE,
                        typed(baseConfiguration(PAGE_TTL), pageOf(BranchResponse.class)))
                .withCacheConfiguration(CacheNames.USER_PAGE,
                        typed(baseConfiguration(PAGE_TTL), pageOf(UserResponse.class)))
                .withCacheConfiguration(CacheNames.APPLICATION_PAGE,
                        typed(baseConfiguration(VOLATILE_TTL), pageOf(LoanApplicationResponse.class)))
                .withCacheConfiguration(CacheNames.APPLICATION_BY_CUSTOMER,
                        typed(baseConfiguration(VOLATILE_TTL), pageOf(LoanApplicationResponse.class)))
                .withCacheConfiguration(CacheNames.CUSTOMER_PLAFOND,
                        typed(baseConfiguration(VOLATILE_TTL), typeOf(CustomerPlafondResponse.class)))
                ;
    }

    private RedisCacheConfiguration singleEntry(RedisCacheConfiguration configuration) {
        return configuration.computePrefixWith(cacheName -> "qudu::" + cacheName);
    }

    private RedisCacheConfiguration typed(RedisCacheConfiguration configuration, JavaType type) {
        return configuration.serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<>(type)));
    }

    private static JavaType typeOf(Class<?> type) {
        return TypeFactory.createDefaultInstance().constructType(type);
    }

    private static JavaType pageOf(Class<?> rowType) {
        return TypeFactory.createDefaultInstance()
                .constructParametricType(PageResponse.class, rowType);
    }

    private static JavaType listOf(Class<?> elementType) {
        return TypeFactory.createDefaultInstance()
                .constructCollectionType(java.util.List.class, elementType);
    }

    private RedisCacheConfiguration baseConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("qudu::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(typeOf(Object.class))));
    }

    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) ->
                params.length == 0 ? "" : SimpleKeyGenerator.generateKey(params);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed on {} (key {}); falling back to the database: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache write failed on {} (key {}): {}",
                        cache.getName(), key, exception.toString());
            }
        };
    }
}
