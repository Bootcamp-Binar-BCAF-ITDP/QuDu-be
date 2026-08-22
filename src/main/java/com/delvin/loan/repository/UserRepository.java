package com.delvin.loan.repository;

import com.delvin.loan.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @EntityGraph(attributePaths = {"branch", "role"})
    @Query("""
            SELECT u FROM User u
            LEFT JOIN u.role r
            LEFT JOIN u.branch b
            WHERE (LOWER(u.username)    LIKE :keyword
                OR LOWER(u.email)       LIKE :keyword
                OR LOWER(u.fullName)    LIKE :keyword
                OR LOWER(u.phoneNumber) LIKE :keyword
                OR LOWER(r.roleName)    LIKE :keyword
                OR LOWER(b.branchName)  LIKE :keyword)
            """)
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRole_RoleNameIgnoreCaseAndBranch_BranchId(String roleName, Integer branchId);

    List<User> findByRole_RoleNameIgnoreCase(String roleName);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}