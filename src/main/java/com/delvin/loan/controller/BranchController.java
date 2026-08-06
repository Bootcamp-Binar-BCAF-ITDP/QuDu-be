package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.branch.BranchRequest;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.service.BranchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getAllBranches() {

        List<BranchResponse> branches = branchService.getAllBranches();

        if (branches.isEmpty()) {
            return ResponseUtil.success("No branch data found", branches);
        }

        return ResponseUtil.success("Get branch successfully", branches);
    }

    @GetMapping("/{branchCode}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(@PathVariable String branchCode) {

        try {

            BranchResponse response = branchService.getBranchById(branchCode);

            return ResponseUtil.success("Branch found", response);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());

        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@RequestBody BranchRequest request) {

        try {

            branchService.createBranch(request);

            return ResponseUtil.created("Branch created successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            return ResponseUtil.error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred."
            );
        }
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(@PathVariable Integer id, @RequestBody BranchRequest request) {

        try {

            branchService.updateBranch(id, request);

            return ResponseUtil.success("Branch updated successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());

        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> deleteBranch(@PathVariable Integer id) {

        try {

            branchService.deleteBranch(id);

            return ResponseUtil.success("Branch deleted successfully", null);

        } catch (RuntimeException e) {

            return ResponseUtil.error(HttpStatus.NOT_FOUND, e.getMessage());

        }
    }
}