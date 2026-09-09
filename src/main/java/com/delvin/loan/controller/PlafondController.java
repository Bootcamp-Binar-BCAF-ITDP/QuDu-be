package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.PaginationUtil;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.plafond.PlafondRequest;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.service.PlafondService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/plafonds")
@RequiredArgsConstructor
public class PlafondController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("plafondId", "maxAmount", "minAmount", "level", "adminFee", "minTenor", "maxtenor", "interestRate");

    private final PlafondService plafondService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PlafondResponse>>> getAll(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "plafondId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search) {

        Pageable pageable = PaginationUtil.build(page, size, sortBy, sortDir, SORTABLE_FIELDS, "roleId");

        PageResponse<PlafondResponse> plafonds = plafondService.getAll(search, pageable);

        if (plafonds.getTotalElements() == 0) {
            String message = search.isBlank() ? "No plafond data found": "No Plafond matches your search";
            return ResponseUtil.success(message, plafonds);
        }

        return ResponseUtil.success("Plafonds retrieved successfully", plafonds);
    }

    /**
     * Public rate card, read by the customer app's loan simulator.
     *
     * Sits above the /{plafondId} mapping only for readability - Spring matches
     * the literal path first either way. Permitted without a login through
     * SecurityRoutes.PUBLIC, which is evaluated before the SUPERADMIN rule that
     * covers the rest of /api/plafonds/**.
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<PlafondResponse>>> catalog() {
        return ResponseUtil.success("Plafond catalog retrieved successfully",
                plafondService.catalog());
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