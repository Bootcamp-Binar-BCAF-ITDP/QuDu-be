package com.delvin.loan.service;

import com.delvin.loan.dto.request.role.RoleRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.model.Menu;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.RoleMenu;
import com.delvin.loan.repository.MenuRepository;
import com.delvin.loan.repository.RoleMenuRepository;
import com.delvin.loan.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService{

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final RoleMenuRepository roleMenuRepository;

    public RoleService(RoleRepository roleRepository,
                       MenuRepository menuRepository,
                       RoleMenuRepository roleMenuRepository) {

        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
        this.roleMenuRepository = roleMenuRepository;
    }

    public List<RoleResponse> getAllRoles() {

        return roleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Integer id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        return toResponse(role);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {

        if (roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
            throw new RuntimeException("Role name already exists.");
        }

        Role role = new Role();
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());

        Role savedRole = roleRepository.save(role);

        for (Integer menuId : request.getMenuIds()) {

            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new RuntimeException("Menu not found"));

            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRole(savedRole);
            roleMenu.setMenu(menu);

            roleMenuRepository.save(roleMenu);
        }

        // re-fetch so roleMenus is populated before mapping to response
        savedRole = roleRepository.findById(savedRole.getRoleId())
                .orElseThrow();

        return toResponse(savedRole);
    }

    @Transactional
    public RoleResponse updateRole(Integer id, RoleRequest request) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());

        Role savedRole = roleRepository.save(role);

        roleMenuRepository.deleteByRole(savedRole);

        for (Integer menuId : request.getMenuIds()) {

            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new RuntimeException("Menu not found"));

            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRole(savedRole);
            roleMenu.setMenu(menu);

            roleMenuRepository.save(roleMenu);
        }

        savedRole = roleRepository.findById(savedRole.getRoleId())
                .orElseThrow();

        return toResponse(savedRole);
    }

    @Transactional
    public void deleteRole(Integer id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        roleMenuRepository.deleteByRole(role);

        roleRepository.delete(role);
    }

    private RoleResponse toResponse(Role role) {

        RoleResponse response = new RoleResponse();

        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());

        List<MenuResponse> menus = role.getRoleMenus()
                .stream()
                .map(roleMenu -> {

                    MenuResponse menuResponse = new MenuResponse();

                    menuResponse.setMenuId(roleMenu.getMenu().getMenuId());
                    menuResponse.setMenuName(roleMenu.getMenu().getMenuName());

                    return menuResponse;
                })
                .collect(Collectors.toList());

        response.setMenus(menus);

        return response;
    }
}
