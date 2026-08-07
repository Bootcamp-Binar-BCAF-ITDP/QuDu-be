package com.delvin.loan.dto.response.rolemenu;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleMenuResponse {

    private Integer roleMenuId;
    private Integer roleId;
    private String roleName;

    private Integer menuId;
    private String menuName;

}