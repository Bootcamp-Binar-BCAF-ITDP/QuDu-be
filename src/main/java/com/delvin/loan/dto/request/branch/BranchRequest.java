package com.delvin.loan.dto.request.branch;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchRequest {
    private String branchCode;
    private String branchName;
    private String location;
    private String email;
    private String phoneNumber;
    private Boolean isActive;
}
