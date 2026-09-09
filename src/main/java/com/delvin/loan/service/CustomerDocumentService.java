package com.delvin.loan.service;

import com.delvin.loan.common.DocumentRules;
import com.delvin.loan.common.DocumentType;
import com.delvin.loan.dto.response.customer.CustomerDocumentResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.CustomerDocument;
import com.delvin.loan.model.CustomerPlafondRequest;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDocument;
import com.delvin.loan.model.PlafondRequestDocument;
import com.delvin.loan.repository.CustomerDocumentRepository;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanDocumentRepository;
import com.delvin.loan.repository.PlafondRequestDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerDocumentService {

    private final CustomerDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final PlafondRequestDocumentRepository plafondRequestDocumentRepository;
    private final DocumentStorageService storage;

    @Transactional
    public CustomerDocumentResponse upload(String customerId, String documentType, MultipartFile file) {

        DocumentRules.validate(file);

        String type = DocumentType.normalize(documentType);

        if (!DocumentType.isProfileType(type)) {
            throw BusinessException.badRequest(
                    "Document type " + documentType + " is not recognised. "
                            + "Yang diterima: "
                            + String.join(", ", DocumentType.REQUIRED_FOR_SUBMISSION) + ".");
        }

        Customer customer = findCustomer(customerId);

        CustomerDocument document = documentRepository
                .findByCustomer_CustomerIdAndDocumentType(customerId, type)
                .orElseGet(CustomerDocument::new);

        document.setCustomer(customer);
        document.setDocumentType(type);
        document.setFileName(file.getOriginalFilename());
        document.setFileUrl(storage.storeCustomerDocument(customerId, file));
        document.setUploadedAt(LocalDateTime.now());

        return CustomerDocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<CustomerDocumentResponse> list(String customerId) {
        return findAll(customerId).stream()
                .map(CustomerDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> missingRequiredTypes(String customerId) {
        return missingOf(customerId, DocumentType.PROFILE_TYPES);
    }

    @Transactional(readOnly = true)
    public List<String> missingForSubmission(String customerId) {
        return missingOf(customerId, DocumentType.REQUIRED_FOR_SUBMISSION);
    }

    public void requireComplete(String customerId) {

        List<String> missing = missingRequiredTypes(customerId);

        if (!missing.isEmpty()) {
            throw BusinessException.conflict(
                    "Complete your profile first. Documents not yet uploaded: "
                            + missing.stream().map(DocumentType::label).collect(Collectors.joining(", "))
                            + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<String> staleForSubmission(String customerId) {

        LocalDateTime cutoff = LocalDateTime.now().minusDays(DocumentType.SUBMISSION_FRESHNESS_DAYS);

        return findAll(customerId).stream()
                .filter(document -> DocumentType.expires(document.getDocumentType()))
                .filter(document -> document.getUploadedAt() == null
                        || document.getUploadedAt().isBefore(cutoff))
                .map(CustomerDocument::getDocumentType)
                .toList();
    }

    public void requireCompleteForSubmission(String customerId) {

        List<String> missing = missingForSubmission(customerId);
        List<String> stale = staleForSubmission(customerId);

        if (missing.isEmpty() && stale.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Please complete your documents first.");

        if (!missing.isEmpty()) {
            message.append(" Not uploaded yet: ")
                    .append(missing.stream().map(DocumentType::label).collect(Collectors.joining(", ")))
                    .append(".");
        }

        if (!stale.isEmpty()) {
            message.append(" Older than ")
                    .append(DocumentType.SUBMISSION_FRESHNESS_DAYS)
                    .append(" days, please upload again: ")
                    .append(stale.stream().map(DocumentType::label).collect(Collectors.joining(", ")))
                    .append(".");
        }

        throw BusinessException.conflict(message.toString());
    }

    private List<String> missingOf(String customerId, List<String> required) {

        List<String> uploaded = findAll(customerId).stream()
                .map(CustomerDocument::getDocumentType)
                .toList();

        return required.stream()
                .filter(type -> !uploaded.contains(type))
                .toList();
    }

    public List<PlafondRequestDocument> copyDocumentsTo(CustomerPlafondRequest request) {

        String customerId = request.getCustomer().getCustomerId();

        List<PlafondRequestDocument> copies = findAll(customerId).stream()
                .map(source -> {
                    PlafondRequestDocument copy = new PlafondRequestDocument();
                    copy.setRequest(request);
                    copy.setDocumentType(source.getDocumentType());
                    copy.setFileName(source.getFileName());
                    copy.setFileUrl(source.getFileUrl());
                    copy.setUploadedAt(LocalDateTime.now());
                    return copy;
                })
                .toList();

        return plafondRequestDocumentRepository.saveAll(copies);
    }

    public List<LoanDocument> copyProfileDocumentsTo(LoanApplication application) {

        String customerId = application.getCustomer().getCustomerId();

        Map<String, LoanDocument> existing = loanDocumentRepository
                .findByApplication_ApplicationId(application.getApplicationId())
                .stream()
                .collect(Collectors.toMap(LoanDocument::getDocumentType, Function.identity(), (a, b) -> a));

        List<LoanDocument> copies = findAll(customerId).stream()
                .filter(source -> !existing.containsKey(source.getDocumentType()))
                .map(source -> {
                    LoanDocument copy = new LoanDocument();
                    copy.setApplication(application);
                    copy.setDocumentType(source.getDocumentType());
                    copy.setFileName(source.getFileName());
                    copy.setFileUrl(source.getFileUrl());
                    copy.setUploadedAt(LocalDate.now());
                    return copy;
                })
                .toList();

        return loanDocumentRepository.saveAll(copies);
    }

    private List<CustomerDocument> findAll(String customerId) {
        return documentRepository.findByCustomer_CustomerIdOrderByDocumentTypeAsc(customerId);
    }

    private Customer findCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("Customer not found: " + customerId));
    }
}
