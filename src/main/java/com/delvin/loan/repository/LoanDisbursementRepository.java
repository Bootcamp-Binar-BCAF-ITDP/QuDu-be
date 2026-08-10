package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDisbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanDisbursementRepository extends JpaRepository<LoanDisbursement, Integer> {

    Optional<LoanDisbursement> findByApplication_ApplicationId(String applicationId);
}
