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
    @Column(name = "verification_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer verificationId;

    @ManyToOne
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    private String callStatus;
    private String verificationNote;
    private LocalDate verificationDate;

    @OneToMany(mappedBy = "verification")
    private List<LoanDisbursement> disbursements;
}