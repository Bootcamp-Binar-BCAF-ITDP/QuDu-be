package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.role.RoleRequest;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    @InjectMocks
    private RoleController controller;

    private RoleResponse role() {
        RoleResponse response = new RoleResponse();
        response.setRoleId(1);
        response.setRoleName(RoleName.MARKETING);
        return response;
    }

    @Test
    @DisplayName("a populated list is returned with the success message")
    void listReturnsRoles() {
        when(roleService.getAllRoles(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(role()), 0, 10, 1, 1, true, true, false));

        ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> result =
                controller.getAllRoles(0, 10, "roleId", "asc", "");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Get roles successfully");
    }

    @Test
    @DisplayName("an empty result with no search term says there is no data at all")
    void emptyWithoutSearchSaysNoData() {
        when(roleService.getAllRoles(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAllRoles(0, 10, "roleId", "asc", "").getBody().getMessage())
                .isEqualTo("No role data found");
    }

    @Test
    @DisplayName("an empty result with a search term blames the search")
    void emptyWithSearchBlamesTheSearch() {
        when(roleService.getAllRoles(eq("zzz"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAllRoles(0, 10, "roleId", "asc", "zzz").getBody().getMessage())
                .isEqualTo("No role matches your search");
    }

    @Test
    @DisplayName("options are returned as a plain list")
    void optionsReturnList() {
        when(roleService.getRoleOptions()).thenReturn(List.of(role()));

        assertThat(controller.getRoleOptions().getBody().getMessage())
                .isEqualTo("Role options retrieved");
    }

    @Test
    @DisplayName("a role is returned by id")
    void getByIdReturnsRole() {
        when(roleService.getRoleById(1)).thenReturn(role());

        ResponseEntity<ApiResponse<RoleResponse>> result = controller.getRoleById(1);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Role found");
    }

    @Test
    @DisplayName("a missing role becomes a 404 body")
    void missingRoleBecomes404() {
        when(roleService.getRoleById(404)).thenThrow(new RuntimeException("Role not found"));

        ResponseEntity<ApiResponse<RoleResponse>> result = controller.getRoleById(404);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Role not found");
    }

    @Test
    @DisplayName("a created role answers 201 with no body payload")
    void createReturns201() {
        RoleRequest request = new RoleRequest();
        when(roleService.createRole(request)).thenReturn(role());

        ResponseEntity<ApiResponse<RoleResponse>> result = controller.createRole(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("a rejected creation answers 400 carrying the reason")
    void createFailureBecomes400() {
        RoleRequest request = new RoleRequest();
        when(roleService.createRole(request))
                .thenThrow(new RuntimeException("Role name already exists."));

        ResponseEntity<ApiResponse<RoleResponse>> result = controller.createRole(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().getMessage()).isEqualTo("Role name already exists.");
    }

    @Test
    @DisplayName("an update answers 200")
    void updateReturns200() {
        RoleRequest request = new RoleRequest();
        when(roleService.updateRole(1, request)).thenReturn(role());

        assertThat(controller.updateRole(1, request).getBody().getMessage())
                .isEqualTo("Role updated successfully");
    }

    @Test
    @DisplayName("updating a missing role answers 404")
    void updateMissingBecomes404() {
        RoleRequest request = new RoleRequest();
        when(roleService.updateRole(404, request)).thenThrow(new RuntimeException("Role not found"));

        assertThat(controller.updateRole(404, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a delete answers 200")
    void deleteReturns200() {
        ResponseEntity<ApiResponse<Void>> result = controller.deleteRole(1);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Role deleted successfully");
    }

    @Test
    @DisplayName("deleting a missing role answers 404")
    void deleteMissingBecomes404() {
        doThrow(new RuntimeException("Role not found")).when(roleService).deleteRole(404);

        assertThat(controller.deleteRole(404).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
