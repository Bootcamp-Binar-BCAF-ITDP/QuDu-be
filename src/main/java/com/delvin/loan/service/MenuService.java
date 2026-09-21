package com.delvin.loan.service;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.evict.EvictsMenuCaches;
import com.delvin.loan.dto.request.menu.MenuRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.model.Menu;
import com.delvin.loan.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    public PageResponse<MenuResponse> getAllMenus(String search, Pageable pageable) {
        String keyword = (search == null) ? "" : search.trim();
        Page<Menu> menus = menuRepository.findByMenuNameContainingIgnoreCase(keyword, pageable);

        return PageResponse.of(menus, this::toResponse);
    }

    @Cacheable(cacheNames = CacheNames.MENU_OPTIONS)
    public List<MenuResponse> getMenuOptions() {
        return menuRepository.findAllByOrderByMenuNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.MENU_BY_ID, key = "#id")
    public MenuResponse getMenuById(Integer id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));

        return toResponse(menu);
    }

    @EvictsMenuCaches
    public MenuResponse createMenu(MenuRequest request) {
        Menu menu = new Menu();

        menu.setMenuName(request.getMenuName());

        return toResponse(menuRepository.save(menu));
    }

    @EvictsMenuCaches
    public MenuResponse updateMenu(Integer id, MenuRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu not found"));

        menu.setMenuName(request.getMenuName());

        return toResponse(menuRepository.save(menu));
    }

    @Transactional
    @EvictsMenuCaches
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