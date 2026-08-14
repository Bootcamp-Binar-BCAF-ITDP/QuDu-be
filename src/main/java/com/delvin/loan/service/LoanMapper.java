package com.delvin.loan.service;

import com.delvin.loan.dto.response.loanresp.*;
import com.delvin.loan.model.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LoanMapper {

    public UserSummary toUserSummary(User user) {
        if (user == null) return null;
        return new UserSummary(
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().getRoleName() : null
        );
    }

    public CustomerSummary toCustomerSummary(Customer customer) {
        if (customer == null) return null;
        return new CustomerSummary(
                customer.getCustomerId(),
                customer.getCustomerName(),
                customer.getEmail(),
                customer.getPhoneNumber()
        );
    }

    public LoanDocumentResponse toDocumentResponse(LoanDocument doc) {
        if (doc == null) return null;
        return new LoanDocumentResponse(
                doc.getDocumentId(),
                doc.getApplication() != null ? doc.getApplication().getApplicationId() : null,
                doc.getDocumentType(),
                doc.getFileName(),
                doc.getFileUrl(),
                doc.getUploadedAt()
        );
    }

    public LoanReviewResponse toReviewResponse(LoanReview review) {
        if (review == null) return null;
        return new LoanReviewResponse(
                review.getReviewId(),
                review.getApplication() != null ? review.getApplication().getApplicationId() : null,
                toUserSummary(review.getMarketing()),
                review.getRecommendation(),
                review.getReviewNote(),
                review.getUploadedAt()
        );
    }

    public LoanVerificationResponse toVerificationResponse(LoanVerification verification) {
        if (verification == null) return null;
        return new LoanVerificationResponse(
                verification.getVerificationId(),
                verification.getApplication() != null ? verification.getApplication().getApplicationId() : null,
                toUserSummary(verification.getVerifiedBy()),
                verification.getCallStatus(),
                verification.getVerificationNote(),
                verification.getVerificationDate()
        );
    }

    public LoanDisbursementResponse toDisbursementResponse(LoanDisbursement disbursement) {
        if (disbursement == null) return null;
        return new LoanDisbursementResponse(
                disbursement.getDisburseId(),
                disbursement.getApplication() != null ? disbursement.getApplication().getApplicationId() : null,
                toUserSummary(disbursement.getProcessedBy()),
                disbursement.getVerification() != null ? disbursement.getVerification().getVerificationId() : null,
                disbursement.getDisbursedAmount(),
                disbursement.getBankName(),
                disbursement.getAccountNumber(),
                disbursement.getDisbursementDate(),
                disbursement.getStatus()
        );
    }

    public LoanDecisionResponse toDecisionResponse(
            LoanDecision decision
    ) {

        if (decision == null) {
            return null;
        }

        return new LoanDecisionResponse(
                decision.getDecisionId(),
                decision.getApplication() != null
                        ? decision.getApplication().getApplicationId()
                        : null,
                toUserSummary(decision.getBranchManager()),
                decision.getDecision(),
                decision.getDecisionNote(),
                decision.getDecidedAt()
        );
    }

    public LoanApplicationResponse toApplicationResponse(LoanApplication app) {
        if (app == null) return null;

        List<LoanDocumentResponse> documents = app.getDocuments() == null
                ? Collections.emptyList()
                : app.getDocuments().stream().map(this::toDocumentResponse).collect(Collectors.toList());

        List<LoanVerificationResponse> verifications = app.getVerifications() == null
                ? Collections.emptyList()
                : app.getVerifications().stream().map(this::toVerificationResponse).collect(Collectors.toList());

        return new LoanApplicationResponse(
                app.getApplicationId(),
                toCustomerSummary(app.getCustomer()),
                app.getRequestedAmount(),
                app.getTenor(),
                app.getPurpose(),
                app.getIncome(),
                app.getStatus(),
                app.getSubmissionDate(),
                documents,
                toReviewResponse(app.getReview()),
                toDecisionResponse(app.getBranchManagerDecision()),
                verifications,
                toDisbursementResponse(app.getDisbursement())
        );
    }
}
