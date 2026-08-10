package com.delvin.loan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "branch")
public class Branch {

    @Id
    @Column(name = "branch_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer branchId;

    @Column(nullable = false)
    private String branchCode;

    @Column(nullable = false)
    private String branchName;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private Boolean isActive;

    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<User> users;
}
