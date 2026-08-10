package com.delvin.loan.service;

import com.delvin.loan.common.CallStatus;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanDisbursementRequest;
import com.delvin.loan.dto.response.loanresp.LoanDisbursementResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDisbursement;
import com.delvin.loan.model.LoanVerification;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanDisbursementRepository;
import com.delvin.loan.repository.LoanVerificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class LoanDisbursementService {

    private final LoanDisbursementRepository disbursementRepository;
    private final LoanVerificationRepository verificationRepository;
    private final LoanApplicationService applicationService;
    private final LoanMapper mapper;

    public LoanDisbursementService(LoanDisbursementRepository disbursementRepository,
                                    LoanVerificationRepository verificationRepository,
                                    LoanApplicationService applicationService,
                                    LoanMapper mapper) {
        this.disbursementRepository = disbursementRepository;
        this.verificationRepository = verificationRepository;
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @Transactional
    public LoanDisbursementResponse disburse(LoanDisbursementRequest request) {
        User backOffice = applicationService.getUserWithRole(request.getBackOfficeUserId(), RoleName.BACK_OFFICE);
        LoanApplication application = applicationService.getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.VERIFIED.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId() + " is not ready for disbursement (current status: "
                            + application.getStatus() + "). It must have a successful verification call first.");
        }

        LoanVerification verification = verificationRepository
                .findFirstByApplication_ApplicationIdAndCallStatusOrderByVerificationDateDesc(
                        application.getApplicationId(), CallStatus.CAN_BE_CONTACTED)
                .orElseThrow(() -> BusinessException.conflict(
                        "No successful verification call on file for application " + application.getApplicationId()));

        LoanDisbursement disbursement = new LoanDisbursement();
        disbursement.setApplication(application);
        disbursement.setProcessedBy(backOffice);
        disbursement.setVerification(verification);
        disbursement.setDisbursedAmount(request.getDisbursedAmount() != null
                ? request.getDisbursedAmount()
                : application.getRequestedAmount());
        disbursement.setBankName(request.getBankName());
        disbursement.setAccountNumber(request.getAccountNumber());
        disbursement.setDisbursementDate(LocalDate.now());
        disbursement.setStatus(LoanStatus.DISBURSED);

        disbursementRepository.save(disbursement);
        application.setStatus(LoanStatus.DISBURSED);

        return mapper.toDisbursementResponse(disbursement);
    }

    public LoanDisbursementResponse getByApplication(String applicationId) {
        LoanDisbursement disbursement = disbursementRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> BusinessException.notFound("No disbursement found for application " + applicationId));
        return mapper.toDisbursementResponse(disbursement);
    }
}
