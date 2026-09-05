package com.delvin.loan.dto.request.device;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceTokenRequest {

    @NotBlank(message = "token is required")
    private String token;

    private String platform;
}
