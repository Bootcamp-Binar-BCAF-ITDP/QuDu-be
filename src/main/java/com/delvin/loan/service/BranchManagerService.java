package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.plafond.PlafondDecisionRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.*;
import com.delvin.loan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BranchManagerService {

    private final CustomerPlafondRequestRepository plafondRequestRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PlafondService plafondService;
    private final LoanApplicationRepository applicationRepository;
    private final LoanDecisionRepository loanDecisionRepository;
    private final LoanMapper mapper;

    // APPLICATION
    public PageResponse<LoanApplicationResponse> listBranchManagerBucket(
            String branchManagerUserId, Pageable pageable) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);
        Integer branchId = requireBranch(branchManager);

        return PageResponse.of(
                applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                        LoanStatus.PENDING_BRANCH_MANAGER, branchId, pageable),
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

    // PLAFOND
    @Transactional(readOnly = true)
    public PageResponse<PlafondRequestResponse> listPlafondRequestBucket(Pageable pageable) {

        Page<CustomerPlafondRequest> page =
                plafondRequestRepository.findByStatus(PlafondRequestStatus.PENDING, pageable);

        return PageResponse.of(page, PlafondRequestResponse::from);
    }

    @Transactional
    public PlafondRequestResponse decidePlafondRequest(String userId,
                                                       String requestId,
                                                       PlafondDecisionRequest decision) {

        CustomerPlafondRequest request = findPending(requestId);

        if (decision.getDecision() != PlafondRequestStatus.APPROVED
                && decision.getDecision() != PlafondRequestStatus.REJECTED) {
            throw BusinessException.badRequest("decision must be APPROVED or REJECTED");
        }

        if (decision.getDecision() == PlafondRequestStatus.APPROVED) {
            approve(request, decision);
        }

        request.setStatus(decision.getDecision());
        request.setDecisionDate(LocalDateTime.now());
        request.setReviewedBy(findUser(userId));
        request.setNotes(decision.getNotes());

        return PlafondRequestResponse.from(plafondRequestRepository.save(request));
    }

    private void approve(CustomerPlafondRequest request, PlafondDecisionRequest decision) {

        BigDecimal approvedAmount = decision.getApprovedAmount() != null
                ? decision.getApprovedAmount()
                : request.getRequestedAmount();

        if (approvedAmount.compareTo(request.getRequestedAmount()) > 0) {
            throw BusinessException.badRequest(
                    "Approved amount cannot be higher than the requested amount of "
                            + request.getRequestedAmount());
        }

        Plafond granted = plafondService.resolveByAmount(approvedAmount);

        Customer customer = request.getCustomer();
        customer.setPlafond(granted);
        customer.setApprovedLimit(approvedAmount);
        customerRepository.save(customer);

        request.setRequestedPlafond(granted);
        request.setApprovedAmount(approvedAmount);
    }

    // HELPER
    private CustomerPlafondRequest findPending(String requestId) {

        CustomerPlafondRequest request = plafondRequestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("Plafond request not found: " + requestId));

        if (request.getStatus() != PlafondRequestStatus.PENDING) {
            throw BusinessException.badRequest(
                    "Request " + requestId + " has already been " + request.getStatus());
        }

        return request;
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
    }

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