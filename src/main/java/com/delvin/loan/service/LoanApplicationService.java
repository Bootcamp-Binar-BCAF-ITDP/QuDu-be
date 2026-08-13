package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.*;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final LoanMapper mapper;

    public LoanApplicationService(LoanApplicationRepository applicationRepository,
                                   CustomerRepository customerRepository,
                                   UserRepository userRepository,
                                   LoanMapper mapper) {
        this.applicationRepository = applicationRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    /** Step 1: customer creates the application. Documents are uploaded separately. */
    @Transactional
    public LoanApplicationResponse createApplication(LoanApplicationCreateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + request.getCustomerId()));

        LoanApplication application = new LoanApplication();
        application.setApplicationId(generateApplicationId());
        application.setCustomer(customer);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTenor(request.getTenor());
        application.setPurpose(request.getPurpose());
        application.setIncome(request.getIncome());
        application.setStatus(LoanStatus.CHECKING);
        application.setSubmissionDate(LocalDate.now());

        applicationRepository.save(application);
        return mapper.toApplicationResponse(application);
    }

    public LoanApplicationResponse getApplication(String applicationId) {
        return mapper.toApplicationResponse(getApplicationOrThrow(applicationId));
    }

    public List<LoanApplicationResponse> listByCustomer(String customerId) {
        return applicationRepository.findByCustomer_CustomerId(customerId).stream()
                .map(mapper::toApplicationResponse)
                .collect(Collectors.toList());
    }

    /** Bucket for marketing: applications freshly submitted, awaiting review. */
    public List<LoanApplicationResponse> listMarketingBucket() {
        return applicationRepository.findByStatus(LoanStatus.CHECKING).stream()
                .map(mapper::toApplicationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Bucket for a branch manager: applications marketing accepted, scoped to
     * the branch of the marketing user who reviewed them (same branch the
     * branch manager belongs to).
     */
    public List<LoanApplicationResponse> listBranchManagerBucket(
            String branchManagerUserId
    ) {

        User branchManager = getUserWithRole(
                branchManagerUserId,
                RoleName.BRANCH_MANAGER
        );

        Integer branchId = requireBranch(branchManager);

        return applicationRepository
                .findByStatusAndReview_Marketing_Branch_BranchId(
                        LoanStatus.PENDING_BRANCH_MANAGER,
                        branchId
                )
                .stream()
                .map(mapper::toApplicationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Bucket for back office: applications the branch manager accepted,
     * scoped the same way (branch of the marketing user who originated it).
     */
    public List<LoanApplicationResponse> listBackOfficeBucket(String backOfficeUserId) {
        User backOffice = getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);
        Integer branchId = requireBranch(backOffice);
        return applicationRepository
                .findByStatusAndReview_Marketing_Branch_BranchId(LoanStatus.PENDING_BACK_OFFICE, branchId)
                .stream()
                .map(mapper::toApplicationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Step 3: branch manager approves or rejects an application marketing
     * already accepted. There is no dedicated table for this decision in the
     * current schema, so it's recorded directly as a status transition.
     */
    @Transactional
    public LoanApplicationResponse branchManagerDecision(
            String branchManagerUserId,
            BranchManagerDecisionRequest request
    ) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);

        LoanApplication application = getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.PENDING_BRANCH_MANAGER.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId()
                            + " is not awaiting branch manager decision "
                            + "(current status: " + application.getStatus() + ")"
            );
        }

        if (application.getReview() == null || application.getReview().getMarketing() == null) {
            throw BusinessException.badRequest(
                    "Application does not have a valid marketing review"
            );
        }

        Integer applicationBranchId = requireBranch(application.getReview().getMarketing());

        Integer branchManagerBranchId = requireBranch(branchManager);

        if (!applicationBranchId.equals(branchManagerBranchId)) {
            throw BusinessException.forbidden(
                    "This application belongs to a different branch."
            );
        }

        application.setStatus(Boolean.TRUE.equals(request.getApprove())
                        ? LoanStatus.PENDING_BACK_OFFICE
                        : LoanStatus.REJECTED_BY_BRANCH_MANAGER
        );

        applicationRepository.save(application);

        return mapper.toApplicationResponse(application);
    }

    // ---- internal helpers, also used by the other services ----
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

    private String generateApplicationId() {
        return "LA-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
