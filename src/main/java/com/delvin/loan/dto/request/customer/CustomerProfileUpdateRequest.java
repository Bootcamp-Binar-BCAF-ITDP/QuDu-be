package com.delvin.loan.dto.request.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerProfileUpdateRequest {

    @NotBlank(message = "Nomor telepon wajib diisi")
    private String phoneNumber;

    @NotBlank(message = "Alamat wajib diisi")
    private String address;

    @NotBlank(message = "Pekerjaan wajib diisi")
    private String occupation;
}
