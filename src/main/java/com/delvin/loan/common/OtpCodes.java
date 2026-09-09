package com.delvin.loan.common;

import java.security.SecureRandom;

/**
 * Short numeric codes emailed to prove somebody holds an address.
 *
 * Two flows now need one - password reset and customer registration - so the
 * generation lives here rather than being copied. What must NOT be copied is the
 * storage policy: each caller decides its own expiry, uniqueness and attempt
 * rules, because those differ (a reset code is looked up globally, a
 * registration code only ever by the email it was issued for).
 */
public final class OtpCodes {

    private OtpCodes() {}

    /** Digits in every code the system emails. */
    public static final int LENGTH = 6;

    /** How long a code stays usable. */
    public static final int TTL_MINUTES = 15;

    /** Wrong guesses before a code is burned. */
    public static final int MAX_ATTEMPTS = 5;

    /**
     * Cryptographic, not {@code Random}: a predictable sequence would let one
     * code disclose the next, which for an account-taking flow is the whole
     * ballgame.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        return String.format("%0" + LENGTH + "d", RANDOM.nextInt(1_000_000));
    }
}
