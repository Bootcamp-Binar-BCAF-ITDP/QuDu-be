package com.delvin.loan.common;

import java.security.SecureRandom;

public final class OtpCodes {

    private OtpCodes() {}

    public static final int LENGTH = 6;

    public static final int TTL_MINUTES = 15;

    public static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        return String.format("%0" + LENGTH + "d", RANDOM.nextInt(1_000_000));
    }
}
