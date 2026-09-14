package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanDisbursementRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanDisbursementResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanDisbursementService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanDisbursementControllerTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);
    private static final String BO_ID = "USR-BO";

    @Mock
    private LoanDisbursementService disbursementService;

    @InjectMocks
    private LoanDisbursementController controller;

    private AppUser principal() {
        return TestFixtures.principal(BO_ID, RoleName.BACK_OFFICE);
    }

    @Test
    @DisplayName("the bucket is scoped to the authenticated back office user")
    void bucketIsScopedToPrincipal() {
        when(disbursementService.getDisbursementBucketList(BO_ID, PAGE))
                .thenReturn(new PageResponse<>(List.of(mock(LoanApplicationResponse.class)),
                        0, 10, 1, 1, true, true, false));

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.getMyBucket(principal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Disbursement bucket retrieved");
        verify(disbursementService).getDisbursementBucketList(BO_ID, PAGE);
    }

    @Test
    @DisplayName("an empty bucket is a 200, not an error")
    void emptyBucketIsStillOk() {
        when(disbursementService.getDisbursementBucketList(BO_ID, PAGE))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getMyBucket(principal(), PAGE).getBody().getData().getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("a disbursement decision answers 200, not 201, because nothing new is created")
    void disburseReturns200() {
        LoanDisbursementRequest request = new LoanDisbursementRequest();
        LoanDisbursementResponse response = mock(LoanDisbursementResponse.class);
        when(disbursementService.disburse(BO_ID, request)).thenReturn(response);

        ResponseEntity<ApiResponse<LoanDisbursementResponse>> result =
                controller.disburse(principal(), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Disbursement decision recorded");
        assertThat(result.getBody().getData()).isSameAs(response);
    }

    @Test
    @DisplayName("the decider is taken from the authenticated principal, never from the body")
    void deciderComesFromThePrincipal() {
        LoanDisbursementRequest request = new LoanDisbursementRequest();
        when(disbursementService.disburse(BO_ID, request))
                .thenReturn(mock(LoanDisbursementResponse.class));

        controller.disburse(principal(), request);

        verify(disbursementService).disburse(BO_ID, request);
    }

    @Test
    @DisplayName("a refused disbursement is left to the global handler")
    void refusedDisbursementPropagates() {
        LoanDisbursementRequest request = new LoanDisbursementRequest();
        AppUser appUser = principal();
        when(disbursementService.disburse(BO_ID, request))
                .thenThrow(BusinessException.forbidden("This application belongs to a different branch"));

        assertThatThrownBy(() -> controller.disburse(appUser, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a disbursement is returned by application id")
    void getByApplicationReturnsDisbursement() {
        LoanDisbursementResponse response = mock(LoanDisbursementResponse.class);
        when(disbursementService.getByApplication(TestFixtures.APPLICATION_ID)).thenReturn(response);

        ResponseEntity<ApiResponse<LoanDisbursementResponse>> result =
                controller.get(TestFixtures.APPLICATION_ID);

        assertThat(result.getBody().getMessage()).isEqualTo("Disbursement retrieved");
        assertThat(result.getBody().getData()).isSameAs(response);
    }

    @Test
    @DisplayName("a missing disbursement is left to the global handler")
    void missingDisbursementPropagates() {
        when(disbursementService.getByApplication("APP-404"))
                .thenThrow(BusinessException.notFound("No disbursement found for application APP-404"));

        assertThatThrownBy(() -> controller.get("APP-404"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
