package com.delvin.loan.repository;

import com.delvin.loan.model.PlafondRequestDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlafondRequestDocumentRepository extends JpaRepository<PlafondRequestDocument, Integer> {

    List<PlafondRequestDocument> findByRequest_RequestIdOrderByDocumentTypeAsc(String requestId);

    Optional<PlafondRequestDocument> findByDocumentIdAndRequest_RequestId(Integer documentId, String requestId);
}
