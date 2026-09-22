package com.delvin.loan.service;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.evict.EvictsApplicationCaches;
import org.springframework.cache.annotation.Cacheable;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.BranchManagerDecisionRequest;
import com.delvin.loan.dto.request.plafond.PlafondDecisionRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.event.LoanStatusChangedEvent;
import com.delvin.loan.event.PlafondDecisionEvent;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.*;
import com.delvin.loan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BranchManagerService {

    private final CustomerPlafondRequestRepository plafondRequestRepository;
    private final PlafondRequestDocumentRepository plafondRequestDocumentRepository;
    private final DocumentStorageService documentStorage;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PlafondService plafondService;
    private final LoanApplicationRepository applicationRepository;
    private final LoanDecisionRepository loanDecisionRepository;
    private final LoanMapper mapper;
    private final ApplicationEventPublisher events;
    private final BranchRouting branchRouting;

    public PageResponse<LoanApplicationResponse> listBranchManagerBucket(
            String branchManagerUserId, Pageable pageable) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);
        Integer branchId = requireBranch(branchManager);

        return PageResponse.of(
                applicationRepository.findBucket(
                        LoanStatus.PENDING_BRANCH_MANAGER, branchId, pageable),
                mapper::toApplicationResponse);
    }

    @EvictsApplicationCaches
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

        branchRouting.requireSameBranch(branchManager, application);

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

        boolean approved = Boolean.TRUE.equals(request.getApprove());

        application.setStatus(
                approved
                        ? LoanStatus.PENDING_BACK_OFFICE
                        : LoanStatus.REJECTED_BY_BRANCH_MANAGER
        );

        applicationRepository.save(application);

        if (!approved) {
            events.publishEvent(LoanStatusChangedEvent.of(
                    application,
                    decision.getDecisionNote(),
                    application.getRequestedAmount()
            ));
        }

        return mapper.toApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlafondRequestResponse> listPlafondRequestBucket(
            String branchManagerUserId, Pageable pageable) {

        User branchManager = getUserWithRole(branchManagerUserId, RoleName.BRANCH_MANAGER);

        Page<CustomerPlafondRequest> page = plafondRequestRepository.findBucket(
                PlafondRequestStatus.PENDING, requireBranch(branchManager), pageable);

        return PageResponse.of(page, PlafondRequestResponse::from);
    }

    @EvictsApplicationCaches
    @Transactional
    public PlafondRequestResponse decidePlafondRequest(String userId,
                                                       String requestId,
                                                       PlafondDecisionRequest decision) {

        CustomerPlafondRequest request = findPending(requestId);

        requireSameBranch(getUserWithRole(userId, RoleName.BRANCH_MANAGER), request);

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

        CustomerPlafondRequest decided = plafondRequestRepository.save(request);

        events.publishEvent(PlafondDecisionEvent.of(decided));

        return PlafondRequestResponse.from(decided);
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

    @Transactional(readOnly = true)
    public StoredPlafondDocument loadDocumentContent(String requestId, Integer documentId) {

        PlafondRequestDocument document = plafondRequestDocumentRepository
                .findByDocumentIdAndRequest_RequestId(documentId, requestId)
                .orElseThrow(() -> BusinessException.notFound("Document not found: " + documentId));

        Path path = documentStorage.resolveStored(document.getFileUrl());

        return new StoredPlafondDocument(
                path,
                documentStorage.contentTypeOf(path),
                document.getFileName() == null ? path.getFileName().toString() : document.getFileName());
    }

    public record StoredPlafondDocument(Path path, MediaType contentType, String fileName) {}

    private void requireSameBranch(User branchManager, CustomerPlafondRequest request) {

        Integer requestBranchId = null;

        if (request.getBranch() != null) {
            requestBranchId = request.getBranch().getBranchId();
        } else if (request.getCustomer() != null && request.getCustomer().getBranch() != null) {
            requestBranchId = request.getCustomer().getBranch().getBranchId();
        }

        if (requestBranchId == null) {
            throw BusinessException.badRequest(
                    "Request " + request.getRequestId() + " has no branch. The customer registered"
                            + " before branch routing existed; assign them a branch first.");
        }

        if (!requestBranchId.equals(requireBranch(branchManager))) {
            throw BusinessException.forbidden("This request belongs to a different branch");
        }
    }

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