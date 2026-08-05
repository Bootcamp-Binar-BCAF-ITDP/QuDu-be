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
    @Column(name = "application_id")
    private String applicationId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private BigDecimal requestedAmount;
    private Integer tenor;
    private String purpose;
    private BigDecimal income;
    private String status;
    private LocalDate submissionDate;

    @OneToMany(mappedBy = "application")
    private List<LoanDocument> documents;

    @OneToOne(mappedBy = "application")
    private LoanReview review;

    @OneToOne(mappedBy = "application")
    private LoanDisbursement disbursement;
}