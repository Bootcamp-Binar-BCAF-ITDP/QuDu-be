package com.delvin.loan.repository;

import com.delvin.loan.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRole_RoleNameIgnoreCaseAndBranch_BranchId(String roleName, Integer branchId);

    List<User> findByRole_RoleNameIgnoreCase(String roleName);
}