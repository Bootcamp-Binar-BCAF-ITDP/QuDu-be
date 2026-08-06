package com.delvin.loan.service;

import com.delvin.loan.dto.request.role.RoleRequest;
import com.delvin.loan.dto.response.role.RoleResponse;
import com.delvin.loan.model.Role;
import com.delvin.loan.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
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

    public RoleResponse createRole(RoleRequest request) {

        if (roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
            throw new RuntimeException("Role name already exists.");
        }

        Role role = new Role();

        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());

        return toResponse(roleRepository.save(role));
    }

    public RoleResponse updateRole(Integer id, RoleRequest request) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());

        return toResponse(roleRepository.save(role));
    }

    public void deleteRole(Integer id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        roleRepository.delete(role);
    }

    private RoleResponse toResponse(Role role) {

        RoleResponse response = new RoleResponse();

        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());

        return response;
    }
}
