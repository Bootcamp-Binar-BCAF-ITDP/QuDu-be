package com.delvin.loan.service;

import com.delvin.loan.common.CallStatus;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanVerificationRequest;
import com.delvin.loan.dto.response.loanresp.LoanVerificationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.User;
import com.delvin.loan.model.LoanVerification;
import com.delvin.loan.repository.LoanVerificationRepository;
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
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanVerificationServiceTest {

    @Mock
    private LoanVerificationRepository verificationRepository;

    @Mock
    private LoanApplicationService applicationService;

    @Mock
    private LoanMapper mapper;

    @Spy
    private BranchRouting branchRouting = new BranchRouting();

    @InjectMocks
    private LoanVerificationService service;

    private LoanVerificationRequest request;

    @BeforeEach
    void setUp() {
        request = new LoanVerificationRequest();
        request.setApplicationId(TestFixtures.APPLICATION_ID);
        request.setCallStatus(CallStatus.CAN_BE_CONTACTED);
        request.setVerificationNote("Spoke to the customer, details confirmed");
    }

    private LoanApplication stubLookups(String status) {
        LoanApplication application = TestFixtures.application(status);

        User backOffice = TestFixtures.user(RoleName.BACK_OFFICE, TestFixtures.BRANCH_ID);
        backOffice.setUserId(TestFixtures.USER_ID);

        when(applicationService.getUserWithRole(TestFixtures.USER_ID, RoleName.BACK_OFFICE))
                .thenReturn(backOffice);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);

        return application;
    }

    @Test
    @DisplayName("back office cannot verify another branch's application")
    void rejectsCrossBranchVerification() {
        LoanApplication application =
                TestFixtures.application(LoanStatus.PENDING_BACK_OFFICE, TestFixtures.BRANCH_ID + 1);

        when(applicationService.getUserWithRole(TestFixtures.USER_ID, RoleName.BACK_OFFICE))
                .thenReturn(TestFixtures.user(RoleName.BACK_OFFICE, TestFixtures.BRANCH_ID));
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);

        assertThatThrownBy(() -> service.submitVerification(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This application belongs to a different branch")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(verificationRepository);
        assertThat(application.getStatus()).isEqualTo(LoanStatus.PENDING_BACK_OFFICE);
    }

    @Test
    @DisplayName("a successful call moves the application to VERIFIED")
    void successfulCallVerifies() {
        LoanApplication application = stubLookups(LoanStatus.PENDING_BACK_OFFICE);
        when(mapper.toVerificationResponse(any())).thenReturn(mock(LoanVerificationResponse.class));

        service.submitVerification(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.VERIFIED);
        verify(verificationRepository).save(any(LoanVerification.class));
    }

    @ParameterizedTest(name = "call status \"{0}\" leaves the application pending")
    @ValueSource(strings = {
            CallStatus.NADA_SAMBUNG_TIDAK_DIANGKAT,
            CallStatus.SALAH_SAMBUNG,
    })
    @DisplayName("a failed call is recorded but the application stays put so it can be re-attempted")
    void failedCallLeavesApplicationPending(String callStatus) {
        request.setCallStatus(callStatus);

        LoanApplication application = stubLookups(LoanStatus.PENDING_BACK_OFFICE);
        when(mapper.toVerificationResponse(any())).thenReturn(mock(LoanVerificationResponse.class));

        service.submitVerification(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.PENDING_BACK_OFFICE);
        verify(verificationRepository).save(any(LoanVerification.class));
    }

    @Test
    @DisplayName("the attempt is stored with its note, caller and date")
    void storesTheAttempt() {
        stubLookups(LoanStatus.PENDING_BACK_OFFICE);
        when(mapper.toVerificationResponse(any())).thenReturn(mock(LoanVerificationResponse.class));

        service.submitVerification(TestFixtures.USER_ID, request);

        ArgumentCaptor<LoanVerification> captor = ArgumentCaptor.forClass(LoanVerification.class);
        verify(verificationRepository).save(captor.capture());

        LoanVerification saved = captor.getValue();
        assertThat(saved.getCallStatus()).isEqualTo(CallStatus.CAN_BE_CONTACTED);
        assertThat(saved.getVerificationNote()).isEqualTo("Spoke to the customer, details confirmed");
        assertThat(saved.getVerifiedBy().getUserId()).isEqualTo(TestFixtures.USER_ID);
        assertThat(saved.getVerificationDate()).isNotNull();
    }

    @Test
    @DisplayName("the call status is matched case insensitively")
    void callStatusIsCaseInsensitive() {
        request.setCallStatus("can be contacted");

        LoanApplication application = stubLookups(LoanStatus.PENDING_BACK_OFFICE);
        when(mapper.toVerificationResponse(any())).thenReturn(mock(LoanVerificationResponse.class));

        service.submitVerification(TestFixtures.USER_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.VERIFIED);
    }

    @ParameterizedTest(name = "status {0} cannot be verified")
    @ValueSource(strings = {
            LoanStatus.CHECKING,
            LoanStatus.PENDING_BRANCH_MANAGER,
            LoanStatus.VERIFIED,
            LoanStatus.DISBURSED,
            LoanStatus.REJECTED_BY_BACK_OFFICE,
    })
    @DisplayName("only an application awaiting back office may be verified")
    void rejectsApplicationsNotAwaitingVerification(String status) {
        stubLookups(status);

        assertThatThrownBy(() -> service.submitVerification(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not awaiting back office verification")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a missing call status is rejected before anything is looked up")
    void missingCallStatusFailsFast() {
        request.setCallStatus(null);

        assertThatThrownBy(() -> service.submitVerification(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("callStatus is required");

        verifyNoInteractions(applicationService, verificationRepository);
    }

    @ParameterizedTest(name = "call status \"{0}\" is not allowed")
    @ValueSource(strings = {"Busy", "no answer", "", "CONTACTED"})
    @DisplayName("an unknown call status names the three that are allowed")
    void rejectsUnknownCallStatus(String callStatus) {
        request.setCallStatus(callStatus);

        assertThatThrownBy(() -> service.submitVerification(TestFixtures.USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("callStatus must be one of")
                .hasMessageContaining(CallStatus.CAN_BE_CONTACTED);

        verifyNoInteractions(applicationService, verificationRepository);
    }

    @Test
    @DisplayName("every attempt on an application is listed, newest first")
    void listsEveryAttempt() {
        LoanVerification first = new LoanVerification();
        LoanVerification second = new LoanVerification();
        LoanVerificationResponse firstResponse = mock(LoanVerificationResponse.class);
        LoanVerificationResponse secondResponse = mock(LoanVerificationResponse.class);

        when(verificationRepository
                .findByApplication_ApplicationIdOrderByVerificationDateDesc(TestFixtures.APPLICATION_ID))
                .thenReturn(List.of(first, second));
        when(mapper.toVerificationResponse(first)).thenReturn(firstResponse);
        when(mapper.toVerificationResponse(second)).thenReturn(secondResponse);

        assertThat(service.listByApplication(TestFixtures.APPLICATION_ID))
                .containsExactly(firstResponse, secondResponse);
    }

    @Test
    @DisplayName("an application with no attempts yields an empty list, not null")
    void listIsEmptyWhenNoAttempts() {
        when(verificationRepository
                .findByApplication_ApplicationIdOrderByVerificationDateDesc("APP-404"))
                .thenReturn(List.of());

        assertThat(service.listByApplication("APP-404")).isEmpty();
    }
}
