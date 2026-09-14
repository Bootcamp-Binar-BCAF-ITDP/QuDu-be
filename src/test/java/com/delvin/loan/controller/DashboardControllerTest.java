package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.DashboardPeriod;
import com.delvin.loan.dto.response.dashboard.DashboardResponse;
import com.delvin.loan.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController controller;

    @Test
    @DisplayName("the dashboard is returned in the standard envelope")
    void returnsDashboardInEnvelope() {
        DashboardResponse dashboard = mock(DashboardResponse.class);
        when(dashboardService.getDashboard(DashboardPeriod.THIS_MONTH)).thenReturn(dashboard);

        ResponseEntity<ApiResponse<DashboardResponse>> result =
                controller.dashboard(DashboardPeriod.THIS_MONTH);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Dashboard retrieved");
        assertThat(result.getBody().getData()).isSameAs(dashboard);
    }

    @ParameterizedTest
    @EnumSource(DashboardPeriod.class)
    @DisplayName("every period is passed straight through to the service")
    void everyPeriodReachesTheService(DashboardPeriod period) {
        when(dashboardService.getDashboard(period)).thenReturn(mock(DashboardResponse.class));

        controller.dashboard(period);

        verify(dashboardService).getDashboard(period);
    }
}
