package com.delvin.loan.service;

import com.delvin.loan.common.CallStatus;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanDisbursementRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanDisbursementResponse;
import com.delvin.loan.event.LoanDisbursedEvent;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.*;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.LoanDisbursementRepository;
import com.delvin.loan.repository.LoanVerificationRepository;
import com.delvin.loan.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;

@Service
public class LoanDisbursementService {

    private final LoanDisbursementRepository disbursementRepository;
    private final LoanVerificationRepository verificationRepository;
    private final LoanApplicationService applicationService;
    private final LoanMapper mapper;
    private final LoanApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    private static final Logger log = LoggerFactory.getLogger(LoanDisbursementService.class);
    private final ApplicationEventPublisher events;

    public LoanDisbursementService(
            LoanDisbursementRepository disbursementRepository,
            LoanVerificationRepository verificationRepository,
            LoanApplicationService applicationService,
            LoanMapper mapper,
            UserRepository userRepository,
            LoanApplicationRepository applicationRepository, ApplicationEventPublisher events
    ) {
        this.disbursementRepository = disbursementRepository;
        this.verificationRepository = verificationRepository;
        this.applicationService = applicationService;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.events = events;
    }

    @Transactional
    public LoanDisbursementResponse disburse(
            String backOfficeUserId,
            LoanDisbursementRequest request
    ) {
        User backOffice = applicationService.getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);

        LoanApplication application = applicationService.getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.VERIFIED.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId()
                            + " is not ready for disbursement (current status: " + application.getStatus()
                            + "). It must have a successful verification call first.");
        }

        if (disbursementRepository.existsByApplication_ApplicationId(application.getApplicationId())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId() + " already has a disbursement decision");
        }

        LoanReview review = application.getReview();
        if (review == null || review.getMarketing() == null) {
            throw BusinessException.badRequest(
                    "Application " + application.getApplicationId() + " has no marketing review on file");
        }

        if (!requireBranch(review.getMarketing()).equals(requireBranch(backOffice))) {
            throw BusinessException.forbidden("This application belongs to a different branch");
        }

        LoanVerification verification = verificationRepository
                .findFirstByApplication_ApplicationIdAndCallStatusOrderByVerificationDateDesc(
                        application.getApplicationId(), CallStatus.CAN_BE_CONTACTED)
                .orElseThrow(() -> BusinessException.conflict(
                        "No successful verification call on file for application " + application.getApplicationId()));

        boolean approved = Boolean.TRUE.equals(request.getApprove());

        String note = request.getNote() == null ? null : request.getNote().trim();
        if (!approved && (note == null || note.isEmpty())) {
            throw BusinessException.badRequest("A note is required when a disbursement is rejected");
        }

        LoanDisbursement disbursement = new LoanDisbursement();
        disbursement.setApplication(application);
        disbursement.setProcessedBy(backOffice);
        disbursement.setVerification(verification);
        disbursement.setDecision(approved ? "APPROVED" : "REJECTED");
        disbursement.setDecisionNote(note);
        disbursement.setDisbursementDate(LocalDate.now());

        disbursement.setBankName(application.getBank());
        disbursement.setAccountNumber(application.getBankAccountNumber());
        disbursement.setAccountName(application.getBankAccountName());
        disbursement.setDisbursedAmount(approved ? application.getRequestedAmount() : null);

        disbursementRepository.save(disbursement);

        application.setStatus(approved ? LoanStatus.DISBURSED : LoanStatus.REJECTED_BY_BACK_OFFICE);
        applicationRepository.save(application);

        if (approved) {
            Customer customer = application.getCustomer();
            String email = customer == null ? null : customer.getEmail();

            if (email == null || email.isBlank()) {
                log.warn("Application {} was disbursed but the customer has no email on file",
                        application.getApplicationId());
            } else {
                events.publishEvent(new LoanDisbursedEvent(
                        application.getApplicationId(),
                        customer.getCustomerName(),
                        email,
                        disbursement.getDisbursedAmount(),
                        disbursement.getBankName(),
                        disbursement.getAccountNumber()
                ));
            }
        }

        return mapper.toDisbursementResponse(disbursement);
    }

    @Transactional
    public PageResponse<LoanApplicationResponse> getDisbursementBucketList (
            String backOfficeUserId, Pageable pageable) {
        User backOffice = getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);

        Integer branchId = requireBranch(backOffice);

        return PageResponse.of(applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                LoanStatus.VERIFIED, branchId, pageable),
        mapper::toApplicationResponse);
    }

    public LoanDisbursementResponse getByApplication(String applicationId) {
        LoanDisbursement disbursement = disbursementRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> BusinessException.notFound("No disbursement found for application " + applicationId));
        return mapper.toDisbursementResponse(disbursement);
    }

    // Helper
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
