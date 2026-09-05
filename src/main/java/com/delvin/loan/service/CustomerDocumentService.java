package com.delvin.loan.service;

import com.delvin.loan.common.DocumentRules;
import com.delvin.loan.common.DocumentType;
import com.delvin.loan.dto.response.customer.CustomerDocumentResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.CustomerDocument;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDocument;
import com.delvin.loan.repository.CustomerDocumentRepository;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.LoanDocumentRepository;
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
    private final DocumentStorageService storage;

    @Transactional
    public CustomerDocumentResponse upload(String customerId, String documentType, MultipartFile file) {

        DocumentRules.validate(file);

        String type = DocumentType.normalize(documentType);

        if (!DocumentType.isProfileType(type)) {
            throw BusinessException.badRequest(
                    "Jenis dokumen " + documentType + " bukan dokumen profil. "
                            + "Dokumen profil: " + String.join(", ", DocumentType.PROFILE_TYPES)
                            + ". Slip gaji dan buku rekening diunggah pada pengajuan.");
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

        List<String> uploaded = findAll(customerId).stream()
                .map(CustomerDocument::getDocumentType)
                .toList();

        return DocumentType.PROFILE_TYPES.stream()
                .filter(required -> !uploaded.contains(required))
                .toList();
    }

    public void requireComplete(String customerId) {

        List<String> missing = missingRequiredTypes(customerId);

        if (!missing.isEmpty()) {
            throw BusinessException.conflict(
                    "Lengkapi profil Anda terlebih dahulu. Dokumen yang belum diunggah: "
                            + missing.stream().map(DocumentType::label).collect(Collectors.joining(", "))
                            + ".");
        }
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
