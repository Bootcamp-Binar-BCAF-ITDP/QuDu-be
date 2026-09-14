package com.delvin.loan.repository;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {

    interface StatusCount {
        String getStatus();
        long getTotal();
    }

    interface DailyStatusCount {
        LocalDate getDay();
        String getStatus();
        long getTotal();
    }

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

    /**
     * One query for every combination of status, search term and date window.
     *
     * Deliberately carries no nullable parameters. The caller widens instead:
     * an empty status filter becomes every status, an empty search becomes the
     * wildcard "%", and an open-ended date becomes a sentinel far outside any
     * real submission date. That keeps the JPQL free of `:param IS NULL` checks,
     * which are the ones that fail on Postgres with "could not determine data
     * type of parameter" and cannot be caught by a mocked unit test.
     *
     * Applications with no submissionDate are therefore excluded. Every
     * application gets one at creation, and the dashboard's BETWEEN queries
     * already assume it.
     */
    @Query("""
            SELECT a FROM LoanApplication a
            LEFT JOIN a.customer c
            WHERE a.status IN :statuses
              AND a.submissionDate BETWEEN :from AND :to
              AND (LOWER(a.applicationId) LIKE :term
                OR LOWER(c.customerName)  LIKE :term
                OR LOWER(c.nik)           LIKE :term
                OR LOWER(a.purpose)       LIKE :term)
            """)
    Page<LoanApplication> filter(@Param("statuses") Collection<String> statuses,
                                 @Param("term") String term,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 Pageable pageable);

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

    @Query("""
            select a.status as status, count(a) as total
            from LoanApplication a
            where a.submissionDate between :from and :to
            group by a.status
            """)
    List<StatusCount> countByStatusBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a.submissionDate as day, a.status as status, count(a) as total
            from LoanApplication a
            where a.submissionDate between :from and :to
            group by a.submissionDate, a.status
            order by a.submissionDate
            """)
    List<DailyStatusCount> countByDayAndStatusBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

}
