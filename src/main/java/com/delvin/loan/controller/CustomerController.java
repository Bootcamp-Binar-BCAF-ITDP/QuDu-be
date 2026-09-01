package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.CustomerService;
import com.delvin.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {
    private final CustomerService customerService;
    private final LoanApplicationService applicationService;

    public CustomerController(CustomerService customerService, LoanApplicationService applicationService) {
        this.customerService = customerService;
        this.applicationService = applicationService;
    }

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
}
