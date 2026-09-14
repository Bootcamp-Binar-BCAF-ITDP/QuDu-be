package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.UserRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoanMapper mapper;

    @Mock
    private CreditScoreService creditScoreService;

    @InjectMocks
    private LoanApplicationService service;

    private Page<LoanApplication> onePage() {
        return new PageImpl<>(List.of(TestFixtures.checkingApplication()), PAGE, 1);
    }

    private void stubMapper() {
        when(mapper.toApplicationResponse(any())).thenReturn(mock(LoanApplicationResponse.class));
    }

    private void stubFilter() {
        when(applicationRepository.filter(any(), any(), any(), any(), eq(PAGE))).thenReturn(onePage());
        stubMapper();
    }

    private record FilterArgs(
            Collection<String> statuses, String term, LocalDate from, LocalDate to) {}

    @SuppressWarnings("unchecked")
    private FilterArgs captureFilter() {
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> term = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);

        verify(applicationRepository)
                .filter(statuses.capture(), term.capture(), from.capture(), to.capture(), eq(PAGE));

        return new FilterArgs(
                statuses.getValue(), term.getValue(), from.getValue(), to.getValue());
    }

    @Test
    @DisplayName("no filter and no search term asks for every status across all time")
    void noFilterNoSearchReadsEverything() {
        stubFilter();

        service.getAllApplication(null, null, null, null, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.statuses()).containsExactlyInAnyOrder(
                LoanStatus.CHECKING,
                LoanStatus.REJECTED_BY_MARKETING,
                LoanStatus.PENDING_BRANCH_MANAGER,
                LoanStatus.REJECTED_BY_BRANCH_MANAGER,
                LoanStatus.PENDING_BACK_OFFICE,
                LoanStatus.VERIFIED,
                LoanStatus.DISBURSED,
                LoanStatus.REJECTED_BY_BACK_OFFICE);
        assertThat(args.term()).isEqualTo("%");
        assertThat(args.from()).isEqualTo(LoanApplicationService.EARLIEST);
        assertThat(args.to()).isEqualTo(LoanApplicationService.LATEST);
    }

    @Test
    @DisplayName("a status filter narrows the statuses and leaves the rest wide open")
    void statusFilterNarrowsStatuses() {
        stubFilter();

        service.getAllApplication(List.of("checking"), "  ", null, null, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.statuses()).containsExactly(LoanStatus.CHECKING);
        assertThat(args.term()).isEqualTo("%");
    }

    @Test
    @DisplayName("a search term is wrapped for LIKE and lowercased")
    void searchTermIsWrapped() {
        stubFilter();

        service.getAllApplication(null, "BuDi", null, null, PAGE);

        assertThat(captureFilter().term()).isEqualTo("%budi%");
    }

    @ParameterizedTest(name = "status {0} is normalised to {1}")
    @CsvSource({
            "checking,CHECKING",
            "  disbursed  ,DISBURSED",
            "Verified,VERIFIED",
    })
    @DisplayName("a status is trimmed and uppercased before it reaches the query")
    void statusIsNormalised(String raw, String expected) {
        stubFilter();

        service.getAllApplication(List.of(raw), null, null, null, PAGE);

        assertThat(captureFilter().statuses()).containsExactly(expected);
    }

    @Test
    @DisplayName("blank and null statuses are dropped, which widens back to every status")
    void blankStatusesAreDropped() {
        stubFilter();

        service.getAllApplication(Arrays.asList(null, "", "   "), null, null, null, PAGE);

        assertThat(captureFilter().statuses()).hasSize(8);
    }

    @Test
    @DisplayName("a repeated status is only asked for once")
    void duplicateStatusesAreCollapsed() {
        stubFilter();

        service.getAllApplication(
                List.of("CHECKING", "checking", " Checking "), null, null, null, PAGE);

        assertThat(captureFilter().statuses()).containsExactly(LoanStatus.CHECKING);
    }

    @Test
    @DisplayName("an unknown status is refused and the allowed set is named")
    void unknownStatusIsRefused() {
        assertThatThrownBy(
                () -> service.getAllApplication(List.of("PENDING"), null, null, null, PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status: PENDING")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(applicationRepository);
    }

    @ParameterizedTest(name = "search {0} is escaped so the wildcard is matched literally")
    @CsvSource({
            "100%,'%100\\%%'",
            "a_b,'%a\\_b%'",
    })
    @DisplayName("LIKE wildcards inside a search term are escaped, not honoured")
    void likeWildcardsAreEscaped(String raw, String expected) {
        stubFilter();

        service.getAllApplication(null, raw, null, null, PAGE);

        assertThat(captureFilter().term()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a backslash in a search term is escaped first, so the other escapes survive")
    void backslashIsEscapedFirst() {
        stubFilter();

        service.getAllApplication(null, "a\\b", null, null, PAGE);

        assertThat(captureFilter().term()).isEqualTo("%a\\\\b%");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank search term becomes the match-everything wildcard")
    void blankSearchIsNoSearch(String search) {
        stubFilter();

        service.getAllApplication(null, search, null, null, PAGE);

        assertThat(captureFilter().term()).isEqualTo("%");
    }

    @Test
    @DisplayName("both dates are passed through as given")
    void dateWindowIsPassedThrough() {
        stubFilter();

        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        service.getAllApplication(null, null, from, to, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.from()).isEqualTo(from);
        assertThat(args.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("a lower bound alone leaves the upper bound open")
    void openEndedUpperBound() {
        stubFilter();

        LocalDate from = LocalDate.of(2026, 3, 1);
        service.getAllApplication(null, null, from, null, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.from()).isEqualTo(from);
        assertThat(args.to()).isEqualTo(LoanApplicationService.LATEST);
    }

    @Test
    @DisplayName("an upper bound alone leaves the lower bound open")
    void openEndedLowerBound() {
        stubFilter();

        LocalDate to = LocalDate.of(2026, 3, 31);
        service.getAllApplication(null, null, null, to, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.from()).isEqualTo(LoanApplicationService.EARLIEST);
        assertThat(args.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("a single day window is allowed, since from and to are both inclusive")
    void singleDayWindowIsAllowed() {
        stubFilter();

        LocalDate day = LocalDate.of(2026, 3, 15);
        service.getAllApplication(null, null, day, day, PAGE);

        FilterArgs args = captureFilter();
        assertThat(args.from()).isEqualTo(day);
        assertThat(args.to()).isEqualTo(day);
    }

    @Test
    @DisplayName("a window that runs backwards is refused rather than quietly returning nothing")
    void backwardsWindowIsRefused() {
        assertThatThrownBy(() -> service.getAllApplication(
                null, null, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1), PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("from cannot be after to")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("the marketing bucket is every application still in CHECKING")
    void marketingBucketIsCheckingApplications() {
        when(applicationRepository.findByStatus(LoanStatus.CHECKING, PAGE)).thenReturn(onePage());
        stubMapper();

        service.listMarketingBucket(PAGE);

        verify(applicationRepository).findByStatus(LoanStatus.CHECKING, PAGE);
    }

    @Test
    @DisplayName("the branch manager bucket is scoped to the reviewer's branch")
    void branchManagerBucketIsBranchScoped() {
        User manager = TestFixtures.user(RoleName.BRANCH_MANAGER, 5);
        when(userRepository.findById(manager.getUserId())).thenReturn(Optional.of(manager));
        when(applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                LoanStatus.PENDING_BRANCH_MANAGER, 5, PAGE)).thenReturn(onePage());
        stubMapper();

        service.listBranchManagerBucket(manager.getUserId(), PAGE);

        verify(applicationRepository).findByStatusAndReview_Marketing_Branch_BranchId(
                LoanStatus.PENDING_BRANCH_MANAGER, 5, PAGE);
    }

    @Test
    @DisplayName("the back office bucket is scoped to the reviewer's branch")
    void backOfficeBucketIsBranchScoped() {
        User backOffice = TestFixtures.user(RoleName.BACK_OFFICE, 5);
        when(userRepository.findById(backOffice.getUserId())).thenReturn(Optional.of(backOffice));
        when(applicationRepository.findByStatusAndReview_Marketing_Branch_BranchId(
                LoanStatus.PENDING_BACK_OFFICE, 5, PAGE)).thenReturn(onePage());
        stubMapper();

        service.listBackOfficeBucket(backOffice.getUserId(), PAGE);

        verify(applicationRepository).findByStatusAndReview_Marketing_Branch_BranchId(
                LoanStatus.PENDING_BACK_OFFICE, 5, PAGE);
    }

    @Test
    @DisplayName("a staff member with no branch cannot open a bucket")
    void bucketNeedsABranch() {
        User manager = TestFixtures.user(RoleName.BRANCH_MANAGER);
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.listBranchManagerBucket(TestFixtures.USER_ID, PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no branch assigned");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a principal with no role has no bucket")
    void noRoleMeansNoBucket(String roleName) {
        assertThatThrownBy(() -> service.getMyBucket(TestFixtures.USER_ID, roleName, PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User has no role assigned")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a role with no bucket of its own is refused by name")
    void unknownRoleHasNoBucket() {
        assertThatThrownBy(() -> service.getMyBucket(TestFixtures.USER_ID, RoleName.CUSTOMER, PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no application bucket");
    }

    @Test
    @DisplayName("the role name is matched case insensitively when routing to a bucket")
    void bucketRoutingIsCaseInsensitive() {
        when(applicationRepository.findByStatus(LoanStatus.CHECKING, PAGE)).thenReturn(onePage());
        stubMapper();

        service.getMyBucket(TestFixtures.USER_ID, "  marketing  ", PAGE);

        verify(applicationRepository).findByStatus(LoanStatus.CHECKING, PAGE);
    }

    @Test
    @DisplayName("applications are listed for one customer only")
    void listsApplicationsForOneCustomer() {
        when(applicationRepository.findByCustomer_CustomerId(TestFixtures.CUSTOMER_ID, PAGE))
                .thenReturn(onePage());
        stubMapper();

        assertThat(service.listByCustomer(TestFixtures.CUSTOMER_ID, PAGE).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("an unknown application id is a 404")
    void unknownApplicationIsNotFound() {
        when(applicationRepository.findById("APP-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplication("APP-404"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Loan application not found: APP-404")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getUserWithRole rejects an unknown user")
    void getUserWithRoleRejectsUnknownUser() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserWithRole("nobody", RoleName.MARKETING))
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
    @DisplayName("getUserWithRole rejects a user carrying no role at all")
    void getUserWithRoleRejectsRolelessUser() {
        User bare = new User();
        bare.setUserId(TestFixtures.USER_ID);
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(bare));

        assertThatThrownBy(() -> service.getUserWithRole(TestFixtures.USER_ID, RoleName.MARKETING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not have the required role");
    }
}
