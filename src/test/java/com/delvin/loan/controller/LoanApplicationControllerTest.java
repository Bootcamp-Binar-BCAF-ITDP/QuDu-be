package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.response.loanresp.CreditScoreResponse;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationControllerTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private LoanApplicationService applicationService;

    @InjectMocks
    private LoanApplicationController controller;

    private PageResponse<LoanApplicationResponse> onePage() {
        return new PageResponse<>(List.of(mock(LoanApplicationResponse.class)),
                0, 10, 1, 1, true, true, false);
    }

    private AppUser principal() {
        return TestFixtures.principal("USR-MKT", RoleName.MARKETING);
    }

    @Test
    @DisplayName("the list is returned in the standard envelope")
    void listReturnsApplications() {
        when(applicationService.getAllApplication(null, null, null, null, PAGE)).thenReturn(onePage());

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getAllApplication(null, null, null, null, PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Loan Application retrieved");
    }

    @Test
    @DisplayName("status and search are passed straight through")
    void filtersReachTheService() {
        List<String> statuses = List.of(LoanStatus.CHECKING);
        when(applicationService.getAllApplication(statuses, "budi", null, null, PAGE)).thenReturn(onePage());

        controller.getAllApplication(statuses, "budi", null, null, PAGE);

        verify(applicationService).getAllApplication(statuses, "budi", null, null, PAGE);
    }

    @Test
    @DisplayName("an anonymous caller has no bucket and is told so without reaching the service")
    void anonymousCallerHasNoBucket() {
        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getMyBucket(null, PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody().getMessage())
                .isEqualTo("Only internal users have an application bucket");
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("the bucket is resolved from the principal's own id and role")
    void bucketUsesPrincipalIdAndRole() {
        when(applicationService.getMyBucket("USR-MKT", RoleName.MARKETING, PAGE))
                .thenReturn(onePage());

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getMyBucket(principal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Bucket retrieved");
        verify(applicationService).getMyBucket("USR-MKT", RoleName.MARKETING, PAGE);
    }

    @Test
    @DisplayName("a business failure on the bucket keeps its own status code")
    void bucketBusinessFailureKeepsItsStatus() {
        when(applicationService.getMyBucket(any(), any(), any()))
                .thenThrow(BusinessException.forbidden("Role CUSTOMER has no application bucket"));

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getMyBucket(principal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody().getMessage()).isEqualTo("Role CUSTOMER has no application bucket");
    }

    @Test
    @DisplayName("an unexpected failure on the bucket is flattened to a 500 with no internals leaked")
    void bucketUnexpectedFailureBecomes500() {
        when(applicationService.getMyBucket(any(), any(), any()))
                .thenThrow(new IllegalStateException("connection reset by peer"));

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getMyBucket(principal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().getMessage()).isEqualTo("Failed to retrieve bucket");
        assertThat(result.getBody().getMessage()).doesNotContain("connection reset");
    }

    @Test
    @DisplayName("an application is returned by id")
    void getByIdReturnsApplication() {
        LoanApplicationResponse application = mock(LoanApplicationResponse.class);
        when(applicationService.getApplication(TestFixtures.APPLICATION_ID)).thenReturn(application);

        ResponseEntity<ApiResponse<LoanApplicationResponse>> result =
                controller.getApplicationById(TestFixtures.APPLICATION_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Loan application retrieved");
        assertThat(result.getBody().getData()).isSameAs(application);
    }

    @Test
    @DisplayName("a missing application keeps the 404 the service chose")
    void missingApplicationKeepsItsStatus() {
        when(applicationService.getApplication("APP-404"))
                .thenThrow(BusinessException.notFound("Loan application not found: APP-404"));

        ResponseEntity<ApiResponse<LoanApplicationResponse>> result =
                controller.getApplicationById("APP-404");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Loan application not found: APP-404");
    }

    @Test
    @DisplayName("an unexpected failure on a lookup is flattened to a 500")
    void lookupUnexpectedFailureBecomes500() {
        when(applicationService.getApplication("APP-1"))
                .thenThrow(new IllegalStateException("boom"));

        ResponseEntity<ApiResponse<LoanApplicationResponse>> result =
                controller.getApplicationById("APP-1");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().getMessage()).isEqualTo("Failed to retrieve loan application");
    }

    @Test
    @DisplayName("the credit score is returned for one application")
    void creditScoreReturnsRatio() {
        CreditScoreResponse score = new CreditScoreResponse(
                new BigDecimal("1066186"), new BigDecimal("0.12"), new BigDecimal("5000000"),
                new BigDecimal("21.32"), "LOW", null);
        when(applicationService.creditScore(TestFixtures.APPLICATION_ID)).thenReturn(score);

        ResponseEntity<ApiResponse<CreditScoreResponse>> result =
                controller.creditScore(TestFixtures.APPLICATION_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Credit score retrieved");
        assertThat(result.getBody().getData().getDsr()).isEqualByComparingTo(new BigDecimal("21.32"));
        assertThat(result.getBody().getData().getBand()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("a score that could not be computed still answers 200, carrying the reason")
    void unavailableScoreIsStillOk() {
        CreditScoreResponse score = new CreditScoreResponse(
                null, null, null, null, "UNKNOWN", "The application declares no monthly income");
        when(applicationService.creditScore(TestFixtures.APPLICATION_ID)).thenReturn(score);

        ResponseEntity<ApiResponse<CreditScoreResponse>> result =
                controller.creditScore(TestFixtures.APPLICATION_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData().getDsr()).isNull();
        assertThat(result.getBody().getData().getUnavailableReason()).contains("no monthly income");
    }

    @Test
    @DisplayName("a score for an unknown application is left to the global handler as a 404")
    void creditScoreForUnknownApplicationPropagates() {
        when(applicationService.creditScore("APP-404"))
                .thenThrow(BusinessException.notFound("Loan application not found: APP-404"));

        assertThatThrownBy(() -> controller.creditScore("APP-404"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("applications are listed for one customer")
    void listsByCustomer() {
        when(applicationService.listByCustomer(TestFixtures.CUSTOMER_ID, PAGE)).thenReturn(onePage());

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.listByCustomer(TestFixtures.CUSTOMER_ID, PAGE);

        assertThat(result.getBody().getMessage()).isEqualTo("Loan applications retrieved");
        assertThat(result.getBody().getData().getContent()).hasSize(1);
    }
}
