package com.delvin.loan.service;

import com.delvin.loan.dto.request.menu.MenuRequest;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.model.Menu;
import com.delvin.loan.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private MenuRepository menuRepository;

    @InjectMocks
    private MenuService service;

    private MenuRequest request;

    @BeforeEach
    void setUp() {
        request = new MenuRequest();
        request.setMenuName("Dashboard");
    }

    private Menu menu(int id, String name) {
        Menu menu = new Menu();
        menu.setMenuId(id);
        menu.setMenuName(name);
        return menu;
    }

    private Page<Menu> onePage() {
        return new PageImpl<>(List.of(menu(1, "Dashboard")), PAGE, 1);
    }

    @Test
    @DisplayName("a null search becomes an empty keyword, which matches every menu")
    void nullSearchBecomesEmptyKeyword() {
        when(menuRepository.findByMenuNameContainingIgnoreCase(any(), eq(PAGE))).thenReturn(onePage());

        service.getAllMenus(null, PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(menuRepository).findByMenuNameContainingIgnoreCase(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEmpty();
    }

    @Test
    @DisplayName("a search term is trimmed but kept in its original case")
    void searchTermIsTrimmedNotLowercased() {
        when(menuRepository.findByMenuNameContainingIgnoreCase(any(), eq(PAGE))).thenReturn(onePage());

        service.getAllMenus("  DashBoard  ", PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(menuRepository).findByMenuNameContainingIgnoreCase(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("DashBoard");
    }

    @Test
    @DisplayName("the page is mapped into the shared envelope")
    void mapsPageIntoEnvelope() {
        when(menuRepository.findByMenuNameContainingIgnoreCase(any(), eq(PAGE))).thenReturn(onePage());

        assertThat(service.getAllMenus("", PAGE).getContent())
                .extracting(MenuResponse::getMenuName)
                .containsExactly("Dashboard");
    }

    @Test
    @DisplayName("options are listed by name")
    void optionsListedByName() {
        when(menuRepository.findAllByOrderByMenuNameAsc())
                .thenReturn(List.of(menu(1, "Applications"), menu(2, "Dashboard")));

        assertThat(service.getMenuOptions())
                .extracting(MenuResponse::getMenuName)
                .containsExactly("Applications", "Dashboard");
    }

    @Test
    @DisplayName("a menu is found by id")
    void findsMenuById() {
        when(menuRepository.findById(1)).thenReturn(Optional.of(menu(1, "Dashboard")));

        assertThat(service.getMenuById(1).getMenuName()).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("an unknown menu id is refused")
    void unknownMenuIdIsRefused() {
        when(menuRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMenuById(404))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Menu not found");
    }

    @Test
    @DisplayName("a new menu is saved under the requested name")
    void createsMenu() {
        when(menuRepository.save(any(Menu.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.createMenu(request).getMenuName()).isEqualTo("Dashboard");

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(captor.capture());
        assertThat(captor.getValue().getMenuName()).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("an existing menu is renamed in place")
    void updatesMenuName() {
        Menu existing = menu(1, "Old name");
        when(menuRepository.findById(1)).thenReturn(Optional.of(existing));
        when(menuRepository.save(any(Menu.class))).thenAnswer(i -> i.getArgument(0));

        service.updateMenu(1, request);

        assertThat(existing.getMenuName()).isEqualTo("Dashboard");
        verify(menuRepository).save(existing);
    }

    @Test
    @DisplayName("updating an unknown menu is refused")
    void updateUnknownMenuIsRefused() {
        when(menuRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMenu(404, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Menu not found");

        verify(menuRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleting a menu removes the row, unlike a branch which is only deactivated")
    void deleteIsAHardDelete() {
        Menu existing = menu(1, "Dashboard");
        when(menuRepository.findById(1)).thenReturn(Optional.of(existing));

        service.deleteMenu(1);

        verify(menuRepository).delete(existing);
    }

    @Test
    @DisplayName("deleting an unknown menu is refused")
    void deleteUnknownMenuIsRefused() {
        when(menuRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMenu(404))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Menu not found");

        verify(menuRepository, never()).delete(any());
    }
}
