package com.delvin.loan.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    private String email;
}