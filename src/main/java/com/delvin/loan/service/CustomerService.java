package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class CustomerService {

    private final LoanApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final LoanMapper mapper;

    public CustomerService(LoanApplicationRepository applicationRepository, CustomerRepository customerRepository, LoanMapper mapper) {
        this.applicationRepository = applicationRepository;
        this.customerRepository = customerRepository;
        this.mapper = mapper;
    }


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

    // Helper
    private String generateApplicationId() {
        return "LA-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
