package com.delvin.loan.service;

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

    @Transactional(readOnly = true)
    public PageResponse<LoanApplicationResponse> getAllApplication(
            List<String> statuses, String search, Pageable pageable) {

        List<String> filters = normalizeStatuses(statuses);
        String term = normalizeSearch(search);

        Page<LoanApplication> applications;

        if (filters.isEmpty() && term == null) {
            applications = applicationRepository.findAll(pageable);

        } else if (term == null) {
            applications = applicationRepository.findByStatusIn(filters, pageable);

        } else if (filters.isEmpty()) {
            applications = applicationRepository.search(term, pageable);

        } else {
            applications = applicationRepository.searchByStatusIn(filters, term, pageable);
        }

        return PageResponse.of(applications, mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> getMyBucket(
            String userId, String roleName, Pageable pageable) {

        if (roleName == null || roleName.isBlank()) {
            throw BusinessException.forbidden("User has no role assigned");
        }

        return switch (roleName.trim().toUpperCase()) {
            case RoleName.MARKETING      -> listMarketingBucket(pageable);
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

    public PageResponse<LoanApplicationResponse> listByCustomer(String customerId, Pageable pageable) {
        return PageResponse.of(
                applicationRepository.findByCustomer_CustomerId(customerId, pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listMarketingBucket(Pageable pageable) {
        return PageResponse.of(
                applicationRepository.findByStatus(LoanStatus.CHECKING, pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listBranchManagerBucket(
            String branchManagerUserId, Pageable pageable) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);
        Integer branchId = requireBranch(branchManager);

        return PageResponse.of(
                applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                        LoanStatus.PENDING_BRANCH_MANAGER, branchId, pageable),
                mapper::toApplicationResponse);
    }

    public PageResponse<LoanApplicationResponse> listBackOfficeBucket(
            String backOfficeUserId, Pageable pageable) {

        User backOffice = getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);
        Integer branchId = requireBranch(backOffice);

        return PageResponse.of(
                applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                        LoanStatus.PENDING_BACK_OFFICE, branchId, pageable),
                mapper::toApplicationResponse);
    }

    // ---- internal helpers ----
    private String normalizeSearch(String search) {

        if (search == null) {
            return null;
        }

        String trimmed = search.trim();

        if (trimmed.isEmpty()) {
            return null;
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