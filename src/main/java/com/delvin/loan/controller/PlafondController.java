package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.plafond.PlafondRequest;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.service.PlafondService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plafond master data. Customer facing endpoints live in CustomerController,
 * approval endpoints in BranchManagerController.
 */
@RestController
@RequestMapping("/api/plafonds")
@RequiredArgsConstructor
public class PlafondController {

    private final PlafondService plafondService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlafondResponse>>> getAll(
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        return ResponseUtil.success("Plafonds retrieved successfully", plafondService.getAll(activeOnly));
    }

    @GetMapping("/{plafondId}")
    public ResponseEntity<ApiResponse<PlafondResponse>> getById(@PathVariable Integer plafondId) {
        return ResponseUtil.success("Plafond retrieved successfully", plafondService.getById(plafondId));
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<ApiResponse<PlafondResponse>> getByLevel(@PathVariable Integer level) {
        return ResponseUtil.success("Plafond retrieved successfully", plafondService.getByLevel(level));
    }

    @GetMapping("/simulate")
    public ResponseEntity<ApiResponse<PlafondResponse>> simulate(@RequestParam BigDecimal amount) {
        return ResponseUtil.success("Matching plafond found", plafondService.simulate(amount));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlafondResponse>> create(@Valid @RequestBody PlafondRequest request) {
        return ResponseUtil.created("Plafond created successfully", plafondService.create(request));
    }

    @PutMapping("/{plafondId}")
    public ResponseEntity<ApiResponse<PlafondResponse>> update(@PathVariable Integer plafondId,
                                                               @Valid @RequestBody PlafondRequest request) {
        return ResponseUtil.success("Plafond updated successfully", plafondService.update(plafondId, request));
    }

    @DeleteMapping("/{plafondId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer plafondId) {
        plafondService.delete(plafondId);
        return ResponseUtil.success("Plafond deactivated successfully", null);
    }
}