package com.delvin.loan.repository;

import com.delvin.loan.model.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, Integer> {

    List<CustomerDocument> findByCustomer_CustomerIdOrderByDocumentTypeAsc(String customerId);

    Optional<CustomerDocument> findByCustomer_CustomerIdAndDocumentType(String customerId, String documentType);
}
