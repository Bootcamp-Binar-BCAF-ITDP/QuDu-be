package com.delvin.loan.repository;

import com.delvin.loan.model.Plafond;
import com.delvin.loan.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlafondRepository extends JpaRepository<Plafond, Integer> {

    Optional<Plafond> findByLevel(Integer level);

    boolean existsByLevel(Integer level);

    List<Plafond> findAllByIsActiveTrueOrderByLevelAsc();

    Optional<Plafond> findFirstByMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByLevelAsc(
            BigDecimal minimumAmount, BigDecimal maxAmount);

    List<Plafond> findByMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(
            BigDecimal maxAmount, BigDecimal minimumAmount);

    List<Plafond> findAllByOrderByLevelAsc();

    Optional<Plafond> findFirstByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByLevelAsc(
            BigDecimal minimumAmount, BigDecimal maxAmount);

    List<Plafond> findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(
            BigDecimal maxAmount, BigDecimal minimumAmount);

    @Query("""
            SELECT p FROM Plafond p
            WHERE LOWER(p.description)    LIKE :keyword
            """)
    Page<Plafond> search(@Param("keyword") String keyword, Pageable pageable);
}