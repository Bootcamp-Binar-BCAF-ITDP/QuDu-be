package com.delvin.loan.controller;

import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.auth.LoginRequest;
import com.delvin.loan.dto.request.auth.RegisterRequest;
import com.delvin.loan.dto.response.auth.AuthResponse;
import com.delvin.loan.dto.response.auth.RegisterResponse;
import com.delvin.loan.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        RegisterResponse response =
                authService.register(request);

        return ResponseUtil.created(
                "User berhasil didaftarkan",
                null
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        AuthResponse response =
                authService.login(request);

        return ResponseEntity.ok(response);
    }
}
