package com.delvin.loan.repository;

import com.delvin.loan.model.LoanVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanVerificationRepository extends JpaRepository<LoanVerification, Integer> {

    List<LoanVerification> findByApplication_ApplicationIdOrderByVerificationDateDesc(String applicationId);

    Optional<LoanVerification> findFirstByApplication_ApplicationIdAndCallStatusOrderByVerificationDateDesc(
            String applicationId, String callStatus);
}
