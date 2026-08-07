package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.menu.MenuRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.service.MenuService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getAllMenus() {

        List<MenuResponse> menus = menuService.getAllMenus();

        if (menus.isEmpty()) {
            return ResponseUtil.success("No menu data found", menus);
        }

        return ResponseUtil.success("Menus retrieved successfully", menus);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuResponse>> getMenuById(@PathVariable Integer id) {

        try {

            MenuResponse response = menuService.getMenuById(id);

            return ResponseUtil.success("Menu found", response);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuResponse>> createMenu(
            @RequestBody MenuRequest request) {

        try {

            MenuResponse response = menuService.createMenu(request);

            return ResponseUtil.created("Menu created successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            return ResponseUtil.error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred."
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(
            @PathVariable Integer id,
            @RequestBody MenuRequest request) {

        try {

            MenuResponse response = menuService.updateMenu(id, request);

            return ResponseUtil.success("Menu updated successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteMenu(@PathVariable Integer id) {

        try {

            menuService.deleteMenu(id);

            return ResponseUtil.success("Menu deleted successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}