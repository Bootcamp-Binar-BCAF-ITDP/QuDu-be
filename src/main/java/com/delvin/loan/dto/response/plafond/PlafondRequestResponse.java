package com.delvin.loan.dto.response.plafond;

import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.model.CustomerPlafondRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlafondRequestResponse {

    private String requestId;
    private String customerId;
    private String customerName;

    private Integer previousLevel;
    private Integer requestedLevel;

    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;

    private PlafondRequestStatus status;
    private LocalDateTime requestDate;
    private LocalDateTime decisionDate;
    private String reviewedBy;
    private String notes;

    private PlafondResponse requestedPlafond;

    /** The paperwork the decision is meant to be made on. Never empty in practice. */
    private List<PlafondRequestDocumentResponse> documents;

    public static PlafondRequestResponse from(CustomerPlafondRequest request) {

        if (request == null) {
            return null;
        }

        return PlafondRequestResponse.builder()
                .requestId(request.getRequestId())
                .customerId(request.getCustomer().getCustomerId())
                .customerName(request.getCustomer().getCustomerName())
                .previousLevel(request.getCurrentPlafond() != null ? request.getCurrentPlafond().getLevel() : null)
                .requestedLevel(request.getRequestedPlafond().getLevel())
                .requestedAmount(request.getRequestedAmount())
                .approvedAmount(request.getApprovedAmount())
                .status(request.getStatus())
                .requestDate(request.getRequestDate())
                .decisionDate(request.getDecisionDate())
                .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getFullName() : null)
                .notes(request.getNotes())
                .requestedPlafond(PlafondResponse.from(request.getRequestedPlafond()))
                .documents(request.getDocuments() == null ? List.of()
                        : request.getDocuments().stream()
                                .map(PlafondRequestDocumentResponse::from)
                                .toList())
                .build();
    }
}