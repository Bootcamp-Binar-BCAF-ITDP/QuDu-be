package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.model.Branch;
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
    public ResponseEntity<ApiResponse<List<Branch>>> getAllBranches() {

        List<Branch> branches = branchService.getAllBranches();

        if (branches.isEmpty()) {
            return ResponseUtil.success("No branch data found", branches);
        }

        return ResponseUtil.success("Branches retrieved successfully", branches);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Branch>> getBranchById(@PathVariable String id) {

        return branchService.getBranchById(id)
                .map(branch ->
                        ResponseUtil.success("Branch found", branch))
                .orElseGet(() ->
                        ResponseUtil.error(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Branch>> createBranch(@RequestBody Branch branch) {

        branchService.createBranch(branch);

        return ResponseUtil.created("Branch created successfully", null);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<Branch>> updateBranch(
            @PathVariable String id,
            @RequestBody Branch branch) {

        Branch updatedBranch = branchService.updateBranch(id, branch);

        return ResponseUtil.success("Branch updated successfully", updatedBranch);
    }

    @PutMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteBranch(@PathVariable String id) {

        branchService.deleteBranch(id);

        return ResponseUtil.success("Branch deleted successfully", null);
    }
}