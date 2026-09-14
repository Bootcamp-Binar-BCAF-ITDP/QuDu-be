package com.delvin.loan.service;

import com.delvin.loan.common.CallStatus;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanDisbursementRequest;
import com.delvin.loan.dto.response.loanresp.LoanDisbursementResponse;
import com.delvin.loan.event.LoanStatusChangedEvent;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDisbursement;
import com.delvin.loan.model.LoanVerification;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.LoanDisbursementRepository;
import com.delvin.loan.repository.LoanVerificationRepository;
import com.delvin.loan.repository.UserRepository;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanDisbursementServiceTest {

    private static final int BRANCH = 7;
    private static final String BACK_OFFICE_ID = RoleName.BACK_OFFICE + "-" + BRANCH;

    @Mock
    private LoanDisbursementRepository disbursementRepository;

    @Mock
    private LoanVerificationRepository verificationRepository;

    @Mock
    private LoanApplicationService applicationService;

    @Mock
    private LoanMapper mapper;

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    @InjectMocks
    private LoanDisbursementService service;

    private LoanDisbursementRequest request;

    @BeforeEach
    void setUp() {
        request = new LoanDisbursementRequest();
        request.setApplicationId(TestFixtures.APPLICATION_ID);
        request.setApprove(true);
        request.setNote("Funds released");
    }

    private LoanApplication stubUpToBranchCheck(String status) {
        User backOffice = TestFixtures.user(RoleName.BACK_OFFICE, BRANCH);
        LoanApplication application = TestFixtures.reviewedApplication(status, BRANCH);

        when(applicationService.getUserWithRole(BACK_OFFICE_ID, RoleName.BACK_OFFICE))
                .thenReturn(backOffice);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);

        return application;
    }

    private void stubSuccessfulCall() {
        when(verificationRepository
                .findFirstByApplication_ApplicationIdAndCallStatusOrderByVerificationDateDesc(
                        TestFixtures.APPLICATION_ID, CallStatus.CAN_BE_CONTACTED))
                .thenReturn(Optional.of(new LoanVerification()));
    }

    private LoanApplication stubHappyPath() {
        LoanApplication application = stubUpToBranchCheck(LoanStatus.VERIFIED);
        when(disbursementRepository.existsByApplication_ApplicationId(TestFixtures.APPLICATION_ID))
                .thenReturn(false);
        stubSuccessfulCall();
        when(mapper.toDisbursementResponse(any())).thenReturn(mock(LoanDisbursementResponse.class));
        return application;
    }

    @Test
    @DisplayName("approval moves the application to DISBURSED and records the full amount")
    void approvalDisburses() {
        LoanApplication application = stubHappyPath();

        service.disburse(BACK_OFFICE_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.DISBURSED);

        ArgumentCaptor<LoanDisbursement> captor = ArgumentCaptor.forClass(LoanDisbursement.class);
        verify(disbursementRepository).save(captor.capture());

        LoanDisbursement saved = captor.getValue();
        assertThat(saved.getDecision()).isEqualTo("APPROVED");
        assertThat(saved.getDisbursedAmount())
                .isEqualByComparingTo(application.getRequestedAmount());
        verify(applicationRepository).save(application);
    }

    @Test
    @DisplayName("the payout account is snapshotted from the application, not left to the caller")
    void snapshotsPayoutAccount() {
        LoanApplication application = stubHappyPath();

        service.disburse(BACK_OFFICE_ID, request);

        ArgumentCaptor<LoanDisbursement> captor = ArgumentCaptor.forClass(LoanDisbursement.class);
        verify(disbursementRepository).save(captor.capture());

        LoanDisbursement saved = captor.getValue();
        assertThat(saved.getBankName()).isEqualTo(application.getBank());
        assertThat(saved.getAccountNumber()).isEqualTo(application.getBankAccountNumber());
        assertThat(saved.getAccountName()).isEqualTo(application.getBankAccountName());
    }

    @Test
    @DisplayName("an approved disbursement publishes an approved event carrying the disbursed amount")
    void approvalPublishesApprovedEvent() {
        LoanApplication application = stubHappyPath();

        service.disburse(BACK_OFFICE_ID, request);

        ArgumentCaptor<LoanStatusChangedEvent> captor =
                ArgumentCaptor.forClass(LoanStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());

        LoanStatusChangedEvent published = captor.getValue();
        assertThat(published.approved()).isTrue();
        assertThat(published.status()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(published.amount()).isEqualByComparingTo(application.getRequestedAmount());
        assertThat(published.bankName()).isEqualTo("BCA");
    }

    @Test
    @DisplayName("rejection ends the application and records no disbursed amount")
    void rejectionRecordsNoAmount() {
        request.setApprove(false);
        request.setNote("Account name does not match the passbook");

        LoanApplication application = stubHappyPath();

        service.disburse(BACK_OFFICE_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.REJECTED_BY_BACK_OFFICE);

        ArgumentCaptor<LoanDisbursement> captor = ArgumentCaptor.forClass(LoanDisbursement.class);
        verify(disbursementRepository).save(captor.capture());

        LoanDisbursement saved = captor.getValue();
        assertThat(saved.getDecision()).isEqualTo("REJECTED");
        assertThat(saved.getDisbursedAmount()).isNull();
    }

    @Test
    @DisplayName("a rejection event carries the requested amount, since nothing was disbursed")
    void rejectionEventCarriesRequestedAmount() {
        request.setApprove(false);
        request.setNote("Account name does not match the passbook");

        LoanApplication application = stubHappyPath();

        service.disburse(BACK_OFFICE_ID, request);

        ArgumentCaptor<LoanStatusChangedEvent> captor =
                ArgumentCaptor.forClass(LoanStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());

        LoanStatusChangedEvent published = captor.getValue();
        assertThat(published.approved()).isFalse();
        assertThat(published.amount()).isEqualByComparingTo(application.getRequestedAmount());
        assertThat(published.decisionNote()).isEqualTo("Account name does not match the passbook");
    }

    @Test
    @DisplayName("a null approve flag is treated as a rejection, so it still needs a note")
    void nullApproveIsARejection() {
        request.setApprove(null);
        request.setNote(null);

        stubUpToBranchCheck(LoanStatus.VERIFIED);
        when(disbursementRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        stubSuccessfulCall();

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A note is required when a disbursement is rejected");
    }

    @Test
    @DisplayName("a rejection with a blank note is refused")
    void rejectionNeedsANote() {
        request.setApprove(false);
        request.setNote("   ");

        stubUpToBranchCheck(LoanStatus.VERIFIED);
        when(disbursementRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        stubSuccessfulCall();

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A note is required when a disbursement is rejected")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(disbursementRepository, never()).save(any());
    }

    @ParameterizedTest(name = "status {0} cannot be disbursed")
    @ValueSource(strings = {
            LoanStatus.CHECKING,
            LoanStatus.PENDING_BRANCH_MANAGER,
            LoanStatus.PENDING_BACK_OFFICE,
            LoanStatus.DISBURSED,
            LoanStatus.REJECTED_BY_BRANCH_MANAGER,
    })
    @DisplayName("only a VERIFIED application may be disbursed")
    void rejectsApplicationNotVerified(String status) {
        stubUpToBranchCheck(status);

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not ready for disbursement")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(disbursementRepository, never()).save(any());
    }

    @Test
    @DisplayName("an application cannot be decided twice")
    void rejectsSecondDecision() {
        stubUpToBranchCheck(LoanStatus.VERIFIED);
        when(disbursementRepository.existsByApplication_ApplicationId(TestFixtures.APPLICATION_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a disbursement decision");
    }

    @Test
    @DisplayName("an application with no marketing review cannot be disbursed")
    void rejectsApplicationWithoutReview() {
        User backOffice = TestFixtures.user(RoleName.BACK_OFFICE, BRANCH);
        LoanApplication application = TestFixtures.application(LoanStatus.VERIFIED);

        when(applicationService.getUserWithRole(BACK_OFFICE_ID, RoleName.BACK_OFFICE))
                .thenReturn(backOffice);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);
        when(disbursementRepository.existsByApplication_ApplicationId(any())).thenReturn(false);

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no marketing review on file");
    }

    @Test
    @DisplayName("back office cannot disburse another branch's application")
    void rejectsCrossBranchDisbursement() {
        User backOffice = TestFixtures.user(RoleName.BACK_OFFICE, BRANCH);
        LoanApplication application = TestFixtures.reviewedApplication(LoanStatus.VERIFIED, BRANCH + 1);

        when(applicationService.getUserWithRole(BACK_OFFICE_ID, RoleName.BACK_OFFICE))
                .thenReturn(backOffice);
        when(applicationService.getApplicationOrThrow(TestFixtures.APPLICATION_ID))
                .thenReturn(application);
        when(disbursementRepository.existsByApplication_ApplicationId(any())).thenReturn(false);

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This application belongs to a different branch")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("money cannot move without a successful verification call on file")
    void rejectsWithoutSuccessfulCall() {
        stubUpToBranchCheck(LoanStatus.VERIFIED);
        when(disbursementRepository.existsByApplication_ApplicationId(any())).thenReturn(false);
        when(verificationRepository
                .findFirstByApplication_ApplicationIdAndCallStatusOrderByVerificationDateDesc(
                        any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disburse(BACK_OFFICE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No successful verification call on file");

        verify(disbursementRepository, never()).save(any());
    }

    @Test
    @DisplayName("an application with no customer is still decided, but notifies nobody")
    void missingCustomerSkipsNotification() {
        LoanApplication application = stubHappyPath();
        application.setCustomer(null);

        service.disburse(BACK_OFFICE_ID, request);

        assertThat(application.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        verify(disbursementRepository).save(any());
        verify(events, never()).publishEvent(any(LoanStatusChangedEvent.class));
    }

    @Test
    @DisplayName("reading a disbursement that does not exist is a 404")
    void getByApplicationNotFound() {
        when(disbursementRepository.findByApplication_ApplicationId("APP-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByApplication("APP-404"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No disbursement found for application APP-404")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getUserWithRole rejects an unknown user")
    void getUserWithRoleRejectsUnknownUser() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserWithRole("nobody", RoleName.BACK_OFFICE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found: nobody");
    }

    @Test
    @DisplayName("getUserWithRole rejects a user holding the wrong role")
    void getUserWithRoleRejectsWrongRole() {
        when(userRepository.findById(TestFixtures.USER_ID))
                .thenReturn(Optional.of(TestFixtures.user(RoleName.MARKETING)));

        assertThatThrownBy(() -> service.getUserWithRole(TestFixtures.USER_ID, RoleName.BACK_OFFICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not have the required role")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getUserWithRole matches the role name case insensitively")
    void getUserWithRoleIsCaseInsensitive() {
        User user = TestFixtures.user("back_office");
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.getUserWithRole(TestFixtures.USER_ID, RoleName.BACK_OFFICE))
                .isSameAs(user);
    }

    @Test
    @DisplayName("a disbursed amount is never guessed: it always equals what was requested")
    void disbursedAmountMatchesRequested() {
        LoanApplication application = stubHappyPath();
        application.setRequestedAmount(BigDecimal.valueOf(7_250_000));

        service.disburse(BACK_OFFICE_ID, request);

        ArgumentCaptor<LoanDisbursement> captor = ArgumentCaptor.forClass(LoanDisbursement.class);
        verify(disbursementRepository).save(captor.capture());

        assertThat(captor.getValue().getDisbursedAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(7_250_000));
    }
}
