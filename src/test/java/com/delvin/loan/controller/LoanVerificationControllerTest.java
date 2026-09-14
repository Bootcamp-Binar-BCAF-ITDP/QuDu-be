package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanVerificationRequest;
import com.delvin.loan.dto.response.loanresp.LoanVerificationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanVerificationService;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanVerificationControllerTest {

    private static final String BO_ID = "USR-BO";

    @Mock
    private LoanVerificationService verificationService;

    @InjectMocks
    private LoanVerificationController controller;

    private AppUser principal() {
        return TestFixtures.principal(BO_ID, RoleName.BACK_OFFICE);
    }

    @Test
    @DisplayName("a recorded call answers 201, because an attempt row is created")
    void submitReturns201() {
        LoanVerificationRequest request = new LoanVerificationRequest();
        LoanVerificationResponse response = mock(LoanVerificationResponse.class);
        when(verificationService.submitVerification(BO_ID, request)).thenReturn(response);

        ResponseEntity<ApiResponse<LoanVerificationResponse>> result =
                controller.submit(principal(), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getMessage()).isEqualTo("Verification call recorded");
        assertThat(result.getBody().getData()).isSameAs(response);
    }

    @Test
    @DisplayName("the caller is taken from the authenticated principal, never from the body")
    void callerComesFromThePrincipal() {
        LoanVerificationRequest request = new LoanVerificationRequest();
        when(verificationService.submitVerification(BO_ID, request))
                .thenReturn(mock(LoanVerificationResponse.class));

        controller.submit(principal(), request);

        verify(verificationService).submitVerification(BO_ID, request);
    }

    @Test
    @DisplayName("a refused verification is left to the global handler")
    void refusedVerificationPropagates() {
        LoanVerificationRequest request = new LoanVerificationRequest();
        AppUser appUser = principal();
        when(verificationService.submitVerification(BO_ID, request))
                .thenThrow(BusinessException.conflict("not awaiting back office verification"));

        assertThatThrownBy(() -> controller.submit(appUser, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("the call history is returned for an application")
    void listReturnsHistory() {
        LoanVerificationResponse first = mock(LoanVerificationResponse.class);
        when(verificationService.listByApplication(TestFixtures.APPLICATION_ID))
                .thenReturn(List.of(first));

        ResponseEntity<ApiResponse<List<LoanVerificationResponse>>> result =
                controller.list(TestFixtures.APPLICATION_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Verification history retrieved");
        assertThat(result.getBody().getData()).containsExactly(first);
    }

    @Test
    @DisplayName("an application with no attempts answers an empty list, not a 404")
    void emptyHistoryIsStillOk() {
        when(verificationService.listByApplication("APP-404")).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<LoanVerificationResponse>>> result =
                controller.list("APP-404");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData()).isEmpty();
    }
}
