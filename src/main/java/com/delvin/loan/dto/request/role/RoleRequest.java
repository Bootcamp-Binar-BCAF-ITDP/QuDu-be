package com.delvin.loan.dto.request.role;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleRequest {

    private String roleName;
    private String description;

    private List<Integer> menuIds;
}
