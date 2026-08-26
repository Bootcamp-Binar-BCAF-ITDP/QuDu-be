package com.delvin.loan.repository;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {

    Page<LoanApplication> findByStatus(String status, Pageable pageable);

    Page<LoanApplication> findByCustomer_CustomerId(String customerId, Pageable pageable);

    Page<LoanApplication> findByStatusAndReview_Marketing_Branch_BranchId(String status, Integer branchId, Pageable pageable);

    Page<LoanApplication> findByStatusIn(Collection<String> statuses, Pageable pageable);

    @Query("""
            SELECT a FROM LoanApplication a
            LEFT JOIN a.customer c
            WHERE LOWER(a.applicationId) LIKE :term
               OR LOWER(c.customerName)  LIKE :term
               OR LOWER(c.nik)           LIKE :term
               OR LOWER(a.purpose)       LIKE :term
            """)
    Page<LoanApplication> search(@Param("term") String term, Pageable pageable);

    @Query("""
            SELECT a FROM LoanApplication a
            LEFT JOIN a.customer c
            WHERE a.status IN :statuses
              AND (LOWER(a.applicationId) LIKE :term
                OR LOWER(c.customerName)  LIKE :term
                OR LOWER(c.nik)           LIKE :term
                OR LOWER(a.purpose)       LIKE :term)
            """)
    Page<LoanApplication> searchByStatusIn(@Param("statuses") Collection<String> statuses,
                                           @Param("term") String term,
                                           Pageable pageable);

}
