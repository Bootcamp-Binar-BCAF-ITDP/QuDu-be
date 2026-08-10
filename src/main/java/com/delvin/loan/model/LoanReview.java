package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "loan_review")
public class LoanReview {

    @Id
    @Column(name = "review_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reviewId;

    @OneToOne
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "marketing_id")
    private User marketing;

    @Column(nullable = false)
    private String recommendation;
    @Column(nullable = false)
    private String reviewNote;
    @Column(nullable = false)
    private LocalDate uploadedAt;
}