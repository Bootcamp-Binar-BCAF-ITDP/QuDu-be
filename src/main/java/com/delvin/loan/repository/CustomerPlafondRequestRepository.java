package com.delvin.loan.repository;

import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.model.CustomerPlafondRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPlafondRequestRepository extends JpaRepository<CustomerPlafondRequest, String> {

    List<CustomerPlafondRequest> findByCustomer_CustomerIdOrderByRequestDateDesc(String customerId);

    Optional<CustomerPlafondRequest> findFirstByCustomer_CustomerIdOrderByRequestDateDesc(String customerId);

    boolean existsByCustomer_CustomerIdAndStatus(String customerId, PlafondRequestStatus status);

    /** Branch manager bucket. */
    Page<CustomerPlafondRequest> findByStatus(PlafondRequestStatus status, Pageable pageable);
}