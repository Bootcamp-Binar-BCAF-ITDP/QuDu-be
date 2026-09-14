package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.plafond.PlafondRequest;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.service.PlafondService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlafondControllerTest {

    @Mock
    private PlafondService plafondService;

    @InjectMocks
    private PlafondController controller;

    private PlafondResponse tier(int level) {
        PlafondResponse response = new PlafondResponse();
        response.setPlafondId(level);
        response.setLevel(level);
        return response;
    }

    @Test
    @DisplayName("a populated list is returned with the success message")
    void listReturnsPlafonds() {
        when(plafondService.getAll(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(tier(1)), 0, 10, 1, 1, true, true, false));

        ResponseEntity<ApiResponse<PageResponse<PlafondResponse>>> result =
                controller.getAll(0, 10, "plafondId", "asc", "");

        assertThat(result.getBody().getMessage()).isEqualTo("Plafonds retrieved successfully");
    }

    @Test
    @DisplayName("an empty result with no search term says there is no data at all")
    void emptyWithoutSearchSaysNoData() {
        when(plafondService.getAll(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAll(0, 10, "plafondId", "asc", "").getBody().getMessage())
                .isEqualTo("No plafond data found");
    }

    @Test
    @DisplayName("an empty result with a search term blames the search")
    void emptyWithSearchBlamesTheSearch() {
        when(plafondService.getAll(eq("zzz"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAll(0, 10, "plafondId", "asc", "zzz").getBody().getMessage())
                .isEqualTo("No Plafond matches your search");
    }

    @Test
    @DisplayName("the public catalog returns the active tiers")
    void catalogReturnsActiveTiers() {
        when(plafondService.catalog()).thenReturn(List.of(tier(1), tier(2)));

        ResponseEntity<ApiResponse<List<PlafondResponse>>> result = controller.catalog();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Plafond catalog retrieved successfully");
        assertThat(result.getBody().getData()).hasSize(2);
    }

    @Test
    @DisplayName("an empty catalog is a 200 with an empty list, not an error")
    void emptyCatalogIsStillOk() {
        when(plafondService.catalog()).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<PlafondResponse>>> result = controller.catalog();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData()).isEmpty();
    }

    @Test
    @DisplayName("a tier is returned by id")
    void getByIdReturnsTier() {
        when(plafondService.getById(3)).thenReturn(tier(3));

        assertThat(controller.getById(3).getBody().getData().getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("a tier is returned by level")
    void getByLevelReturnsTier() {
        when(plafondService.getByLevel(3)).thenReturn(tier(3));

        assertThat(controller.getByLevel(3).getBody().getData().getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("a lookup failure is left to the global handler, unlike the branch controller")
    void lookupFailurePropagates() {
        when(plafondService.getById(404))
                .thenThrow(BusinessException.notFound("Plafond with id 404 not found"));

        assertThatThrownBy(() -> controller.getById(404))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a simulation resolves the amount to a tier")
    void simulateResolvesAmount() {
        when(plafondService.simulate(BigDecimal.valueOf(15_000_000))).thenReturn(tier(2));

        ResponseEntity<ApiResponse<PlafondResponse>> result =
                controller.simulate(BigDecimal.valueOf(15_000_000));

        assertThat(result.getBody().getMessage()).isEqualTo("Matching plafond found");
        assertThat(result.getBody().getData().getLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("a created tier answers 201 and carries the saved tier back")
    void createReturns201WithBody() {
        PlafondRequest request = new PlafondRequest();
        when(plafondService.create(request)).thenReturn(tier(4));

        ResponseEntity<ApiResponse<PlafondResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getData().getLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("an update answers 200 and carries the saved tier back")
    void updateReturns200WithBody() {
        PlafondRequest request = new PlafondRequest();
        when(plafondService.update(4, request)).thenReturn(tier(4));

        ResponseEntity<ApiResponse<PlafondResponse>> result = controller.update(4, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData().getLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("a delete answers 200 and says deactivated, because the row survives")
    void deleteSaysDeactivated() {
        ResponseEntity<ApiResponse<Void>> result = controller.delete(4);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Plafond deactivated successfully");
        verify(plafondService).delete(4);
    }

    @Test
    @DisplayName("refusing to delete the default tier is left to the global handler")
    void deleteDefaultTierPropagates() {
        doThrow(BusinessException.badRequest("The default plafond (level 1) cannot be deleted"))
                .when(plafondService).delete(1);

        assertThatThrownBy(() -> controller.delete(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deleted");
    }
}
