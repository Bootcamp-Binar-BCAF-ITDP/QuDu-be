package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "plafond")
public class Plafond {

    @Id
    @Column(name = "plafond_id")
    private String plafondId;

    private String productName;
    private BigDecimal minimumAmount;
    private BigDecimal maxAmount;
    private BigDecimal interestRate;
    private Integer minTenor;
    private Integer maxTenor;
    private BigDecimal adminFee;
    private Boolean isActive;
    private String description;

    @OneToMany(mappedBy = "plafond")
    private List<Customer> customers;
}