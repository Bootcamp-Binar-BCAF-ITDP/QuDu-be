package com.delvin.loan.repository;

import com.delvin.loan.model.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {


    Optional<Branch> findByBranchIdAndIsActive(Integer branchId, Boolean isActive);

    Optional<Branch> findByBranchCodeAndIsActive(String branchCode, Boolean isActive);

    @Query("""
            SELECT b FROM Branch b
            WHERE b.isActive = true
              AND (LOWER(b.branchCode)  LIKE :keyword
                OR LOWER(b.branchName)  LIKE :keyword
                OR LOWER(b.location)    LIKE :keyword
                OR LOWER(b.email)       LIKE :keyword
                OR LOWER(b.phoneNumber) LIKE :keyword)
            """)
    Page<Branch> searchActive(@Param("keyword") String keyword, Pageable pageable);

    List<Branch> findByIsActiveOrderByBranchNameAsc(Boolean isActive);

}
