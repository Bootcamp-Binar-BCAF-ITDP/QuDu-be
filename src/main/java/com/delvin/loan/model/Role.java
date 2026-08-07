package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "role_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer roleId;

    private String roleName;
    private String description;

    @OneToMany(mappedBy = "role")
    private List<UserRoles> userRoles;

    @OneToMany(mappedBy = "role")
    private List<RoleMenu> roleMenus = new ArrayList<>();
}
