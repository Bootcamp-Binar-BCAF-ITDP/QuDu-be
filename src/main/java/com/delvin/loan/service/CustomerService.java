package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.LoanNotificationText;
import com.delvin.loan.common.PlafondRequestStatus;
import com.delvin.loan.dto.request.customer.CustomerProfileUpdateRequest;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.request.plafond.PlafondUpgradeRequest;
import com.delvin.loan.dto.response.customer.CustomerProfileResponse;
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
import com.delvin.loan.dto.request.device.DeviceTokenRequest;
import com.delvin.loan.model.DeviceToken;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.DeviceTokenRepository;
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
    private final DeviceTokenRepository deviceTokenRepository;
    private final CustomerDocumentService customerDocumentService;
    private final CreditLimitService creditLimitService;

    // APPLICATION
    @Transactional
    public LoanApplicationResponse createApplication(LoanApplicationCreateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + request.getCustomerId()));

        customerDocumentService.requireComplete(customer.getCustomerId());

        validateAgainstPlafond(customer, request.getRequestedAmount(), request.getTenor());

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

        application.setDocuments(customerDocumentService.copyProfileDocumentsTo(application));

        return mapper.toApplicationResponse(application);
    }

    // PROFILE
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(String customerId) {
        return toProfileResponse(findCustomer(customerId));
    }

    @Transactional
    public CustomerProfileResponse updateProfile(String customerId, CustomerProfileUpdateRequest request) {

        Customer customer = findCustomer(customerId);

        customer.setPhoneNumber(request.getPhoneNumber().trim());
        customer.setAddress(request.getAddress().trim());
        customer.setOccupation(request.getOccupation().trim());

        return toProfileResponse(customerRepository.save(customer));
    }

    private CustomerProfileResponse toProfileResponse(Customer customer) {

        List<String> missing = customerDocumentService.missingRequiredTypes(customer.getCustomerId());

        return CustomerProfileResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .nik(customer.getNik())
                .address(customer.getAddress())
                .sex(customer.getSex())
                .birthPlace(customer.getBirthPlace())
                .birthDate(customer.getBirthDate())
                .occupation(customer.getOccupation())
                .citizenship(customer.getCitizenship())
                .approvedLimit(creditLimitService.grantedLimit(customer))
                .usedLimit(creditLimitService.usedLimit(customer.getCustomerId()))
                .availableLimit(creditLimitService.availableLimit(customer))
                .plafond(PlafondResponse.from(customer.getPlafond()))
                .documents(customerDocumentService.list(customer.getCustomerId()))
                .profileComplete(missing.isEmpty())
                .missingDocuments(missing)
                .build();
    }

    // PLAFOND
    @Transactional(readOnly = true)
    public CustomerPlafondResponse getMyPlafond(String customerId) {

        Customer customer = findCustomer(customerId);

        return CustomerPlafondResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .approvedLimit(creditLimitService.grantedLimit(customer))
                .usedLimit(creditLimitService.usedLimit(customerId))
                .availableLimit(creditLimitService.availableLimit(customer))
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

        BigDecimal granted = creditLimitService.grantedLimit(customer);

        if (body.getRequestedAmount().compareTo(granted) <= 0) {

            BigDecimal used = creditLimitService.usedLimit(customerId);

            throw BusinessException.badRequest(
                    "Limit yang diminta " + LoanNotificationText.rupiah(body.getRequestedAmount())
                            + " tidak lebih tinggi dari limit Anda saat ini ("
                            + LoanNotificationText.rupiah(granted)
                            + (used.signum() > 0
                                    ? ", terpakai " + LoanNotificationText.rupiah(used)
                                    : "")
                            + "). Ajukan total limit baru yang lebih besar.");
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

    // DEVICE TOKEN (push notifications)
    public void registerDeviceToken(String customerId, DeviceTokenRequest body) {

        Customer customer = findCustomer(customerId);

        DeviceToken device = deviceTokenRepository.findByToken(body.getToken())
                .orElseGet(DeviceToken::new);

        if (device.getDeviceTokenId() == null) {
            device.setToken(body.getToken());
            device.setCreatedAt(LocalDateTime.now());
        }

        device.setCustomer(customer);
        device.setPlatform(body.getPlatform() == null ? "android" : body.getPlatform());
        device.setLastSeenAt(LocalDateTime.now());

        deviceTokenRepository.save(device);
    }

    public void removeDeviceToken(String customerId, String token) {

        findCustomer(customerId);

        deviceTokenRepository.findByToken(token)
                .filter(device -> device.getCustomer() != null
                        && customerId.equals(device.getCustomer().getCustomerId()))
                .ifPresent(deviceTokenRepository::delete);
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

        BigDecimal granted = creditLimitService.grantedLimit(customer);
        BigDecimal used = creditLimitService.usedLimit(customer.getCustomerId());
        BigDecimal available = granted.subtract(used).max(BigDecimal.ZERO);

        if (requestedAmount.compareTo(available) > 0) {
            String reason = used.signum() > 0
                    ? " melebihi sisa limit Anda sebesar " + LoanNotificationText.rupiah(available)
                            + " (limit " + LoanNotificationText.rupiah(granted)
                            + ", terpakai " + LoanNotificationText.rupiah(used) + ")"
                    : " melebihi limit Anda sebesar " + LoanNotificationText.rupiah(available);

            throw BusinessException.unprocessable(
                    "Jumlah pengajuan " + LoanNotificationText.rupiah(requestedAmount) + reason
                            + ". Ajukan kenaikan limit plafond terlebih dahulu.");
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
