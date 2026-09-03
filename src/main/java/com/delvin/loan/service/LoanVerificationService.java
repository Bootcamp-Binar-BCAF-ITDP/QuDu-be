package com.delvin.loan.service;

import com.delvin.loan.common.CallStatus;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanVerificationRequest;
import com.delvin.loan.dto.response.loanresp.LoanVerificationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanVerification;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanVerificationService {

    private final LoanVerificationRepository verificationRepository;
    private final LoanApplicationService applicationService;
    private final LoanMapper mapper;

    @Transactional
    public LoanVerificationResponse submitVerification(String backOfficeUserId, LoanVerificationRequest request) {
        String callStatus = normalizeCallStatus(request.getCallStatus());

        User backOffice = applicationService.getUserWithRole(backOfficeUserId, RoleName.BACK_OFFICE);
        LoanApplication application = applicationService.getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.PENDING_BACK_OFFICE.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId() + " is not awaiting back office verification (current status: "
                            + application.getStatus() + ")");
        }

        LoanVerification verification = new LoanVerification();
        verification.setApplication(application);
        verification.setVerifiedBy(backOffice);
        verification.setCallStatus(callStatus);
        verification.setVerificationNote(request.getVerificationNote());
        verification.setVerificationDate(LocalDate.now());
        verificationRepository.save(verification);

        if (CallStatus.CAN_BE_CONTACTED.equals(callStatus)) {
            application.setStatus(LoanStatus.VERIFIED);
        }
        // otherwise the application stays in PENDING_BACK_OFFICE so back office can re-attempt the call

        return mapper.toVerificationResponse(verification);
    }

    public List<LoanVerificationResponse> listByApplication(String applicationId) {
        return verificationRepository.findByApplication_ApplicationIdOrderByVerificationDateDesc(applicationId).stream()
                .map(mapper::toVerificationResponse)
                .collect(Collectors.toList());
    }

    private String normalizeCallStatus(String callStatus) {
        if (callStatus == null) {
            throw BusinessException.badRequest("callStatus is required");
        }
        if (callStatus.equalsIgnoreCase(CallStatus.CAN_BE_CONTACTED)) return CallStatus.CAN_BE_CONTACTED;
        if (callStatus.equalsIgnoreCase(CallStatus.NADA_SAMBUNG_TIDAK_DIANGKAT)) return CallStatus.NADA_SAMBUNG_TIDAK_DIANGKAT;
        if (callStatus.equalsIgnoreCase(CallStatus.SALAH_SAMBUNG)) return CallStatus.SALAH_SAMBUNG;
        throw BusinessException.badRequest("callStatus must be one of: "
                + CallStatus.CAN_BE_CONTACTED + ", " + CallStatus.NADA_SAMBUNG_TIDAK_DIANGKAT + ", " + CallStatus.SALAH_SAMBUNG);
    }
}
