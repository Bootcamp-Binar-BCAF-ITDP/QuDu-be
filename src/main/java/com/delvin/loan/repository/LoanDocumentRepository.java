package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Integer> {

    List<LoanDocument> findByApplication_ApplicationId(String applicationId);
}
