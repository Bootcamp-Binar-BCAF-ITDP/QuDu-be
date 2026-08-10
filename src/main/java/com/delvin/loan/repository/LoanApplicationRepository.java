package com.delvin.loan.repository;

import com.delvin.loan.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {

    List<LoanApplication> findByStatus(String status);

    List<LoanApplication> findByCustomer_CustomerId(String customerId);

    List<LoanApplication> findByStatusAndReview_Marketing_Branch_BranchId(String status, Integer branchId);
}
