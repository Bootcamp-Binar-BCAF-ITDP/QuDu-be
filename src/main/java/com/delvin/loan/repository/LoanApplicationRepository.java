package com.delvin.loan.repository;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {

    Page<LoanApplication> findByStatus(String status, Pageable pageable);

    Page<LoanApplication> findByCustomer_CustomerId(String customerId, Pageable pageable);

    Page<LoanApplication> findByStatusAndReview_Marketing_Branch_BranchId(String status, Integer branchId, Pageable pageable);

}
