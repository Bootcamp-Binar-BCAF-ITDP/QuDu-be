package com.delvin.loan.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "role_menu")
public class RoleMenu {

    @Id
    @Column(name = "role_menu_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer roleMenuId;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne
    @JoinColumn(name = "menu_id")
    private Menu menu;
}
