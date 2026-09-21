package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.LoanReviewService;
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
class MarketingControllerTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private LoanApplicationService applicationService;

    @Mock
    private LoanReviewService loanReviewService;

    @InjectMocks
    private MarketingController controller;

    private AppUser marketingPrincipal() {
        AppUser appUser = mock(AppUser.class);
        when(appUser.getUserId()).thenReturn(TestFixtures.USER_ID);
        return appUser;
    }

    @Test
    @DisplayName("the bucket is returned in the standard envelope")
    void bucketReturnsApplications() {
        PageResponse<LoanApplicationResponse> page =
                new PageResponse<>(List.of(mock(LoanApplicationResponse.class)), 0, 10, 1, 1, true, true, false);
        when(applicationService.listMarketingBucket(TestFixtures.USER_ID, PAGE)).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.marketingBucket(marketingPrincipal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Marketing bucket retrieved");
        assertThat(result.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("an empty bucket is a 200, not an error")
    void emptyBucketIsStillOk() {
        when(applicationService.listMarketingBucket(TestFixtures.USER_ID, PAGE))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> result =
                controller.marketingBucket(marketingPrincipal(), PAGE);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData().getContent()).isEmpty();
    }

    @Test
    @DisplayName("a submitted review answers 201")
    void submitReturns201() {
        LoanReviewRequest request = new LoanReviewRequest();
        LoanReviewResponse review = mock(LoanReviewResponse.class);
        when(loanReviewService.submitReview(TestFixtures.USER_ID, request)).thenReturn(review);

        ResponseEntity<ApiResponse<LoanReviewResponse>> result =
                controller.submit(marketingPrincipal(), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getMessage()).isEqualTo("Review submitted");
        assertThat(result.getBody().getData()).isSameAs(review);
    }

    @Test
    @DisplayName("the reviewer is taken from the authenticated principal, never from the body")
    void reviewerComesFromThePrincipal() {
        LoanReviewRequest request = new LoanReviewRequest();
        when(loanReviewService.submitReview(TestFixtures.USER_ID, request))
                .thenReturn(mock(LoanReviewResponse.class));

        controller.submit(marketingPrincipal(), request);

        verify(loanReviewService).submitReview(TestFixtures.USER_ID, request);
    }

    @Test
    @DisplayName("a refused review is left to the global handler")
    void refusedReviewPropagates() {
        LoanReviewRequest request = new LoanReviewRequest();
        when(loanReviewService.submitReview(TestFixtures.USER_ID, request))
                .thenThrow(BusinessException.conflict("Application already has a review."));

        AppUser principal = marketingPrincipal();

        assertThatThrownBy(() -> controller.submit(principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
