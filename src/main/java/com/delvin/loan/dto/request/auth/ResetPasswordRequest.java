package com.delvin.loan.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Token wajib diisi")
    private String token;

    @NotBlank(message = "Password baru wajib diisi")
    @Size(
            min = 8,
            message = "Password minimal 8 karakter"
    )
    private String newPassword;

    @NotBlank(message = "Konfirmasi password wajib diisi")
    private String confirmPassword;
}