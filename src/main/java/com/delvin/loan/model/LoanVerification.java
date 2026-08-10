package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "loan_verification")
public class LoanVerification {

    @Id
    @Column(name = "verification_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer verificationId;

    // NOTE: added - the original entity had no link back to the application
    // being verified, which is required so back office can list/retry calls
    // per application and so the disbursement step knows which verification
    // record to reference.
    @ManyToOne
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(nullable = false)
    private String callStatus;
    @Column(nullable = false)
    private String verificationNote;
    @Column(nullable = false)
    private LocalDate verificationDate;

    @OneToMany(mappedBy = "verification")
    private List<LoanDisbursement> disbursements;
}