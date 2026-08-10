package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class LoanDocumentResponse {
    private Integer documentId;
    private String applicationId;
    private String documentType;
    private String fileName;
    private String fileUrl;
    private LocalDate uploadedAt;
}
