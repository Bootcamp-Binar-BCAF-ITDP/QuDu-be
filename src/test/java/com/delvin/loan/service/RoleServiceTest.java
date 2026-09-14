package com.delvin.loan.service;

import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.role.RoleRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.model.Menu;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.RoleMenu;
import com.delvin.loan.repository.MenuRepository;
import com.delvin.loan.repository.RoleMenuRepository;
import com.delvin.loan.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private RoleMenuRepository roleMenuRepository;

    @InjectMocks
    private RoleService service;

    private RoleRequest request;

    @BeforeEach
    void setUp() {
        request = new RoleRequest();
        request.setRoleName(RoleName.MARKETING);
        request.setDescription("Reviews incoming applications");
        request.setMenuIds(List.of(1, 2));
    }

    private Menu menu(int id, String name) {
        Menu menu = new Menu();
        menu.setMenuId(id);
        menu.setMenuName(name);
        return menu;
    }

    private Role role(int id, String name, Menu... menus) {
        Role role = new Role();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setDescription("desc");

        List<RoleMenu> links = new ArrayList<>();
        for (Menu menu : menus) {
            RoleMenu link = new RoleMenu();
            link.setRole(role);
            link.setMenu(menu);
            links.add(link);
        }
        role.setRoleMenus(links);
        return role;
    }

    @Test
    @DisplayName("a blank search becomes a match-everything keyword")
    void blankSearchMatchesEverything() {
        when(roleRepository.search(any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(role(1, RoleName.MARKETING)), PAGE, 1));

        service.getAllRoles(null, PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(roleRepository).search(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%%");
    }

    @Test
    @DisplayName("a search term is trimmed, lowercased and wrapped for LIKE")
    void searchTermIsNormalised() {
        when(roleRepository.search(any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.getAllRoles("  MarKeting ", PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(roleRepository).search(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%marketing%");
    }

    @Test
    @DisplayName("a role carries its menus, which is what drives the frontend sidebar")
    void roleCarriesItsMenus() {
        Role role = role(1, RoleName.MARKETING, menu(1, "Dashboard"), menu(2, "Applications"));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));

        RoleResponse response = service.getRoleById(1);

        assertThat(response.getMenus())
                .extracting(MenuResponse::getMenuName)
                .containsExactly("Dashboard", "Applications");
    }

    @Test
    @DisplayName("options omit the menus, because a dropdown does not need them")
    void optionsOmitMenus() {
        when(roleRepository.findAllByOrderByRoleNameAsc())
                .thenReturn(List.of(role(1, RoleName.BACK_OFFICE), role(2, RoleName.MARKETING)));

        assertThat(service.getRoleOptions())
                .extracting(RoleResponse::getRoleName)
                .containsExactly(RoleName.BACK_OFFICE, RoleName.MARKETING);

        assertThat(service.getRoleOptions()).allSatisfy(r -> assertThat(r.getMenus()).isNull());
    }

    @Test
    @DisplayName("an unknown role id is refused")
    void unknownRoleIdIsRefused() {
        when(roleRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoleById(404))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role not found");
    }

    @Test
    @DisplayName("a duplicate role name is refused before anything is saved")
    void refusesDuplicateRoleName() {
        when(roleRepository.findByRoleName(RoleName.MARKETING))
                .thenReturn(Optional.of(role(1, RoleName.MARKETING)));

        assertThatThrownBy(() -> service.createRole(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role name already exists.");

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("creating a role links every requested menu")
    void createLinksEveryMenu() {
        Role saved = role(1, RoleName.MARKETING, menu(1, "Dashboard"), menu(2, "Applications"));

        when(roleRepository.findByRoleName(RoleName.MARKETING)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(saved);
        when(menuRepository.findById(1)).thenReturn(Optional.of(menu(1, "Dashboard")));
        when(menuRepository.findById(2)).thenReturn(Optional.of(menu(2, "Applications")));
        when(roleRepository.findById(1)).thenReturn(Optional.of(saved));

        RoleResponse response = service.createRole(request);

        verify(roleMenuRepository, times(2)).save(any(RoleMenu.class));
        assertThat(response.getMenus()).hasSize(2);
    }

    @Test
    @DisplayName("an unknown menu id aborts the whole creation")
    void createRejectsUnknownMenu() {
        Role saved = role(1, RoleName.MARKETING);

        when(roleRepository.findByRoleName(RoleName.MARKETING)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(saved);
        when(menuRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRole(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Menu not found");
    }

    @Test
    @DisplayName("an update replaces the menu links wholesale rather than adding to them")
    void updateReplacesMenuLinks() {
        Role existing = role(1, RoleName.MARKETING, menu(9, "Old menu"));

        when(roleRepository.findById(1)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class))).thenReturn(existing);
        when(menuRepository.findById(1)).thenReturn(Optional.of(menu(1, "Dashboard")));
        when(menuRepository.findById(2)).thenReturn(Optional.of(menu(2, "Applications")));

        service.updateRole(1, request);

        verify(roleMenuRepository).deleteByRole(existing);
        verify(roleMenuRepository, times(2)).save(any(RoleMenu.class));
    }

    @Test
    @DisplayName("an update rewrites the name and description")
    void updateRewritesNameAndDescription() {
        Role existing = role(1, "OLD_NAME");

        when(roleRepository.findById(1)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class))).thenReturn(existing);
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu(1, "Dashboard")));

        service.updateRole(1, request);

        assertThat(existing.getRoleName()).isEqualTo(RoleName.MARKETING);
        assertThat(existing.getDescription()).isEqualTo("Reviews incoming applications");
    }

    @Test
    @DisplayName("updating an unknown role is refused")
    void updateUnknownRoleIsRefused() {
        when(roleRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(404, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role not found");

        verify(roleMenuRepository, never()).deleteByRole(any());
    }

    @Test
    @DisplayName("deleting a role clears its menu links first, so no orphan rows are left")
    void deleteClearsMenuLinksFirst() {
        Role existing = role(1, RoleName.MARKETING, menu(1, "Dashboard"));
        when(roleRepository.findById(1)).thenReturn(Optional.of(existing));

        service.deleteRole(1);

        verify(roleMenuRepository).deleteByRole(existing);
        verify(roleRepository).delete(existing);
    }

    @Test
    @DisplayName("deleting an unknown role is refused")
    void deleteUnknownRoleIsRefused() {
        when(roleRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRole(404))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role not found");

        verify(roleRepository, never()).delete(any());
    }
}
