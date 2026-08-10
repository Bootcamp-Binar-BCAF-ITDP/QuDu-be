package com.delvin.loan.repository;

import com.delvin.loan.model.LoanReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanReviewRepository extends JpaRepository<LoanReview, Integer> {

    Optional<LoanReview> findByApplication_ApplicationId(String applicationId);

    boolean existsByApplication_ApplicationId(String applicationId);
}
