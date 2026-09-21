package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RecommendationStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.event.LoanStatusChangedEvent;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanReview;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanReviewRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReviewServiceTest {

    @Mock
    private LoanReviewRepository reviewRepository;

    @Mock
    private LoanApplicationService applicationService;

    @Mock
    private LoanMapper mapper;

    @Mock
    private ApplicationEventPublisher events;

    @Spy
    private BranchRouting branchRouting = new BranchRouting();

    @InjectMocks
    private LoanReviewService service;

    private LoanReviewRequest request;

    @BeforeEach
    void setUp() {
        request = new LoanReviewRequest();
        request.setApplicationId(TestFixtures.APPLICATION_ID);
        request.setRecommendation(RecommendationStatus.ACCEPT);
        request.setReviewNote("Documents check out");
    }

    private LoanApplication stubHappyPath(String status) {
        User marketing = TestFixtures.user(RoleName.MARKETING, TestFixtures.BRANCH_ID);
        LoanApplication application = TestFixtures.application(status);

        when(applicationService.getUserWithRole(TestFixtures.USER_ID, RoleName.MARKETING))
                .thenReturn(marketing);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);

        return application;
    }

    @Test
    @DisplayName("marketing cannot review another branch's application")
    void rejectsCrossBranchReview() {
        User marketing = TestFixtures.user(RoleName.MARKETING, TestFixtures.BRANCH_ID);
        LoanApplication application =
                TestFixtures.application(LoanStatus.CHECKING, TestFixtures.BRANCH_ID + 1);

        when(applicationService.getUserWithRole(TestFixtures.USER_ID, RoleName.MARKETING))
                .thenReturn(marketing);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);

        assertThatThrownBy(() -> service.submitReview(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This application belongs to a different branch")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(reviewRepository);
        assertThat(application.getStatus()).isEqualTo(LoanStatus.CHECKING);
    }

    @Test
    @DisplayName("ACCEPT moves the application to the branch manager")
    void acceptAdvancesToBranchManager() {
        LoanApplication application = stubHappyPath(LoanStatus.CHECKING);
        when(reviewRepository.existsByApplication_ApplicationId(TestFixtures.APPLICATION_ID))
                .thenReturn(false);
        when(mapper.toReviewResponse(any())).thenReturn(mock(LoanReviewResponse.class));

        service.submitReview(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.PENDING_BRANCH_MANAGER);
        verify(reviewRepository).save(any(LoanReview.class));
    }

    @Test
    @DisplayName("an accepted review notifies nobody, because nothing was decided against the customer")
    void acceptPublishesNoEvent() {
        stubHappyPath(LoanStatus.CHECKING);
        when(reviewRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        when(mapper.toReviewResponse(any())).thenReturn(mock(LoanReviewResponse.class));

        service.submitReview(TestFixtures.USER_ID, request);

        verify(events, never()).publishEvent(any(LoanStatusChangedEvent.class));
    }

    @Test
    @DisplayName("REJECT ends the application and publishes the rejection")
    void rejectEndsApplicationAndPublishes() {
        request.setRecommendation(RecommendationStatus.REJECT);
        request.setReviewNote("Income too low");

        LoanApplication application = stubHappyPath(LoanStatus.CHECKING);
        when(reviewRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        when(mapper.toReviewResponse(any())).thenReturn(mock(LoanReviewResponse.class));

        service.submitReview(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.REJECTED_BY_MARKETING);

        ArgumentCaptor<LoanStatusChangedEvent> captor =
                ArgumentCaptor.forClass(LoanStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());

        LoanStatusChangedEvent published = captor.getValue();
        assertThat(published.applicationId()).isEqualTo(TestFixtures.APPLICATION_ID);
        assertThat(published.status()).isEqualTo(LoanStatus.REJECTED_BY_MARKETING);
        assertThat(published.decisionNote()).isEqualTo("Income too low");
        assertThat(published.approved()).isFalse();
    }

    @Test
    @DisplayName("the recommendation is matched case insensitively")
    void recommendationIsCaseInsensitive() {
        request.setRecommendation("accept");

        LoanApplication application = stubHappyPath(LoanStatus.CHECKING);
        when(reviewRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        when(mapper.toReviewResponse(any())).thenReturn(mock(LoanReviewResponse.class));

        service.submitReview(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.PENDING_BRANCH_MANAGER);
    }

    @ParameterizedTest(name = "status {0} cannot be reviewed")
    @ValueSource(strings = {
            LoanStatus.PENDING_BRANCH_MANAGER,
            LoanStatus.PENDING_BACK_OFFICE,
            LoanStatus.VERIFIED,
            LoanStatus.DISBURSED,
            LoanStatus.REJECTED_BY_MARKETING,
    })
    @DisplayName("only an application still in CHECKING may be reviewed")
    void rejectsApplicationsNotAwaitingReview(String status) {
        stubHappyPath(status);

        assertThatThrownBy(() -> service.submitReview(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not awaiting marketing review")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("an application cannot be reviewed twice")
    void rejectsSecondReview() {
        stubHappyPath(LoanStatus.CHECKING);
        when(reviewRepository.existsByApplication_ApplicationId(TestFixtures.APPLICATION_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitReview(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a review");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("a missing recommendation is rejected before anything is looked up")
    void missingRecommendationFailsFast() {
        request.setRecommendation(null);

        assertThatThrownBy(() -> service.submitReview(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("recommendation is required");

        verifyNoInteractions(applicationService, reviewRepository, events);
    }

    @ParameterizedTest(name = "recommendation \"{0}\" is not allowed")
    @ValueSource(strings = {"MAYBE", "approve", "", "ACCEPTED"})
    @DisplayName("only ACCEPT or REJECT are understood")
    void rejectsUnknownRecommendation(String recommendation) {
        request.setRecommendation(recommendation);

        assertThatThrownBy(() -> service.submitReview(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("recommendation must be ACCEPT or REJECT");

        verifyNoInteractions(applicationService, reviewRepository, events);
    }

    @Test
    @DisplayName("reading a review that does not exist is a 404")
    void getByApplicationNotFound() {
        when(reviewRepository.findByApplication_ApplicationId("APP-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByApplication("APP-404"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No review found for application APP-404")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an existing review is mapped and returned")
    void getByApplicationReturnsMappedReview() {
        LoanReview review = new LoanReview();
        LoanReviewResponse response = mock(LoanReviewResponse.class);

        when(reviewRepository.findByApplication_ApplicationId(TestFixtures.APPLICATION_ID))
                .thenReturn(Optional.of(review));
        when(mapper.toReviewResponse(review)).thenReturn(response);

        assertThat(service.getByApplication(TestFixtures.APPLICATION_ID)).isSameAs(response);
    }
}
