package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "role_id")
    private String roleId;

    private String roleName;
    private String description;

    @OneToMany(mappedBy = "role")
    private List<UserRoles> userRoles;

    @OneToMany(mappedBy = "role")
    private List<RoleMenu> roleMenus;
}
