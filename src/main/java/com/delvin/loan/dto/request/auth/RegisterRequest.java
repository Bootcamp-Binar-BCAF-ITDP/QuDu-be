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

    @NotNull(message = "Account type wajib diisi")
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
