package com.delvin.loan.service;

import com.delvin.loan.common.DashboardPeriod;
import com.delvin.loan.dto.response.dashboard.DashboardResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.LoanDisbursementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private LoanDisbursementRepository disbursementRepository;

    @InjectMocks
    private DashboardService service;

    @BeforeEach
    void stubEmptyData() {
        lenient().when(applicationRepository.countByStatusBetween(any(), any()))
                .thenReturn(List.of());
        lenient().when(applicationRepository.countByDayAndStatusBetween(any(), any()))
                .thenReturn(List.of());
        lenient().when(disbursementRepository.sumDisbursedBetween(any(), any()))
                .thenReturn(null);
    }

    private List<LocalDate> capturedRanges() {
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);

        verify(applicationRepository, org.mockito.Mockito.atLeast(2))
                .countByStatusBetween(from.capture(), to.capture());

        return List.of(
                from.getAllValues().get(0), to.getAllValues().get(0),
                from.getAllValues().get(1), to.getAllValues().get(1));
    }

    @Test
    @DisplayName("a custom window is used exactly as given")
    void customWindowIsUsedAsGiven() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = LocalDate.of(2026, 3, 19);

        DashboardResponse response = service.getDashboard(DashboardPeriod.THIS_MONTH, from, to);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("a custom window overrides the preset rather than combining with it")
    void customWindowOverridesThePreset() {
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.of(2020, 1, 31);

        DashboardResponse response = service.getDashboard(DashboardPeriod.THIS_YEAR, from, to);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("the comparison window is the equally long one ending the day before")
    void comparisonWindowPrecedesTheCustomOne() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = LocalDate.of(2026, 3, 19);

        service.getDashboard(DashboardPeriod.THIS_MONTH, from, to);

        List<LocalDate> ranges = capturedRanges();

        assertThat(ranges.get(0)).isEqualTo(from);
        assertThat(ranges.get(1)).isEqualTo(to);
        assertThat(ranges.get(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ranges.get(3)).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("a single day window compares against the day before")
    void singleDayComparesAgainstTheDayBefore() {
        LocalDate day = LocalDate.of(2026, 3, 15);

        service.getDashboard(DashboardPeriod.THIS_MONTH, day, day);

        List<LocalDate> ranges = capturedRanges();

        assertThat(ranges.get(2)).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(ranges.get(3)).isEqualTo(LocalDate.of(2026, 3, 14));
    }

    @Test
    @DisplayName("a lower bound without an upper one is refused rather than guessed at")
    void lowerBoundAloneIsRefused() {
        assertThatThrownBy(() -> service.getDashboard(
                DashboardPeriod.THIS_MONTH, LocalDate.of(2026, 3, 1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("from and to must be supplied together")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(applicationRepository, disbursementRepository);
    }

    @Test
    @DisplayName("an upper bound without a lower one is refused too")
    void upperBoundAloneIsRefused() {
        assertThatThrownBy(() -> service.getDashboard(
                DashboardPeriod.THIS_MONTH, null, LocalDate.of(2026, 3, 31)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("from and to must be supplied together");

        verifyNoInteractions(applicationRepository, disbursementRepository);
    }

    @Test
    @DisplayName("a window that runs backwards is refused")
    void backwardsWindowIsRefused() {
        assertThatThrownBy(() -> service.getDashboard(
                DashboardPeriod.THIS_MONTH, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("from cannot be after to");

        verifyNoInteractions(applicationRepository, disbursementRepository);
    }

    @Test
    @DisplayName("with no custom dates the preset still decides the window")
    void presetStillWorks() {
        DashboardResponse response = service.getDashboard(DashboardPeriod.LAST_7_DAYS);

        LocalDate today = LocalDate.now();
        assertThat(response.to()).isEqualTo(today);
        assertThat(response.from()).isEqualTo(today.minusDays(6));
    }

    @Test
    @DisplayName("an empty period still answers with zeroed figures rather than failing")
    void emptyPeriodIsNotAFailure() {
        DashboardResponse response = service.getDashboard(DashboardPeriod.THIS_MONTH);

        assertThat(response.byStatusTotal()).isZero();
        assertThat(response.summary()).isNotNull();
    }
}
