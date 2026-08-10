package com.delvin.loan.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "menu")
public class Menu {

    @Id
    @Column(name = "menu_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer menuId;

    @Column(nullable = false)
    private String menuName;

    @OneToMany(mappedBy = "menu")
    private List<RoleMenu> roleMenus;
}
