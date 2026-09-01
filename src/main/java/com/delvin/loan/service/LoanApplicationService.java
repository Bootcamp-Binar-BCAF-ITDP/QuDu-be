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
public class LoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final LoanMapper mapper;
    private final LoanDecisionRepository loanDecisionRepository;

    public LoanApplicationService(LoanApplicationRepository applicationRepository,
                                  CustomerRepository customerRepository,
                                  UserRepository userRepository,
                                  LoanMapper mapper, LoanDecisionRepository loanDecisionRepository) {
        this.applicationRepository = applicationRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.loanDecisionRepository = loanDecisionRepository;
    }

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

    @Transactional
    public LoanApplicationResponse branchManagerDecision(
            String branchManagerUserId,
            BranchManagerDecisionRequest request
    ) {

        User branchManager = getUserWithRole(
                branchManagerUserId,    
                RoleName.BRANCH_MANAGER
        );

        LoanApplication application = getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.PENDING_BRANCH_MANAGER.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application is not awaiting branch manager decision"
            );
        }

        if (loanDecisionRepository
                .existsByApplication_ApplicationId(application.getApplicationId())) {

            throw BusinessException.conflict(
                    "Application already has a branch manager decision"
            );
        }

        if (application.getReview() == null ||
                application.getReview().getMarketing() == null) {

            throw BusinessException.badRequest(
                    "Application does not have a valid marketing review"
            );
        }

        Integer applicationBranchId = requireBranch(application.getReview().getMarketing());

        Integer branchManagerBranchId = requireBranch(branchManager);

        if (!applicationBranchId.equals(branchManagerBranchId)) {
            throw BusinessException.forbidden(
                    "This application belongs to a different branch"
            );
        }

        LoanDecision decision = new LoanDecision();

        decision.setApplication(application);
        decision.setBranchManager(branchManager);

        decision.setDecision(
                Boolean.TRUE.equals(request.getApprove())
                        ? "APPROVED"
                        : "REJECTED"
        );

        decision.setDecisionNote(request.getNote());
        decision.setDecidedAt(LocalDate.now());

        loanDecisionRepository.save(decision);

        application.setStatus(
                Boolean.TRUE.equals(request.getApprove())
                        ? LoanStatus.PENDING_BACK_OFFICE
                        : LoanStatus.REJECTED_BY_BRANCH_MANAGER
        );

        applicationRepository.save(application);

        return mapper.toApplicationResponse(application);
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

        // Escape LIKE wildcards typed by the user so they are matched literally.
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