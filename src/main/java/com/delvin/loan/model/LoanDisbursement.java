package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @ManyToOne
    @JoinColumn(name = "verification_id")
    private LoanVerification verification;

    @Column(nullable = false)
    private BigDecimal disbursedAmount;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private LocalDate disbursementDate;

    @Column(nullable = false)
    private String status;
}