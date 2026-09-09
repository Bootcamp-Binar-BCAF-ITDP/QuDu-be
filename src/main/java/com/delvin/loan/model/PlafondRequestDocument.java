package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A copy of the customer's paperwork as it stood when a limit increase was
 * filed, so the branch manager decides on what was actually submitted.
 *
 * A copy rather than a live reference for the same reason
 * {@link LoanDocument} snapshots them: replacing a payslip next month must not
 * quietly rewrite the evidence behind a decision already made. The file on disk
 * is shared with the customer's row - only the pointer is duplicated.
 */
@Entity
@Getter
@Setter
@Table(
        name = "plafond_request_document",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_plafond_request_document_type",
                columnNames = {"request_id", "document_type"}
        )
)
public class PlafondRequestDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id", nullable = false)
    private Integer documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private CustomerPlafondRequest request;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
