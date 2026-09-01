package com.delvin.loan.repository;

import com.delvin.loan.model.LoanDisbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface LoanDisbursementRepository extends JpaRepository<LoanDisbursement, Integer> {

    Optional<LoanDisbursement> findByApplication_ApplicationId(String applicationId);

    boolean existsByApplication_ApplicationId(String applicationId);

    @Query("""
            select coalesce(sum(d.disbursedAmount), 0)
            from LoanDisbursement d
            where d.decision = 'APPROVED'
              and d.disbursementDate between :from and :to
            """)
    BigDecimal sumDisbursedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
