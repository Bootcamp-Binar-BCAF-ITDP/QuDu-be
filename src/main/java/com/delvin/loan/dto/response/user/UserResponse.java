package com.delvin.loan.dto.response.user;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserResponse {

    private String userId;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Boolean isActive;

    private Integer branchId;
    private String branchName;

    private Integer roleId;
    private String roleName;
}