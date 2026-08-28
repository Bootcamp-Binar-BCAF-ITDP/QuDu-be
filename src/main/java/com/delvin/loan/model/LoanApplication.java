package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private BigDecimal requestedAmount;

    @Column(nullable = false)
    private Integer tenor;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private BigDecimal income;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDate submissionDate;

    @Column(nullable = false, name = "bank_account_number")
    private String bankAccountNumber;

    @Column(nullable = false, name = "bank_account_name")
    private String bankAccountName;

    @Column(nullable = false, name = "bank")
    private String bank;

    @OneToMany(mappedBy = "application")
    private List<LoanDocument> documents;

    @OneToOne(mappedBy = "application")
    private LoanReview review;

    @OneToOne(mappedBy = "application")
    private LoanDecision branchManagerDecision;

    @OneToMany(mappedBy = "application")
    private List<LoanVerification> verifications;

    @OneToOne(mappedBy = "application")
    private LoanDisbursement disbursement;
}