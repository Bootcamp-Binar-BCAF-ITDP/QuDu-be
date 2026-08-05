package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "loan_document")
public class LoanDocument {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    private String documentType;
    private String fileName;
    private String fileUrl;
    private LocalDate uploadedAt;
}
