package com.delvin.loan.controller;

import com.delvin.loan.common.*;
import com.delvin.loan.common.evict.EvictsRoleCaches;
import com.delvin.loan.dto.request.role.RoleRequest;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("roleId", "roleName", "description");

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAllRoles(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "roleId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search) {

        Pageable pageable = PaginationUtil.build(page, size, sortBy, sortDir, SORTABLE_FIELDS, "roleId");
        PageResponse<RoleResponse> roles = roleService.getAllRoles(search, pageable);

        if (roles.getTotalElements() == 0) {
            String message = search.isBlank()
                    ? "No role data found"
                    : "No role matches your search";

            return ResponseUtil.success(message, roles);
        }

        return ResponseUtil.success("Get roles successfully", roles);
    }

    @Cacheable(cacheNames = CacheNames.ROLE_OPTIONS)
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoleOptions() {
        return ResponseUtil.success("Role options retrieved", roleService.getRoleOptions());
    }

    @GetMapping("/{id}")
    @Cacheable(cacheNames = CacheNames.ROLE_BY_ID, key = "#id")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Integer id) {
        try {
            RoleResponse response = roleService.getRoleById(id);

            return ResponseUtil.success("Role found", response);

        } catch (RuntimeException e) {
            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    @EvictsRoleCaches
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@RequestBody RoleRequest request) {
        try {
            roleService.createRole(request);

            return ResponseUtil.created("Role created successfully", null);
        } catch (RuntimeException e) {
            return ResponseUtil.error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return ResponseUtil.error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred."
            );
        }
    }

    @PutMapping("/update/{id}")
    @EvictsRoleCaches
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable Integer id, @RequestBody RoleRequest request) {
        try {
            roleService.updateRole(id, request);

            return ResponseUtil.success("Role updated successfully", null);
        } catch (RuntimeException e) {
            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    @EvictsRoleCaches
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Integer id) {
        try {
            roleService.deleteRole(id);

            return ResponseUtil.success("Role deleted successfully", null);
        } catch (RuntimeException e) {
            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}