package com.delvin.loan.repository;

import com.delvin.loan.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, String> {

    List<Branch> findByIsActiveOrderByBranchIdAsc(Boolean isActive);

    Optional<Branch> findByBranchIdAndIsActive(Integer branchId, Boolean isActive);

    Optional<Branch> findByBranchCodeAndIsActive(String branchCode, Boolean isActive);

}
