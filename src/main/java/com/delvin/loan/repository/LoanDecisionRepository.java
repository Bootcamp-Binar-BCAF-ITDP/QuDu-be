package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanDecisionRepository extends JpaRepository<LoanDecision, Integer> {

    boolean existsByApplication_ApplicationId(String applicationId);

    Optional<LoanDecision> findByApplication_ApplicationId(String applicationId);
}
