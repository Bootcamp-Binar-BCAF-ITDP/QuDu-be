package com.delvin.loan.dto.response.branch;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchResponse {
    private Integer branchId;
    private String branchCode;
    private String branchName;
    private String location;
    private String email;
    private String phoneNumber;
    private Boolean isActive;
}
