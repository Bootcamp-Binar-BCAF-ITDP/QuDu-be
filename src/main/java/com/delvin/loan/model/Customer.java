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
    @Column(name = "customer_id")
    private String customerId;

    @ManyToOne
    @JoinColumn(name = "plafond_id")
    private Plafond plafond;

    private String customerName;
    private String email;
    private String password;
    private String phoneNumber;
    private String nik;
    private String address;
    private String sex;
    private String birthPlace;
    private LocalDate birthDate;
    private String occupation;
    private String citizenship;

    @OneToMany(mappedBy = "customer")
    private List<LoanApplication> applications;
}