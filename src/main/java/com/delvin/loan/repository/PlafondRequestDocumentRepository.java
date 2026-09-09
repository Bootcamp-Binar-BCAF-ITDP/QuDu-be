package com.delvin.loan.repository;

import com.delvin.loan.model.PlafondRequestDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlafondRequestDocumentRepository extends JpaRepository<PlafondRequestDocument, Integer> {

    List<PlafondRequestDocument> findByRequest_RequestIdOrderByDocumentTypeAsc(String requestId);

    /**
     * Scoped by request as well as id: document ids are a plain sequence, so
     * without the request in the lookup a branch manager could walk them into
     * paperwork belonging to a customer they were never given.
     */
    Optional<PlafondRequestDocument> findByDocumentIdAndRequest_RequestId(Integer documentId, String requestId);
}
