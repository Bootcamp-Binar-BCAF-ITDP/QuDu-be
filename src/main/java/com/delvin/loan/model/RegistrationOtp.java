package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Proof that whoever is registering can read the address they typed.
 *
 * Keyed by email and nothing else, because at this point in the flow there is no
 * account to hang it off - that is the whole difference from
 * {@link PasswordResetToken}, which always belongs to an existing user or
 * customer. One live row per address: requesting again replaces the previous
 * one, so an abandoned attempt cannot be used later.
 */
@Entity
@Getter
@Setter
@Table(
        name = "registration_otp",
        uniqueConstraints = @UniqueConstraint(name = "uk_registration_otp_email", columnNames = "email")
)
public class RegistrationOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    /**
     * Wrong codes entered against this address.
     *
     * Six digits is a million guesses, which is minutes of scripting. Burning
     * the code after a handful of misses is what makes one this short safe to
     * type - see OtpCodes.MAX_ATTEMPTS.
     */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
