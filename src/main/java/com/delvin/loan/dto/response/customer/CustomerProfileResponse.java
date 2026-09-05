package com.delvin.loan.dto.response.customer;

import com.delvin.loan.dto.response.plafond.PlafondResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileResponse {

    private String customerId;
    private String customerName;
    private String email;
    private String phoneNumber;
    private String nik;
    private String address;
    private String sex;
    private String birthPlace;
    private LocalDate birthDate;
    private String occupation;
    private String citizenship;

    private BigDecimal approvedLimit;
    private BigDecimal usedLimit;
    private BigDecimal availableLimit;

    private PlafondResponse plafond;

    private List<CustomerDocumentResponse> documents;

    private boolean profileComplete;

    private List<String> missingDocuments;
}
