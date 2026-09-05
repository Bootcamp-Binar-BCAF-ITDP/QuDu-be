package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Integer> {

    List<LoanDocument> findByApplication_ApplicationId(String applicationId);

    Optional<LoanDocument> findByApplication_ApplicationIdAndDocumentType(String applicationId, String documentType);
}
