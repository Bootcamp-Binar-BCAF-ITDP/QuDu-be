package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "plafond")
public class Plafond {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plafond_id", nullable = false)
    private Integer plafondId;

    @Column(name = "level", nullable = false, unique = true)
    private Integer level;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "min_tenor", nullable = false)
    private Integer minTenor;

    @Column(name = "max_tenor", nullable = false)
    private Integer maxTenor;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "admin_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal adminFee;
}