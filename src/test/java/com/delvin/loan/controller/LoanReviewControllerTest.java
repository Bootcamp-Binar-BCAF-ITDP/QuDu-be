package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.service.LoanReviewService;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReviewControllerTest {

    @Mock
    private LoanReviewService reviewService;

    @InjectMocks
    private LoanReviewController controller;

    @Test
    @DisplayName("returns the review wrapped in the standard envelope")
    void returnsReviewInEnvelope() {
        LoanReviewResponse review = mock(LoanReviewResponse.class);
        when(reviewService.getByApplication(TestFixtures.APPLICATION_ID)).thenReturn(review);

        ResponseEntity<ApiResponse<LoanReviewResponse>> result =
                controller.get(TestFixtures.APPLICATION_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);

        ApiResponse<LoanReviewResponse> body = result.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body.getMessage()).isEqualTo("Review retrieved");
        assertThat(body.getData()).isSameAs(review);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("lets a service failure through so the global handler can shape it")
    void propagatesServiceFailure() {
        when(reviewService.getByApplication("APP-404"))
                .thenThrow(BusinessException.notFound("No review found for application APP-404"));

        assertThatThrownBy(() -> controller.get("APP-404"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
