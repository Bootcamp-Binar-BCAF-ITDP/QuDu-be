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
    @Column(name = "user_id")
    private String userId;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private String username;
    private String email;
    private String password;
    private String fullName;
    private String phoneNumber;
    private Boolean isActive;

    @OneToMany(mappedBy = "user")
    private List<UserRoles> userRoles;

    @OneToMany(mappedBy = "marketing")
    private List<LoanReview> reviews;

    @OneToMany(mappedBy = "verifiedBy")
    private List<LoanVerification> verifications;

    @OneToMany(mappedBy = "processedBy")
    private List<LoanDisbursement> disbursements;
}
