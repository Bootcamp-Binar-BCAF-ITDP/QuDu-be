package com.delvin.loan.dto.request.user;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRequest {

    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Boolean isActive;

    private Integer branchId;
    private Integer roleId;
}