package com.delvin.loan.repository;

import com.delvin.loan.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    @Query("""
            SELECT r FROM Role r
            WHERE LOWER(r.roleName)    LIKE :keyword
               OR LOWER(r.description) LIKE :keyword
            """)
    Page<Role> search(@Param("keyword") String keyword, Pageable pageable);

    /** Unpaginated list for dropdowns. */
    List<Role> findAllByOrderByRoleNameAsc();

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);
}