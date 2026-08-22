package com.delvin.loan.repository;

import com.delvin.loan.model.User;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Override
    @EntityGraph(attributePaths = {"branch", "role"})
    @NonNull
    Page<User> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"branch", "role"})
    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRole_RoleNameIgnoreCaseAndBranch_BranchId(String roleName, Integer branchId);

    List<User> findByRole_RoleNameIgnoreCase(String roleName);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}