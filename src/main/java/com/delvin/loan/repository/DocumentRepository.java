package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<LoanDocument, Integer> {

    List<LoanDocument> findAllByOrderByDocumentIdAsc();
}
