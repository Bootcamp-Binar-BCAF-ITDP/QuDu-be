package com.delvin.loan.configuration;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.evict.EvictsRoleCaches;
import com.delvin.loan.common.evict.EvictsBranchCaches;
import com.delvin.loan.common.evict.EvictsPlafondCaches;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.service.BranchService;
import com.delvin.loan.service.MenuService;
import com.delvin.loan.service.PlafondService;
import com.delvin.loan.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();

    @Test
    @DisplayName("cached entries expire, so a missed eviction goes stale rather than staying wrong forever")
    void entriesExpire() {
        Duration ttl = config.cacheConfiguration().getTtlFunction().getTimeToLive(Object.class, null);

        assertThat(ttl).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("the catalog is given its own, longer TTL")
    void catalogHasItsOwnTtl() {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();

        config.cacheTtls().customize(builder);

        assertThat(builder.getCacheConfigurationFor(CacheNames.PLAFOND_CATALOG))
                .get()
                .extracting(configuration ->
                        configuration.getTtlFunction().getTimeToLive(Object.class, null))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("nulls are not cached, or a 404 would outlive the row being created")
    void nullsAreNotCached() {
        assertThat(config.cacheConfiguration().getAllowCacheNullValues()).isFalse();
    }

    @Test
    @DisplayName("keys are namespaced, so this app can share a Redis without colliding")
    void keysArePrefixed() {
        assertThat(config.cacheConfiguration().getKeyPrefixFor(CacheNames.PLAFOND_BY_ID))
                .isEqualTo("qudu::" + CacheNames.PLAFOND_BY_ID + "::");
    }

    /**
     * The catalog holds one value, so its Redis key is meant to read exactly
     * "qudu::plafond:catalog". That takes two things that have to agree: no
     * trailing "::" in the prefix, and an empty SpEL key on the method. Spring's
     * default for a no-argument method would otherwise append "SimpleKey []".
     */
    @Test
    @DisplayName("the catalog key is exactly qudu::plafond:catalog")
    void catalogKeyHasNoSuffix() throws Exception {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        String prefix = builder.getCacheConfigurationFor(CacheNames.PLAFOND_CATALOG)
                .orElseThrow()
                .getKeyPrefixFor(CacheNames.PLAFOND_CATALOG);

        Object key = config.keyGenerator()
                .generate(null, PlafondService.class.getDeclaredMethod("catalog"));

        assertThat(prefix).isEqualTo("qudu::plafond:catalog");
        assertThat(prefix + key).isEqualTo("qudu::plafond:catalog");
    }

    /**
     * Spring keys a no-argument method by SimpleKey.EMPTY, which prints as
     * "SimpleKey []" — and against the no-trailing-"::" prefix these caches use
     * it would read "qudu::plafond:catalogSimpleKey []". The key generator
     * turns that into the empty string instead, so no service needs to remember
     * to write key = "''".
     */
    @Test
    @DisplayName("a method with no arguments is keyed by the empty string, not SimpleKey")
    void noArgumentMethodsGetAnEmptyKey() throws Exception {

        KeyGenerator generator = config.keyGenerator();

        assertThat(generator.generate(null, PlafondService.class.getDeclaredMethod("catalog")))
                .isEqualTo("");
        assertThat(generator.generate(null, BranchService.class.getDeclaredMethod("getBranchOptions")))
                .isEqualTo("");

        // Methods with arguments keep Spring's own behaviour.
        assertThat(generator.generate(null,
                PlafondService.class.getDeclaredMethod("getById", Integer.class), 7))
                .isEqualTo(7);
    }

    /**
     * BRANCH_PAGE was added to CacheNames and annotated on the service but left
     * out of cacheTtls, which silently falls back to the untyped serializer and
     * returns a Map on the first cache hit. This fails the build instead.
     */
    @Test
    @DisplayName("every cache name declares a typed configuration")
    void everyCacheNameIsRegistered() throws Exception {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        List<String> unregistered = new java.util.ArrayList<>();

        for (java.lang.reflect.Field field : CacheNames.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class) {
                continue;
            }
            String cacheName = (String) field.get(null);
            if (builder.getCacheConfigurationFor(cacheName).isEmpty()) {
                unregistered.add(field.getName() + " (" + cacheName + ")");
            }
        }

        assertThat(unregistered)
                .as("cache names missing from RedisConfig.cacheTtls()")
                .isEmpty();
    }

    @Test
    @DisplayName("a keyed cache still shows its id: qudu::plafond:byId::1")
    void keyedCachesKeepTheirArgument() {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        String prefix = builder.getCacheConfigurationFor(CacheNames.PLAFOND_BY_ID)
                .orElseThrow()
                .getKeyPrefixFor(CacheNames.PLAFOND_BY_ID);

        assertThat(prefix + "1").isEqualTo("qudu::plafond:byId::1");
    }

    @Test
    @DisplayName("the catalog survives a round trip as List<PlafondResponse>, not a list of maps")
    void valuesRoundTripAsTheirOwnType() {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        RedisCacheConfiguration configuration =
                builder.getCacheConfigurationFor(CacheNames.PLAFOND_CATALOG).orElseThrow();

        PlafondResponse plafond = PlafondResponse.builder()
                .plafondId(1)
                .level(1)
                .description("Bronze")
                .minimumAmount(new BigDecimal("1000000"))
                .maxAmount(new BigDecimal("10000000"))
                .interestRate(new BigDecimal("0.12"))
                .build();

        byte[] bytes = configuration.getValueSerializationPair().write(List.of(plafond)).array();
        Object back = configuration.getValueSerializationPair().read(java.nio.ByteBuffer.wrap(bytes));

        assertThat(back).isInstanceOf(List.class);
        assertThat((List<?>) back).singleElement().isInstanceOf(PlafondResponse.class);
        assertThat(((PlafondResponse) ((List<?>) back).get(0)).getMaxAmount())
                .isEqualByComparingTo("10000000");
    }

    @Test
    @DisplayName("the error handler is registered through CachingConfigurer, or Spring ignores it")
    void errorHandlerIsRegistered() {
        assertThat(config).isInstanceOf(org.springframework.cache.annotation.CachingConfigurer.class);
        assertThat(config.errorHandler()).isNotNull();
    }

    @Test
    @DisplayName("a Redis that refuses reads and writes degrades to no cache, not to a broken API")
    void readAndWriteFailuresAreSwallowed() {

        CacheErrorHandler handler = config.errorHandler();
        Cache cache = new org.springframework.cache.concurrent.ConcurrentMapCache("plafond:catalog");
        RuntimeException redisDown = new IllegalStateException("redis: connection refused");

        assertThatCode(() -> handler.handleCacheGetError(redisDown, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(redisDown, cache, "k", "v")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a failed eviction is NOT swallowed: stale prices must not pass silently")
    void evictionFailuresStillThrow() {

        CacheErrorHandler handler = config.errorHandler();
        Cache cache = new org.springframework.cache.concurrent.ConcurrentMapCache("plafond:catalog");
        RuntimeException redisDown = new IllegalStateException("redis: connection refused");

        assertThatThrownBy(() -> handler.handleCacheEvictError(redisDown, cache, "k")).isSameAs(redisDown);
        assertThatThrownBy(() -> handler.handleCacheClearError(redisDown, cache)).isSameAs(redisDown);
    }

    /**
     * A #name in a cache key that does not match a parameter is not a compile
     * error and not a SpEL error either: an unknown variable evaluates to null,
     * and Spring reports "Null key returned for cache operation" at runtime,
     * with a hint about the -parameters flag that has nothing to do with it.
     * That cost a debugging round on BranchService.getBranchById, whose key
     * said #branchId while the parameter was branchCode.
     */
    @Test
    @DisplayName("every cache key names a parameter the method actually has")
    void cacheKeysNameRealParameters() {

        List<String> broken = Stream.of(PlafondService.class, BranchService.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> {
                    Cacheable cacheable =
                            AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
                    if (cacheable == null || !cacheable.key().startsWith("#")) {
                        return false;
                    }
                    String referenced = cacheable.key().substring(1);
                    return Arrays.stream(method.getParameters())
                            .map(Parameter::getName)
                            .noneMatch(referenced::equals);
                })
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName()
                        + " -> key " + AnnotatedElementUtils
                                .findMergedAnnotation(method, Cacheable.class).key())
                .toList();

        assertThat(broken)
                .as("cache keys referring to a parameter that does not exist")
                .isEmpty();
    }

    /**
     * A page is only the same page when the filter, the number, the size and
     * the sort all match. Serving page 0 sorted by level as though it were page
     * 0 sorted by name would show the wrong rows, so each part is in the key.
     */
    @Test
    @DisplayName("the page key covers filter, page, size and sort")
    void pageKeyCoversEveryPart() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = PlafondService.class.getDeclaredMethod("getAll", String.class, Pageable.class);
        Pageable page = PageRequest.of(0, 10, Sort.by("level").ascending());

        assertThat(generator.generate(null, method, "Gold", page))
                .isEqualTo("search=gold|page=0|size=10|sort=level:ASC");

        assertThat(generator.generate(null, method, "Gold", PageRequest.of(1, 10, Sort.by("level").ascending())))
                .isNotEqualTo(generator.generate(null, method, "Gold", page));

        assertThat(generator.generate(null, method, "Gold", PageRequest.of(0, 20, Sort.by("level").ascending())))
                .isNotEqualTo(generator.generate(null, method, "Gold", page));

        assertThat(generator.generate(null, method, "Gold", PageRequest.of(0, 10, Sort.by("level").descending())))
                .isNotEqualTo(generator.generate(null, method, "Gold", page));
    }

    @Test
    @DisplayName("a blank, null or differently-cased filter is the same page, not three")
    void pageKeyNormalisesTheFilter() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = PlafondService.class.getDeclaredMethod("getAll", String.class, Pageable.class);
        Pageable page = PageRequest.of(0, 10);

        Object blank = generator.generate(null, method, "   ", page);

        assertThat(blank).isEqualTo(generator.generate(null, method, (Object) null, page));
        assertThat(blank).isEqualTo("search=*|page=0|size=10|sort=none");
        assertThat(generator.generate(null, method, "GOLD", page))
                .isEqualTo(generator.generate(null, method, "gold", page));
    }

    @Test
    @DisplayName("an unpaged request is named, not read — Pageable.unpaged() throws on getPageNumber")
    void unpagedDoesNotBlowUp() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = PlafondService.class.getDeclaredMethod("getAll", String.class, Pageable.class);

        assertThat(generator.generate(null, method, "gold", Pageable.unpaged()))
                .isEqualTo("search=gold|page=all|size=all|sort=none");
    }

    @Test
    @DisplayName("a cached page reads back as PageResponse with typed rows, not maps")
    void pagesRoundTripTyped() {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        RedisCacheConfiguration configuration =
                builder.getCacheConfigurationFor(CacheNames.PLAFOND_PAGE).orElseThrow();

        PageResponse<PlafondResponse> page = new PageResponse<>(
                List.of(PlafondResponse.builder().plafondId(1).level(1).description("Bronze").build()),
                0, 10, 1, 1, true, true, false);

        byte[] bytes = configuration.getValueSerializationPair().write(page).array();
        Object back = configuration.getValueSerializationPair().read(java.nio.ByteBuffer.wrap(bytes));

        assertThat(back).isInstanceOf(PageResponse.class);
        assertThat(((PageResponse<?>) back).getContent())
                .singleElement()
                .isInstanceOf(PlafondResponse.class);
        assertThat(((PageResponse<?>) back).getTotalElements()).isEqualTo(1);
    }

    /**
     * A no-argument @Cacheable holds one value, and those caches are registered
     * with singleEntry(), whose prefix has no trailing "::". Forget the empty
     * key and Spring falls back to SimpleKey.EMPTY, gluing the two together
     * into "qudu::plafond:catalogSimpleKey []". Verified by running the two
     * configurations side by side, 2026-09-21.
     */
    @Test
    @DisplayName("every role write evicts the role caches")
    void everyRoleWriteEvicts() {

        Set<String> writeMethods = Set.of("createRole", "updateRole", "deleteRole");

        List<Method> unguarded = Arrays.stream(RoleService.class.getDeclaredMethods())
                .filter(method -> writeMethods.contains(method.getName()))
                .filter(method -> !AnnotatedElementUtils.hasAnnotation(method, EvictsRoleCaches.class))
                .toList();

        assertThat(unguarded)
                .as("role write methods without @EvictsRoleCaches")
                .isEmpty();
    }

    @Test
    @DisplayName("every branch write evicts the branch caches")
    void everyBranchWriteEvicts() {

        Set<String> writeMethods = Set.of("createBranch", "updateBranch", "deleteBranch");

        List<Method> unguarded = Arrays.stream(BranchService.class.getDeclaredMethods())
                .filter(method -> writeMethods.contains(method.getName()))
                .filter(method -> !AnnotatedElementUtils.hasAnnotation(method, EvictsBranchCaches.class))
                .toList();

        assertThat(unguarded)
                .as("branch write methods without @EvictsBranchCaches")
                .isEmpty();
    }

    @Test
    @DisplayName("every plafond write evicts the plafond caches")
    void everyWriteEvicts() {

        Set<String> writeMethods = Set.of("create", "update", "delete");

        List<Method> unguarded = Arrays.stream(PlafondService.class.getDeclaredMethods())
                .filter(method -> writeMethods.contains(method.getName()))
                .filter(method -> !AnnotatedElementUtils.hasAnnotation(method, EvictsPlafondCaches.class))
                .toList();

        assertThat(unguarded)
                .as("plafond write methods without @EvictsPlafondCaches")
                .isEmpty();
    }

    @Test
    @DisplayName("the composed eviction annotation clears every plafond cache, the paged listing included")
    void compositeAnnotationClearsEveryCache() {

        Caching caching = EvictsPlafondCaches.class.getAnnotation(Caching.class);

        assertThat(Arrays.stream(caching.evict()).map(evict -> evict.cacheNames()[0]))
                .containsExactlyInAnyOrder(
                        CacheNames.PLAFOND_CATALOG,
                        CacheNames.PLAFOND_BY_ID,
                        CacheNames.PLAFOND_BY_LEVEL,
                        CacheNames.PLAFOND_PAGE);

        assertThat(caching.evict()).allMatch(CacheEvict::allEntries);
    }

    @Test
    @DisplayName("the catalog is read through the cache")
    void catalogIsCacheable() throws Exception {

        // Merged, not raw: value() and cacheNames() are @AliasFor each other, so
        // plain reflection sees only whichever one was written.
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(
                PlafondService.class.getDeclaredMethod("catalog"), Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheNames.PLAFOND_CATALOG);
    }
}
