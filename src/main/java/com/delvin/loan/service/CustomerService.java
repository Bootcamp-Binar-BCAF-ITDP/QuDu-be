package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.request.plafond.PlafondUpgradeRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.plafond.CustomerPlafondResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.CustomerPlafondRequest;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.Plafond;
import com.delvin.loan.repository.CustomerPlafondRequestRepository;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.PlafondRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final LoanMapper mapper;
    private final PlafondService plafondService;
    private final CustomerPlafondRequestRepository plafondRequestRepository;

    // APPLICATION
    @Transactional
    public LoanApplicationResponse createApplication(LoanApplicationCreateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + request.getCustomerId()));

        LoanApplication application = new LoanApplication();
        application.setApplicationId(generateApplicationId());
        application.setCustomer(customer);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTenor(request.getTenor());
        application.setPurpose(request.getPurpose());
        application.setIncome(request.getIncome());
        application.setStatus(LoanStatus.CHECKING);
        application.setSubmissionDate(LocalDate.now());
        application.setBank(request.getBank());
        application.setBankAccountName(request.getBankAccountName());
        application.setBankAccountNumber(request.getBankAccountNumber());

        applicationRepository.save(application);
        return mapper.toApplicationResponse(application);
    }

    // PLAFOND
    @Transactional(readOnly = true)
    public CustomerPlafondResponse getMyPlafond(String customerId) {

        Customer customer = findCustomer(customerId);

        return CustomerPlafondResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .approvedLimit(customer.getApprovedLimit())
                .plafond(PlafondResponse.from(customer.getPlafond()))
                .build();
    }

    @Transactional
    public PlafondRequestResponse requestPlafond(String customerId, PlafondUpgradeRequest body) {

        Customer customer = findCustomer(customerId);

        if (plafondRequestRepository.existsByCustomer_CustomerIdAndStatus(customerId, PlafondRequestStatus.PENDING)) {
            throw BusinessException.conflict("You already have a plafond request waiting for a decision");
        }

        Plafond current = customer.getPlafond() != null
                ? customer.getPlafond()
                : plafondService.getDefaultPlafond();

        Plafond target = plafondService.resolveByAmount(body.getRequestedAmount());

        if (target.getLevel() <= current.getLevel()) {
            throw BusinessException.badRequest(
                    "Requested amount " + body.getRequestedAmount()
                            + " is already covered by your current plafond (level " + current.getLevel()
                            + ", up to " + current.getMaxAmount() + ")");
        }

        CustomerPlafondRequest request = new CustomerPlafondRequest();
        request.setRequestId(generatePlafondRequestId());
        request.setCustomer(customer);
        request.setCurrentPlafond(current);
        request.setRequestedPlafond(target);
        request.setRequestedAmount(body.getRequestedAmount());
        request.setStatus(PlafondRequestStatus.PENDING);
        request.setRequestDate(LocalDateTime.now());

        return PlafondRequestResponse.from(plafondRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<PlafondRequestResponse> getMyPlafondRequests(String customerId) {

        findCustomer(customerId);

        return plafondRequestRepository.findByCustomer_CustomerIdOrderByRequestDateDesc(customerId)
                .stream()
                .map(PlafondRequestResponse::from)
                .toList();
    }

    // Helper
    private String generateApplicationId() {
        return "LA-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void validateAgainstPlafond(Customer customer, BigDecimal requestedAmount, Integer tenor) {

        Plafond plafond = customer.getPlafond();

        if (plafond == null) {
            throw BusinessException.badRequest("Customer has no plafond assigned");
        }

        BigDecimal limit = customer.getApprovedLimit() != null
                ? customer.getApprovedLimit()
                : plafond.getMaxAmount();

        if (requestedAmount.compareTo(limit) > 0) {
            throw BusinessException.badRequest(
                    "Requested amount " + requestedAmount + " exceeds your approved limit of " + limit);
        }

        if (tenor < plafond.getMinTenor() || tenor > plafond.getMaxTenor()) {
            throw BusinessException.badRequest(
                    "Tenor must be between " + plafond.getMinTenor() + " and " + plafond.getMaxTenor()
                            + " months for plafond level " + plafond.getLevel());
        }
    }

    private Customer findCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + customerId));
    }

    private String generatePlafondRequestId() {
        return "PR-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
