package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.menu.MenuRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.service.MenuService;
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
class MenuControllerTest {

    @Mock
    private MenuService menuService;

    @InjectMocks
    private MenuController controller;

    private PageResponse<MenuResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true);
    }

    private PageResponse<MenuResponse> onePage() {
        MenuResponse menu = new MenuResponse();
        menu.setMenuId(1);
        menu.setMenuName("Dashboard");
        return new PageResponse<>(List.of(menu), 0, 10, 1, 1, true, true, false);
    }

    @Test
    @DisplayName("a populated list is returned with the success message")
    void listReturnsMenus() {
        when(menuService.getAllMenus(eq(""), any(Pageable.class))).thenReturn(onePage());

        ResponseEntity<ApiResponse<PageResponse<MenuResponse>>> result =
                controller.getAllMenus(0, 10, "menuId", "asc", "");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Menus retrieved successfully");
    }

    @Test
    @DisplayName("an empty result with no search term says there is no data at all")
    void emptyWithoutSearchSaysNoData() {
        when(menuService.getAllMenus(eq(""), any(Pageable.class))).thenReturn(emptyPage());

        assertThat(controller.getAllMenus(0, 10, "menuId", "asc", "").getBody().getMessage())
                .isEqualTo("No menu data found");
    }

    @Test
    @DisplayName("an empty result with a search term blames the search")
    void emptyWithSearchBlamesTheSearch() {
        when(menuService.getAllMenus(eq("zzz"), any(Pageable.class))).thenReturn(emptyPage());

        assertThat(controller.getAllMenus(0, 10, "menuId", "asc", "zzz").getBody().getMessage())
                .isEqualTo("No menu matches your search");
    }

    @Test
    @DisplayName("a menu is returned by id")
    void getByIdReturnsMenu() {
        MenuResponse menu = new MenuResponse();
        menu.setMenuName("Dashboard");
        when(menuService.getMenuById(1)).thenReturn(menu);

        ResponseEntity<ApiResponse<MenuResponse>> result = controller.getMenuById(1);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Menu found");
        assertThat(result.getBody().getData().getMenuName()).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("a missing menu becomes a 404 body")
    void missingMenuBecomes404() {
        when(menuService.getMenuById(404)).thenThrow(new RuntimeException("Menu not found"));

        ResponseEntity<ApiResponse<MenuResponse>> result = controller.getMenuById(404);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Menu not found");
    }

    @Test
    @DisplayName("options are returned as a plain list")
    void optionsReturnList() {
        when(menuService.getMenuOptions()).thenReturn(List.of(new MenuResponse()));

        assertThat(controller.getMenuOptions().getBody().getMessage())
                .isEqualTo("Menu options retrieved");
    }

    @Test
    @DisplayName("a created menu answers 201")
    void createReturns201() {
        MenuRequest request = new MenuRequest();
        when(menuService.createMenu(request)).thenReturn(new MenuResponse());

        ResponseEntity<ApiResponse<MenuResponse>> result = controller.createMenu(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getMessage()).isEqualTo("Menu created successfully");
    }

    @Test
    @DisplayName("a rejected creation answers 400")
    void createFailureBecomes400() {
        MenuRequest request = new MenuRequest();
        when(menuService.createMenu(request)).thenThrow(new RuntimeException("bad menu"));

        ResponseEntity<ApiResponse<MenuResponse>> result = controller.createMenu(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().getMessage()).isEqualTo("bad menu");
    }

    @Test
    @DisplayName("an update answers 200")
    void updateReturns200() {
        MenuRequest request = new MenuRequest();
        when(menuService.updateMenu(1, request)).thenReturn(new MenuResponse());

        assertThat(controller.updateMenu(1, request).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updating a missing menu answers 404")
    void updateMissingBecomes404() {
        MenuRequest request = new MenuRequest();
        when(menuService.updateMenu(404, request)).thenThrow(new RuntimeException("Menu not found"));

        assertThat(controller.updateMenu(404, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a delete answers 200")
    void deleteReturns200() {
        ResponseEntity<ApiResponse<Object>> result = controller.deleteMenu(1);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Menu deleted successfully");
    }

    @Test
    @DisplayName("deleting a missing menu answers 404")
    void deleteMissingBecomes404() {
        doThrow(new RuntimeException("Menu not found")).when(menuService).deleteMenu(404);

        ResponseEntity<ApiResponse<Object>> result = controller.deleteMenu(404);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Menu not found");
    }
}
