package com.delvin.loan.service;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.evict.EvictsApplicationCaches;
import org.springframework.cache.annotation.Cacheable;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.*;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDecision;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.LoanDecisionRepository;
import com.delvin.loan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final LoanMapper mapper;
    private final CreditScoreService creditScoreService;

    @Cacheable(cacheNames = CacheNames.APPLICATION_PAGE, keyGenerator = "pageKeyGenerator")
    @Transactional(readOnly = true)
    public PageResponse<LoanApplicationResponse> getAllApplication(
            List<String> statuses, String search, LocalDate from, LocalDate to, Pageable pageable) {

        List<String> filters = normalizeStatuses(statuses);
        if (filters.isEmpty()) {
            filters = List.copyOf(VALID_STATUSES);
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("from cannot be after to");
        }

        Page<LoanApplication> applications = applicationRepository.filter(
                filters,
                normalizeSearch(search),
                from == null ? EARLIEST : from,
                to == null ? LATEST : to,
                pageable);

        return PageResponse.of(applications, mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> getMyBucket(
            String userId, String roleName, Pageable pageable) {

        if (roleName == null || roleName.isBlank()) {
            throw BusinessException.forbidden("User has no role assigned");
        }

        return switch (roleName.trim().toUpperCase()) {
            case RoleName.MARKETING      -> listMarketingBucket(userId, pageable);
            case RoleName.BRANCH_MANAGER -> listBranchManagerBucket(userId, pageable);
            case RoleName.BACK_OFFICE    -> listBackOfficeBucket(userId, pageable);
            default -> throw BusinessException.forbidden(
                    "Role " + roleName + " has no application bucket");
        };
    }

    private List<String> normalizeStatuses(List<String> statuses) {

        List<String> normalized = new ArrayList<>();

        if (statuses == null) {
            return normalized;
        }

        for (String raw : statuses) {

            if (raw == null || raw.isBlank()) {
                continue;
            }

            String status = raw.trim().toUpperCase();

            if (!VALID_STATUSES.contains(status)) {
                throw BusinessException.badRequest(
                        "Invalid status: " + raw + ". Allowed: " + VALID_STATUSES);
            }

            if (!normalized.contains(status)) {
                normalized.add(status);
            }
        }

        return normalized;
    }

    public LoanApplicationResponse getApplication(String applicationId) {
        return mapper.toApplicationResponse(getApplicationOrThrow(applicationId));
    }

    @Transactional(readOnly = true)
    public CreditScoreResponse creditScore(String applicationId) {
        return creditScoreService.evaluate(getApplicationOrThrow(applicationId));
    }

    @Cacheable(cacheNames = CacheNames.APPLICATION_BY_CUSTOMER, keyGenerator = "pageKeyGenerator")
    public PageResponse<LoanApplicationResponse> listByCustomer(String customerId, Pageable pageable) {
        return PageResponse.of(
                applicationRepository.findByCustomer_CustomerId(customerId, pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listMarketingBucket(
            String marketingUserId, Pageable pageable) {

        User marketing = getUserWithRole(marketingUserId, RoleName.MARKETING);

        return PageResponse.of(
                applicationRepository.findBucket(
                        LoanStatus.CHECKING, requireBranch(marketing), pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listBranchManagerBucket(
            String branchManagerUserId, Pageable pageable) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);

        return PageResponse.of(
                applicationRepository.findBucket(
                        LoanStatus.PENDING_BRANCH_MANAGER, requireBranch(branchManager), pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listBackOfficeBucket(
            String backOfficeUserId, Pageable pageable) {

        User backOffice = getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);

        return PageResponse.of(
                applicationRepository.findBucket(
                        LoanStatus.PENDING_BACK_OFFICE, requireBranch(backOffice), pageable),
                mapper::toApplicationResponse);
    }

    static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    static final String MATCH_ALL = "%";

    private String normalizeSearch(String search) {

        if (search == null) {
            return MATCH_ALL;
        }

        String trimmed = search.trim();

        if (trimmed.isEmpty()) {
            return MATCH_ALL;
        }

        String escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped.toLowerCase() + "%";
    }

    private static final Set<String> VALID_STATUSES = Set.of(
            LoanStatus.CHECKING,
            LoanStatus.REJECTED_BY_MARKETING,
            LoanStatus.PENDING_BRANCH_MANAGER,
            LoanStatus.REJECTED_BY_BRANCH_MANAGER,
            LoanStatus.PENDING_BACK_OFFICE,
            LoanStatus.VERIFIED,
            LoanStatus.DISBURSED,
            LoanStatus.REJECTED_BY_BACK_OFFICE
    );

    LoanApplication getApplicationOrThrow(String applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("Loan application not found: " + applicationId));
    }

    User getUserWithRole(String userId, String expectedRoleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));

        String actualRole = user.getRole() != null ? user.getRole().getRoleName() : null;
        if (actualRole == null || !actualRole.equalsIgnoreCase(expectedRoleName)) {
            throw BusinessException.forbidden(
                    "User " + userId + " does not have the required role " + expectedRoleName);
        }
        return user;
    }

    private Integer requireBranch(User user) {
        if (user.getBranch() == null) {
            throw BusinessException.badRequest("User " + user.getUserId() + " has no branch assigned.");
        }
        return user.getBranch().getBranchId();
    }
}