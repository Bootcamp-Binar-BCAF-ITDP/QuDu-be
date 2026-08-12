package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.user.UserRequest;
import com.delvin.loan.dto.response.user.UserResponse;
import com.delvin.loan.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {

        List<UserResponse> users = userService.getAllUsers();

        if (users.isEmpty()) {
            return ResponseUtil.success("No user data found", users);
        }

        return ResponseUtil.success("Users retrieved successfully", users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String id) {

        try {

            UserResponse response = userService.getUserById(id);

            return ResponseUtil.success("User found", response);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String id,
            @RequestBody UserRequest request) {

        try {
            userService.updateUser(id, request);

            return ResponseUtil.success("User updated successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable String id) {

        try {
            userService.deleteUser(id);

            return ResponseUtil.success("User deactivated successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}