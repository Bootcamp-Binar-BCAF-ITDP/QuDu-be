package com.delvin.loan.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "That email address is not valid")
    private String email;
}
