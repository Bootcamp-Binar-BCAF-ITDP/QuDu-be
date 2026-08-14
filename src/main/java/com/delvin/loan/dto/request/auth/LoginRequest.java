package com.delvin.loan.dto.request.auth;

import com.delvin.loan.common.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username atau email wajib diisi")
    private String usernameOrEmail;

    @NotBlank(message = "Password wajib diisi")
    private String password;

    @NotNull(message = "Account type wajib diisi")
    private AccountType accountType;
}
