package com.delvin.loan.repository;

import com.delvin.loan.model.RegistrationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {

    Optional<RegistrationOtp> findByEmail(String email);

    void deleteByEmail(String email);

    /**
     * Counts a wrong code against the address.
     *
     * REQUIRES_NEW because the caller throws straight afterwards to reject the
     * attempt, and that rollback would otherwise take the increment with it -
     * leaving the counter stuck at zero and the six-digit code unmetered.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update RegistrationOtp o set o.attempts = o.attempts + 1 where o.id = :id")
    void incrementAttempts(@Param("id") Long id);
}
