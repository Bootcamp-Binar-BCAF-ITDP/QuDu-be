package com.delvin.loan.repository;

import com.delvin.loan.model.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {

    Page<Branch> findByIsActive(Boolean isActive, Pageable pageable);

    Optional<Branch> findByBranchIdAndIsActive(Integer branchId, Boolean isActive);

    Optional<Branch> findByBranchCodeAndIsActive(String branchCode, Boolean isActive);

}
