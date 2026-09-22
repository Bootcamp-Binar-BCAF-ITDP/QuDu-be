package com.delvin.loan.dto.response.loanresp;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSummary {
    private String userId;
    private String username;
    private String fullName;
    private String roleName;
}
