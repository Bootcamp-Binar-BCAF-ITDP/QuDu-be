package com.delvin.loan.dto.response.role;

import com.delvin.loan.dto.response.menu.MenuResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleResponse {

    private Integer roleId;
    private String roleName;
    private String description;
    private List<MenuResponse> menus;

}