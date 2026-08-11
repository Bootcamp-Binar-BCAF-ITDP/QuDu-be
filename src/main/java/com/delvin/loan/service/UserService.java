package com.delvin.loan.service;

import com.delvin.loan.dto.request.user.UserRequest;
import com.delvin.loan.dto.response.user.UserResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.BranchRepository;
import com.delvin.loan.repository.RoleRepository;
import com.delvin.loan.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository,
                       BranchRepository branchRepository,
                       RoleRepository roleRepository
                       ) {

        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.roleRepository = roleRepository;
    }

    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(String id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toResponse(user);
    }


    @Transactional
    public UserResponse updateUser(String id, UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getBranchId() != null) {

            Branch branch = branchRepository
                    .findByBranchIdAndIsActive(request.getBranchId(), true)
                    .orElseThrow(() -> new RuntimeException("Branch not found"));

            user.setBranch(branch);
        }

        if (request.getRoleId() != null) {

            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            user.setRole(role);
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getPassword() != null &&
                !request.getPassword().isBlank()) {

            user.setPassword(request.getPassword());
        }

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    @Transactional
    public void deleteUser(String id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsActive(false);

        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {

        UserResponse response = new UserResponse();

        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setIsActive(user.getIsActive());

        if (user.getBranch() != null) {
            response.setBranchId(user.getBranch().getBranchId());
            response.setBranchName(user.getBranch().getBranchName());
        }

        if (user.getRole() != null) {
            response.setRoleId(user.getRole().getRoleId());
            response.setRoleName(user.getRole().getRoleName());
        }

        return response;
    }
}