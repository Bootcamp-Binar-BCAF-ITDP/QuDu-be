package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "loan_branch_manager_decision")
public class LoanDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Integer decisionId;

    @OneToOne
    @JoinColumn(
            name = "application_id",
            nullable = false,
            unique = true
    )
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(
            name = "branch_manager_id",
            nullable = false
    )
    private User branchManager;

    @Column(nullable = false)
    private String decision;

    private String decisionNote;

    @Column(nullable = false)
    private LocalDate decidedAt;
}