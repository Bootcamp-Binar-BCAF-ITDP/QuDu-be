package com.delvin.loan.dto.request.rolemenu;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleMenuRequest {

    private Integer roleId;
    private List<Integer> menuIds;

}