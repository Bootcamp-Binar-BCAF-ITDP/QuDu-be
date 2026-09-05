package com.delvin.loan.repository;

import com.delvin.loan.model.Customer;
import com.delvin.loan.model.PasswordResetToken;
import com.delvin.loan.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByUser(User user);

    void deleteByUser(User user);

    Optional<PasswordResetToken> findByCustomer(Customer customer);

    void deleteByCustomer(Customer customer);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update PasswordResetToken t set t.attempts = t.attempts + 1 where t.id = :id")
    void incrementAttempts(@Param("id") Long id);
}