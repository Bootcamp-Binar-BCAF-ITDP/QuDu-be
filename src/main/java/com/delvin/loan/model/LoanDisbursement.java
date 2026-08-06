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
    @Column(name = "disburse_id")
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

    private BigDecimal disbursedAmount;
    private String bankName;
    private String accountNumber;
    private LocalDate disbursementDate;
    private String status;
}