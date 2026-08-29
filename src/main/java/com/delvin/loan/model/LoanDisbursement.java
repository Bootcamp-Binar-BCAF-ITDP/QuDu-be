package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "loan_disbursement")
public class LoanDisbursement {

    @Id
    @Column(name = "disburse_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer disburseId;

    @OneToOne
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "processed_by", nullable = false)
    private User processedBy;

    @ManyToOne
    @JoinColumn(name = "verification_id")
    private LoanVerification verification;

    @Column(nullable = false)
    private String decision;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "disbursed_amount")
    private BigDecimal disbursedAmount;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;
}