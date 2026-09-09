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

    /**
     * Everything still missing before a loan application or a limit increase may
     * be filed - identity papers and financial evidence alike.
     *
     * The app renders this rather than deciding for itself what a submission
     * needs, so the rule lives in one place: DocumentType.REQUIRED_FOR_SUBMISSION.
     */
    private List<String> missingForSubmission;

    /**
     * On file but past DocumentType.SUBMISSION_FRESHNESS_DAYS, so it has to be
     * uploaded again before the next submission. Only financial evidence ever
     * appears here.
     */
    private List<String> staleForSubmission;

    /** How old financial evidence may be, so the app can say so without hardcoding it. */
    private int submissionFreshnessDays;

    /** True when nothing is missing or stale, i.e. a submission would be accepted. */
    private boolean readyToSubmit;
}
