package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String phoneNumber;
    private Boolean isActive;

    @OneToMany(mappedBy = "marketing")
    private List<LoanReview> reviews;

    @OneToMany(mappedBy = "verifiedBy")
    private List<LoanVerification> verifications;

    @OneToMany(mappedBy = "processedBy")
    private List<LoanDisbursement> disbursements;
}
