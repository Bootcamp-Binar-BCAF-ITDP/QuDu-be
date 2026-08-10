package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "customer")
public class Customer {

    @Id
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @ManyToOne
    @JoinColumn(name = "plafond_id")
    private Plafond plafond;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String nik;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String sex;

    @Column(nullable = false)
    private String birthPlace;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String occupation;

    @Column(nullable = false)
    private String citizenship;

    @OneToMany(mappedBy = "customer")
    private List<LoanApplication> applications;
}