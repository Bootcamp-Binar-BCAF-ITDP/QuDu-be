package com.delvin.loan.dto.request.auth;

import com.delvin.loan.common.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegisterRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    // USER
    private String username;

    private Integer roleId;

    private Integer branchId;

    // USER + CUSTOMER
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    @NotBlank
    private String phoneNumber;

    /**
     * The code emailed by POST /api/auth/register/otp.
     *
     * Required for CUSTOMER and ignored for USER: staff accounts are created by
     * a superadmin who already controls the address, while a customer types
     * their own and has to prove they can read it. Enforced in AuthService
     * rather than here, because the requirement depends on accountType.
     */
    private String otp;

    // CUSTOMER ONLY
    private Integer plafondId;

    private String nik;

    private String address;

    private String sex;

    private String birthPlace;

    private LocalDate birthDate;

    private String occupation;

    private String citizenship;
}
