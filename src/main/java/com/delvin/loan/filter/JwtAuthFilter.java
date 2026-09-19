package com.delvin.loan.filter;

import com.delvin.loan.exception.ApiErrorWriter;
import com.delvin.loan.service.AppUserDetailsService;
import com.delvin.loan.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter  extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private static final String TOKEN_INVALID =
            "Invalid or expired token";

    private final JwtService jwtService;
    private final AppUserDetailsService appUserDetailsService;
    private final ApiErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization =
                request.getHeader("Authorization");

        if (authorization == null ||
                !authorization.startsWith(PREFIX)) {

            filterChain.doFilter(request, response);
            return;
        }

        String token =
                authorization.substring(PREFIX.length());

        try {

            Claims claims =
                    jwtService.parse(token);

            String username =
                    claims.getSubject();

            if (username == null || username.isBlank()) {

                sendUnauthorized(
                        response,
                        TOKEN_INVALID
                );

                return;
            }

            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                UserDetails userDetails =
                        appUserDetailsService
                                .loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        claims
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(
                                authentication
                        );
            }

            filterChain.doFilter(
                    request,
                    response
            );

        } catch (JwtException |
                 IllegalArgumentException |
                 UsernameNotFoundException ex) {

            SecurityContextHolder.clearContext();

            sendUnauthorized(
                    response,
                    TOKEN_INVALID
            );
        }
    }

    private void sendUnauthorized(
            HttpServletResponse response,
            String message
    ) throws IOException {

        errorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                message
        );
    }
}
