package com.delvin.loan.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username atau email wajib diisi")
    private String usernameOrEmail;

    @NotBlank(message = "Password wajib diisi")
    private String password;
}
