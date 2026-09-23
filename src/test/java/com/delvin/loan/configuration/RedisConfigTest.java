package com.delvin.loan.configuration;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.evict.EvictsRoleCaches;
import com.delvin.loan.common.evict.EvictsApplicationCaches;
import com.delvin.loan.common.evict.EvictsUserCaches;
import com.delvin.loan.common.evict.EvictsBranchCaches;
import com.delvin.loan.common.evict.EvictsPlafondCaches;
import com.delvin.loan.dto.request.auth.RegisterRequest;
import com.delvin.loan.dto.response.loanresp.CustomerSummary;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.service.AuthService;
import com.delvin.loan.service.BranchManagerService;
import com.delvin.loan.service.BranchService;
import com.delvin.loan.service.CustomerService;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.LoanDisbursementService;
import com.delvin.loan.service.LoanDocumentService;
import com.delvin.loan.service.LoanReviewService;
import com.delvin.loan.service.LoanVerificationService;
import com.delvin.loan.service.MenuService;
import com.delvin.loan.service.PlafondService;
import com.delvin.loan.service.RoleService;
import com.delvin.loan.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("a method with no arguments is keyed by the empty string, not SimpleKey")
    void noArgumentMethodsGetAnEmptyKey() throws Exception {

        KeyGenerator generator = config.keyGenerator();

        assertThat(generator.generate(null, PlafondService.class.getDeclaredMethod("catalog")))
                .isEqualTo("");
        assertThat(generator.generate(null, BranchService.class.getDeclaredMethod("getBranchOptions")))
                .isEqualTo("");

        assertThat(generator.generate(null,
                PlafondService.class.getDeclaredMethod("getById", Integer.class), 7))
                .isEqualTo(7);
    }

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

    @Test
    @DisplayName("registering a USER evicts the user caches; registering a CUSTOMER does not")
    void registerEvictsForStaffOnly() throws Exception {

        Caching caching = AuthService.class
                .getDeclaredMethod("register", RegisterRequest.class)
                .getAnnotation(Caching.class);

        assertThat(caching).as("@Caching on AuthService.register").isNotNull();

        assertThat(Arrays.stream(caching.evict()).map(evict -> evict.cacheNames()[0]))
                .containsExactlyInAnyOrder(CacheNames.USER_PAGE, CacheNames.USER_BY_ID);

        RegisterRequest staff = new RegisterRequest();
        staff.setAccountType(AccountType.USER);

        RegisterRequest customer = new RegisterRequest();
        customer.setAccountType(AccountType.CUSTOMER);

        SpelExpressionParser parser = new SpelExpressionParser();

        for (CacheEvict evict : caching.evict()) {

            assertThat(evict.allEntries())
                    .as("a new user can appear on any page, so all entries go")
                    .isTrue();
            assertThat(evict.beforeInvocation())
                    .as("a rejected registration must not clear the cache")
                    .isFalse();

            Expression condition = parser.parseExpression(evict.condition());

            StandardEvaluationContext staffContext = new StandardEvaluationContext();
            staffContext.setVariable("request", staff);
            assertThat(condition.getValue(staffContext, Boolean.class))
                    .as("condition is true for a staff registration")
                    .isTrue();

            StandardEvaluationContext customerContext = new StandardEvaluationContext();
            customerContext.setVariable("request", customer);
            assertThat(condition.getValue(customerContext, Boolean.class))
                    .as("condition is false for a customer registration")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the register eviction sits on the public method, where the proxy can see it")
    void registerEvictionIsOnThePublicMethod() {

        List<String> annotatedPrivates = Arrays.stream(AuthService.class.getDeclaredMethods())
                .filter(method -> !java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, Caching.class)
                        || AnnotatedElementUtils.hasAnnotation(method, CacheEvict.class))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(annotatedPrivates)
                .as("non-public AuthService methods carrying cache annotations (they would never run)")
                .isEmpty();
    }

    @Test
    @DisplayName("every step of the loan workflow evicts the application caches")
    void everyWorkflowStepEvicts() {

        Map<Class<?>, Set<String>> writeMethods = Map.of(
                CustomerService.class, Set.of("createApplication"),
                LoanReviewService.class, Set.of("submitReview"),
                BranchManagerService.class, Set.of("branchManagerDecision", "decidePlafondRequest"),
                LoanVerificationService.class, Set.of("submitVerification"),
                LoanDisbursementService.class, Set.of("disburse"),
                LoanDocumentService.class, Set.of("uploadDocument", "uploadOwnDocument"));

        List<String> unguarded = writeMethods.entrySet().stream()
                .flatMap(entry -> Arrays.stream(entry.getKey().getDeclaredMethods())
                        .filter(method -> entry.getValue().contains(method.getName()))
                        .filter(method -> !AnnotatedElementUtils.hasAnnotation(
                                method, EvictsApplicationCaches.class))
                        .map(method -> entry.getKey().getSimpleName() + "." + method.getName()))
                .toList();

        assertThat(unguarded)
                .as("workflow steps without @EvictsApplicationCaches")
                .isEmpty();

        writeMethods.forEach((type, names) -> {
            Set<String> declared = Arrays.stream(type.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(declared).as(type.getSimpleName()).containsAll(names);
        });
    }

    @Test
    @DisplayName("a cached application page reads back as LoanApplicationResponse, not maps")
    void applicationPageRoundTripsTyped() {

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        config.cacheTtls().customize(builder);

        RedisCacheConfiguration configuration =
                builder.getCacheConfigurationFor(CacheNames.APPLICATION_PAGE).orElseThrow();

        CustomerSummary customer = new CustomerSummary();
        customer.setCustomerId("CUST-001");
        customer.setCustomerName("Budi");

        LoanApplicationResponse application = new LoanApplicationResponse();
        application.setApplicationId("LA-20260101-ABCD1234");
        application.setCustomer(customer);
        application.setStatus("CHECKING");
        application.setRequestedAmount(new BigDecimal("5000000"));
        application.setSubmissionDate(LocalDate.of(2026, 1, 1));

        PageResponse<LoanApplicationResponse> page = new PageResponse<>(
                List.of(application), 0, 10, 1, 1, true, true, false);

        byte[] bytes = configuration.getValueSerializationPair().write(page).array();
        Object back = configuration.getValueSerializationPair().read(java.nio.ByteBuffer.wrap(bytes));

        assertThat(back).isInstanceOf(PageResponse.class);

        Object row = ((PageResponse<?>) back).getContent().get(0);
        assertThat(row).isInstanceOf(LoanApplicationResponse.class);

        LoanApplicationResponse readBack = (LoanApplicationResponse) row;
        assertThat(readBack.getApplicationId()).isEqualTo("LA-20260101-ABCD1234");
        assertThat(readBack.getStatus()).isEqualTo("CHECKING");
        assertThat(readBack.getRequestedAmount()).isEqualByComparingTo("5000000");
        assertThat(readBack.getSubmissionDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(readBack.getCustomer()).isInstanceOf(CustomerSummary.class);
        assertThat(readBack.getCustomer().getCustomerName()).isEqualTo("Budi");
    }

    @Test
    @DisplayName("the application caches clear the staff list, the customer list and the limits together")
    void applicationEvictionCoversAllThree() {

        Caching caching = EvictsApplicationCaches.class.getAnnotation(Caching.class);

        assertThat(Arrays.stream(caching.evict()).map(evict -> evict.cacheNames()[0]))
                .containsExactlyInAnyOrder(
                        CacheNames.APPLICATION_PAGE,
                        CacheNames.APPLICATION_BY_CUSTOMER,
                        CacheNames.CUSTOMER_PLAFOND);

        assertThat(caching.evict()).allMatch(CacheEvict::allEntries);
    }

    @Test
    @DisplayName("a status filter is a set: order does not create a second key")
    void statusFilterOrderDoesNotMatter() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = LoanApplicationService.class.getDeclaredMethod(
                "getAllApplication", List.class, String.class, LocalDate.class,
                LocalDate.class, Pageable.class);
        Pageable page = PageRequest.of(0, 10);

        Object ascending = generator.generate(null, method,
                List.of("CHECKING", "VERIFIED"), null, null, null, page);
        Object descending = generator.generate(null, method,
                List.of("VERIFIED", "CHECKING"), null, null, null, page);

        assertThat(ascending).isEqualTo(descending);
        assertThat(ascending).isEqualTo("search=checking+verified,*,*,*|page=0|size=10|sort=none");
    }

    @Test
    @DisplayName("the date range is part of the key")
    void dateRangeIsPartOfTheKey() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = LoanApplicationService.class.getDeclaredMethod(
                "getAllApplication", List.class, String.class, LocalDate.class,
                LocalDate.class, Pageable.class);
        Pageable page = PageRequest.of(0, 10);

        Object january = generator.generate(null, method, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), page);
        Object february = generator.generate(null, method, null, null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), page);

        assertThat(january).isNotEqualTo(february);
    }

    @Test
    @DisplayName("one customer's history never serves another's")
    void customerHistoryIsKeyedByCustomer() throws Exception {

        PageCacheKeyGenerator generator = new PageCacheKeyGenerator();
        Method method = LoanApplicationService.class.getDeclaredMethod(
                "listByCustomer", String.class, Pageable.class);
        Pageable page = PageRequest.of(0, 10);

        assertThat(generator.generate(null, method, "CUST-001", page))
                .isNotEqualTo(generator.generate(null, method, "CUST-002", page));
    }

    @Test
    @DisplayName("every user write evicts the user caches")
    void everyUserWriteEvicts() {

        Set<String> writeMethods = Set.of("updateUser", "deleteUser");

        List<Method> unguarded = Arrays.stream(UserService.class.getDeclaredMethods())
                .filter(method -> writeMethods.contains(method.getName()))
                .filter(method -> !AnnotatedElementUtils.hasAnnotation(method, EvictsUserCaches.class))
                .toList();

        assertThat(unguarded)
                .as("user write methods without @EvictsUserCaches")
                .isEmpty();
    }

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
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(
                PlafondService.class.getDeclaredMethod("catalog"), Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheNames.PLAFOND_CATALOG);
    }
}
