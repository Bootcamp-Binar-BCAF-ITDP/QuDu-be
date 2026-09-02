package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.request.plafond.PlafondUpgradeRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.CustomerPlafondResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.CustomerService;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.PlafondService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    private final LoanApplicationService applicationService;
    private final PlafondService plafondService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> create(
            @Valid @RequestBody LoanApplicationCreateRequest request) {

        try {
            return ResponseUtil.created(
                "Loan application submitted",
                customerService.createApplication(request));
        } catch (Exception e) {
           return  ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> listByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseUtil.success(
                "Loan applications retrieved",
                applicationService.listByCustomer(customerId, pageable));
    }


    // PLAFOND
    @GetMapping("/plafond")
    public ResponseEntity<ApiResponse<CustomerPlafondResponse>> getMyPlafond(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Customer plafond retrieved successfully",
                customerService.getMyPlafond(appUser.getUserId()));
    }

    /** Submit a limit request. Waits for a branch manager decision. */
    @PostMapping("/plafond")
    public ResponseEntity<ApiResponse<PlafondRequestResponse>> requestPlafond(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody PlafondUpgradeRequest request) {

        return ResponseUtil.created("Plafond request submitted successfully",
                customerService.requestPlafond(appUser.getUserId(), request));
    }

    /** History of the customer's own limit requests. */
    @GetMapping("/plafond/requests")
    public ResponseEntity<ApiResponse<List<PlafondRequestResponse>>> myPlafondRequests(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Plafond requests retrieved successfully",
                customerService.getMyPlafondRequests(appUser.getUserId()));
    }
}
