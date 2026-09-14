package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.RoleName;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackOfficeControllerTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);
    private static final String BO_ID = "USR-BO";

    @Mock
    private LoanApplicationService applicationService;

    @InjectMocks
    private BackOfficeController controller;

    private AppUser principal() {
        return TestFixtures.principal(BO_ID, RoleName.BACK_OFFICE);
    }

    @Test
    @DisplayName("the bucket is scoped to the authenticated back office user")
    void bucketIsScopedToPrincipal() {
        when(applicationService.listBackOfficeBucket(BO_ID, PAGE))
                .thenReturn(new PageResponse<>(List.of(mock(LoanApplicationResponse.class)),
                        0, 10, 1, 1, true, true, false));

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.backOfficeBucket(principal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Back office bucket retrieved");
        verify(applicationService).listBackOfficeBucket(BO_ID, PAGE);
    }

    @Test
    @DisplayName("an empty bucket is a 200, not an error")
    void emptyBucketIsStillOk() {
        when(applicationService.listBackOfficeBucket(BO_ID, PAGE))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.backOfficeBucket(principal(), PAGE).getBody().getData().getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("a user with no branch is left to the global handler")
    void missingBranchPropagates() {
        AppUser appUser = principal();
        when(applicationService.listBackOfficeBucket(BO_ID, PAGE))
                .thenThrow(BusinessException.badRequest("User USR-BO has no branch assigned."));

        assertThatThrownBy(() -> controller.backOfficeBucket(appUser, PAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
