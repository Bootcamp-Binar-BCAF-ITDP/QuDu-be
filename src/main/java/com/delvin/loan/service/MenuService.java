package com.delvin.loan.service;

import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.menu.MenuRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.model.Menu;
import com.delvin.loan.repository.MenuRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuService {
    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public PageResponse<MenuResponse> getAllMenus(Pageable pageable) {

        Page<Menu> menus = menuRepository.findAll(pageable);

        return PageResponse.of(menus, this::toResponse);
    }

    public MenuResponse getMenuById(Integer id) {

        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));

        return toResponse(menu);
    }

    public MenuResponse createMenu(MenuRequest request) {

        Menu menu = new Menu();

        menu.setMenuName(request.getMenuName());

        return toResponse(menuRepository.save(menu));
    }

    public MenuResponse updateMenu(Integer id, MenuRequest request) {

        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));

        menu.setMenuName(request.getMenuName());

        return toResponse(menuRepository.save(menu));
    }

    public void deleteMenu(Integer id) {

        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));

        menuRepository.delete(menu);
    }

    private MenuResponse toResponse(Menu menu) {

        MenuResponse response = new MenuResponse();

        response.setMenuId(menu.getMenuId());
        response.setMenuName(menu.getMenuName());

        return response;
    }
}
