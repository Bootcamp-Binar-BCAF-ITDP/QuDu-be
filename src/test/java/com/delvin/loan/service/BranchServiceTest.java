package com.delvin.loan.service;

import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.branch.BranchRequest;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private BranchService service;

    private BranchRequest request;

    @BeforeEach
    void setUp() {
        request = new BranchRequest();
        request.setBranchCode("BR-99");
        request.setBranchName("Bekasi");
        request.setLocation("Bekasi");
        request.setEmail("bekasi@example.com");
        request.setPhoneNumber("02100000000");
        request.setIsActive(true);
    }

    private Page<Branch> onePage() {
        return new PageImpl<>(List.of(TestFixtures.branch(1)), PAGE, 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank search becomes a match-everything keyword")
    void blankSearchMatchesEverything(String search) {
        when(branchRepository.searchActive(any(), eq(PAGE))).thenReturn(onePage());

        service.getAllBranches(search, PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(branchRepository).searchActive(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%%");
    }

    @Test
    @DisplayName("a search term is trimmed, lowercased and wrapped for LIKE")
    void searchTermIsNormalised() {
        when(branchRepository.searchActive(any(), eq(PAGE))).thenReturn(onePage());

        service.getAllBranches("  JaKarTa  ", PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(branchRepository).searchActive(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%jakarta%");
    }

    @Test
    @DisplayName("the page is mapped into the shared envelope")
    void mapsPageIntoEnvelope() {
        when(branchRepository.searchActive(any(), eq(PAGE))).thenReturn(onePage());

        PageResponse<BranchResponse> result = service.getAllBranches(null, PAGE);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBranchCode()).isEqualTo("BR-1");
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.isFirst()).isTrue();
    }

    @Test
    @DisplayName("options list only active branches")
    void optionsListOnlyActiveBranches() {
        when(branchRepository.findByIsActiveOrderByBranchNameAsc(true))
                .thenReturn(List.of(TestFixtures.branch(1), TestFixtures.branch(2)));

        assertThat(service.getBranchOptions())
                .extracting(BranchResponse::getBranchName)
                .containsExactly("Branch 1", "Branch 2");
    }

    @Test
    @DisplayName("a branch is found by its code")
    void findsBranchByCode() {
        when(branchRepository.findByBranchCodeAndIsActive("BR-1", true))
                .thenReturn(Optional.of(TestFixtures.branch(1)));

        assertThat(service.getBranchById("BR-1").getBranchId()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown branch code is refused")
    void unknownBranchCodeIsRefused() {
        when(branchRepository.findByBranchCodeAndIsActive("nope", true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBranchById("nope"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Branch not found");
    }

    @Test
    @DisplayName("a new branch is saved with every field from the request")
    void createsBranch() {
        when(branchRepository.findByBranchCodeAndIsActive("BR-99", true))
                .thenReturn(Optional.empty());
        when(branchRepository.save(any(Branch.class))).thenAnswer(i -> i.getArgument(0));

        BranchResponse result = service.createBranch(request);

        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(captor.capture());

        Branch saved = captor.getValue();
        assertThat(saved.getBranchCode()).isEqualTo("BR-99");
        assertThat(saved.getBranchName()).isEqualTo("Bekasi");
        assertThat(saved.getLocation()).isEqualTo("Bekasi");
        assertThat(saved.getEmail()).isEqualTo("bekasi@example.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("02100000000");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(result.getBranchCode()).isEqualTo("BR-99");
    }

    @Test
    @DisplayName("a duplicate branch code is refused before anything is saved")
    void refusesDuplicateBranchCode() {
        when(branchRepository.findByBranchCodeAndIsActive("BR-99", true))
                .thenReturn(Optional.of(TestFixtures.branch(1)));

        assertThatThrownBy(() -> service.createBranch(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Branch code already exists.");

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("an update leaves the branch code alone, because it is the lookup key")
    void updateDoesNotChangeBranchCode() {
        Branch existing = TestFixtures.branch(1);
        when(branchRepository.findByBranchIdAndIsActive(1, true)).thenReturn(Optional.of(existing));
        when(branchRepository.save(any(Branch.class))).thenAnswer(i -> i.getArgument(0));

        service.updateBranch(1, request);

        assertThat(existing.getBranchCode()).isEqualTo("BR-1");
        assertThat(existing.getBranchName()).isEqualTo("Bekasi");
    }

    @Test
    @DisplayName("updating an unknown branch is refused")
    void updateUnknownBranchIsRefused() {
        when(branchRepository.findByBranchIdAndIsActive(404, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBranch(404, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Branch not found");

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleting a branch only clears its active flag, the row survives")
    void deleteIsASoftDelete() {
        Branch existing = TestFixtures.branch(1);
        when(branchRepository.findByBranchIdAndIsActive(1, true)).thenReturn(Optional.of(existing));
        when(branchRepository.save(any(Branch.class))).thenAnswer(i -> i.getArgument(0));

        BranchResponse result = service.deleteBranch(1);

        assertThat(existing.getIsActive()).isFalse();
        assertThat(result.getIsActive()).isFalse();
        verify(branchRepository).save(existing);
    }

    @Test
    @DisplayName("deleting an unknown branch is refused")
    void deleteUnknownBranchIsRefused() {
        when(branchRepository.findByBranchIdAndIsActive(404, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBranch(404))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Branch not found");
    }
}
