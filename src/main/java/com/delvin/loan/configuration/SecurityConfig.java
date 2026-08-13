package com.delvin.loan.configuration;

import com.delvin.loan.common.RoleName;
import com.delvin.loan.controller.prop.SecurityRoutes;
import com.delvin.loan.filter.JwtAuthFilter;
import com.delvin.loan.service.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserDetailsService appUserDetailsService;

    @Value("${app.security.cors-allowed-origin}")
    private List<String> allowedOrigins;

    @Bean
    PasswordEncoder passwordEncoder() {

        String id = "bcrypt";

        Map<String, PasswordEncoder> encoders =
                Map.of(
                        id,
                        new BCryptPasswordEncoder(12)
                );

        return new DelegatingPasswordEncoder(
                id,
                encoders
        );
    }

    @Bean
    AuthenticationManager authenticationManager(
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        appUserDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder
        );

        return new ProviderManager(provider);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                allowedOrigins
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration
        );

        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http

                .csrf(csrf ->
                        csrf.disable()
                )

                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource()
                        )
                )

                .headers(headers ->
                        headers

                                .referrerPolicy(referrer ->
                                        referrer.policy(
                                                ReferrerPolicyHeaderWriter
                                                        .ReferrerPolicy
                                                        .NO_REFERRER
                                        )
                                )

                                .httpStrictTransportSecurity(hsts ->
                                        hsts
                                                .includeSubDomains(true)
                                                .maxAgeInSeconds(
                                                        31536000
                                                )
                                )

                                .frameOptions(frame ->
                                        frame.deny()
                                )
                )

                .authorizeHttpRequests(request -> request
                        .requestMatchers(SecurityRoutes.PUBLIC).permitAll()
                        .requestMatchers(SecurityRoutes.SUPERADMIN).hasAnyRole(RoleName.SUPERADMIN)
                        .requestMatchers(SecurityRoutes.BRANCH_MANAGER).hasAnyRole(RoleName.BRANCH_MANAGER)
                        .requestMatchers(SecurityRoutes.BACKOFFICE).hasAnyRole(RoleName.BACK_OFFICE)
                        .requestMatchers(SecurityRoutes.MARKETING).hasAnyRole(RoleName.MARKETING)

                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )
                )

                .formLogin(form ->
                        form.disable()
                )

                .httpBasic(basic ->
                        basic.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

}
