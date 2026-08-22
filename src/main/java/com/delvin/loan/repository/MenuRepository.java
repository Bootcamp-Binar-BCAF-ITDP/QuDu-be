package com.delvin.loan.repository;

import com.delvin.loan.model.Menu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {

    Page<Menu> findByMenuNameContainingIgnoreCase(String menuName, Pageable pageable);

    List<Menu> findAllByOrderByMenuNameAsc();
}
