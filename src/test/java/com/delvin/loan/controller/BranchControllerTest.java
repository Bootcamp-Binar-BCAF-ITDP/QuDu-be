package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.branch.BranchRequest;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.service.BranchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchControllerTest {

    @Mock
    private BranchService branchService;

    @InjectMocks
    private BranchController controller;

    private PageResponse<BranchResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true);
    }

    private PageResponse<BranchResponse> onePage() {
        BranchResponse branch = new BranchResponse();
        branch.setBranchId(1);
        branch.setBranchCode("BR-1");
        return new PageResponse<>(List.of(branch), 0, 10, 1, 1, true, true, false);
    }

    @Test
    @DisplayName("a populated list is returned with the success message")
    void listReturnsBranches() {
        when(branchService.getAllBranches(eq(""), any(Pageable.class))).thenReturn(onePage());

        ResponseEntity<ApiResponse<PageResponse<BranchResponse>>> result =
                controller.getAllBranches(0, 10, "branchId", "asc", "");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Get branch successfully");
        assertThat(result.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("an empty result with no search term says there is no data at all")
    void emptyWithoutSearchSaysNoData() {
        when(branchService.getAllBranches(eq(""), any(Pageable.class))).thenReturn(emptyPage());

        ResponseEntity<ApiResponse<PageResponse<BranchResponse>>> result =
                controller.getAllBranches(0, 10, "branchId", "asc", "");

        assertThat(result.getBody().getMessage()).isEqualTo("No branch data found");
    }

    @Test
    @DisplayName("an empty result with a search term blames the search, not the data")
    void emptyWithSearchBlamesTheSearch() {
        when(branchService.getAllBranches(eq("zzz"), any(Pageable.class))).thenReturn(emptyPage());

        ResponseEntity<ApiResponse<PageResponse<BranchResponse>>> result =
                controller.getAllBranches(0, 10, "branchId", "asc", "zzz");

        assertThat(result.getBody().getMessage()).isEqualTo("No branch matches your search");
    }

    @Test
    @DisplayName("an unsortable field falls back to the default rather than failing")
    void unsortableFieldFallsBack() {
        when(branchService.getAllBranches(eq(""), any(Pageable.class))).thenReturn(onePage());

        controller.getAllBranches(0, 10, "dropTable", "asc", "");

        verify(branchService).getAllBranches(eq(""), any(Pageable.class));
    }

    @Test
    @DisplayName("a branch is returned by code")
    void getByCodeReturnsBranch() {
        BranchResponse branch = new BranchResponse();
        branch.setBranchCode("BR-1");
        when(branchService.getBranchById("BR-1")).thenReturn(branch);

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.getBranchById("BR-1");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch found");
    }

    @Test
    @DisplayName("a missing branch is caught here and turned into a 404 body")
    void missingBranchBecomes404() {
        when(branchService.getBranchById("nope")).thenThrow(new RuntimeException("Branch not found"));

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.getBranchById("nope");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch not found");
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("options are returned as a plain list")
    void optionsReturnList() {
        when(branchService.getBranchOptions()).thenReturn(List.of(new BranchResponse()));

        ResponseEntity<ApiResponse<List<BranchResponse>>> result = controller.getBranchOptions();

        assertThat(result.getBody().getMessage()).isEqualTo("Branch options retrieved");
        assertThat(result.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("a created branch answers 201 with no body payload")
    void createReturns201() {
        BranchRequest request = new BranchRequest();
        when(branchService.createBranch(request)).thenReturn(new BranchResponse());

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.createBranch(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch created successfully");
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("a rejected creation answers 400 carrying the reason")
    void createFailureBecomes400() {
        BranchRequest request = new BranchRequest();
        when(branchService.createBranch(request))
                .thenThrow(new RuntimeException("Branch code already exists."));

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.createBranch(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch code already exists.");
    }

    @Test
    @DisplayName("an update answers 200")
    void updateReturns200() {
        BranchRequest request = new BranchRequest();
        when(branchService.updateBranch(1, request)).thenReturn(new BranchResponse());

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.updateBranch(1, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch updated successfully");
    }

    @Test
    @DisplayName("updating a missing branch answers 404")
    void updateMissingBecomes404() {
        BranchRequest request = new BranchRequest();
        when(branchService.updateBranch(404, request))
                .thenThrow(new RuntimeException("Branch not found"));

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.updateBranch(404, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a delete answers 200")
    void deleteReturns200() {
        when(branchService.deleteBranch(1)).thenReturn(new BranchResponse());

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.deleteBranch(1);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch deleted successfully");
    }

    @Test
    @DisplayName("deleting a missing branch answers 404")
    void deleteMissingBecomes404() {
        doThrow(new RuntimeException("Branch not found")).when(branchService).deleteBranch(404);

        ResponseEntity<ApiResponse<BranchResponse>> result = controller.deleteBranch(404);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("Branch not found");
    }
}
