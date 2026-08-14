package com.delvin.loan.service;

import com.delvin.loan.model.AppCustomer;
import com.delvin.loan.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-ttl-minutes}") long ttlMinutes
    ) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "JWT secret minimal 32 karakter"
            );
        }

        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    // =========================
    // USER JWT
    // =========================
    public String issue(
            AppUser user,
            Instant issuedAt
    ) {

        return userBuilder(user, issuedAt)
                .expiration(
                        Date.from(issuedAt.plus(ttl))
                )
                .compact();
    }

    // =========================
    // CUSTOMER JWT
    // =========================
    public String issue(
            AppCustomer customer,
            Instant issuedAt
    ) {

        return customerBuilder(customer, issuedAt)
                .expiration(
                        Date.from(issuedAt.plus(ttl))
                )
                .compact();
    }

    // =========================
    // PARSE TOKEN
    // =========================
    public Claims parse(String token) {

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // =========================
    // USER TOKEN BUILDER
    // =========================
    private JwtBuilder userBuilder(
            AppUser user,
            Instant issuedAt
    ) {

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("accountType", "USER")
                .issuedAt(Date.from(issuedAt))
                .signWith(key);
    }

    // =========================
    // CUSTOMER TOKEN BUILDER
    // =========================
    private JwtBuilder customerBuilder(
            AppCustomer customer,
            Instant issuedAt
    ) {

        return Jwts.builder()
                .subject(customer.getEmail())
                .claim("customerId", customer.getCustomerId())
                .claim("username", customer.getEmail())
                .claim("role", "CUSTOMER")
                .claim("accountType", "CUSTOMER")
                .issuedAt(Date.from(issuedAt))
                .signWith(key);
    }
}