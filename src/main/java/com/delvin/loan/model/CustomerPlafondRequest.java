package com.delvin.loan.model;

import com.delvin.loan.common.PlafondRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "customer_plafond_request")
public class CustomerPlafondRequest {

    @Id
    @Column(name = "request_id", nullable = false)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_plafond_id")
    private Plafond currentPlafond;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_plafond_id", nullable = false)
    private Plafond requestedPlafond;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlafondRequestStatus status;

    @Column(name = "request_date", nullable = false)
    private LocalDateTime requestDate;

    @Column(name = "decision_date")
    private LocalDateTime decisionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "notes")
    private String notes;

    /** The paperwork as it stood when this was filed - see PlafondRequestDocument. */
    @OneToMany(mappedBy = "request")
    private List<PlafondRequestDocument> documents;
}