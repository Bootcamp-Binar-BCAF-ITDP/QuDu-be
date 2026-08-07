package com.delvin.loan.repository;

import com.delvin.loan.model.Role;
import com.delvin.loan.model.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, Integer> {

    List<RoleMenu> findByRole(Role role);

    void deleteByRole(Role role);

}