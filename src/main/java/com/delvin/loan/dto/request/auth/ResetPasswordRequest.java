package com.delvin.loan.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @Email(message = "That email address is not valid")
    private String email;

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(
            min = 8,
            message = "Password must be at least 8 characters"
    )
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}