package com.delvin.loan.service;

import com.delvin.loan.common.DocumentRules;
import com.delvin.loan.common.DocumentType;
import com.delvin.loan.dto.response.loanresp.LoanDocumentResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDocument;
import com.delvin.loan.repository.LoanDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanDocumentService {

    private final LoanDocumentRepository documentRepository;
    private final LoanApplicationService applicationService;
    private final DocumentStorageService storage;
    private final LoanMapper mapper;

    @Transactional
    public LoanDocumentResponse uploadDocument(String applicationId, String documentType, MultipartFile file) {
        DocumentRules.validate(file);

        LoanApplication application = applicationService.getApplicationOrThrow(applicationId);
        String type = DocumentType.normalize(documentType);

        LoanDocument document = documentRepository
                .findByApplication_ApplicationIdAndDocumentType(applicationId, type)
                .orElseGet(LoanDocument::new);

        document.setApplication(application);
        document.setDocumentType(type);
        document.setFileName(file.getOriginalFilename());
        document.setFileUrl(storage.storeLoanDocument(applicationId, file));
        document.setUploadedAt(LocalDate.now());

        documentRepository.save(document);
        return mapper.toDocumentResponse(document);
    }

    @Transactional
    public LoanDocumentResponse uploadOwnDocument(String customerId, String applicationId,
                                                 String documentType, MultipartFile file) {

        LoanApplication application = requireOwnedBy(customerId, applicationId);

        if (LoanStatus.isTerminal(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + applicationId + " is already closed (" + application.getStatus()
                            + ") and no longer accepts documents");
        }

        if (!DocumentType.isApplicationType(documentType)) {
            throw BusinessException.badRequest(
                    DocumentType.label(documentType) + " adalah dokumen profil. "
                            + "Unggah melalui POST /api/customer/documents - dokumen profil dipakai ulang "
                            + "oleh setiap pengajuan.");
        }

        return uploadDocument(applicationId, documentType, file);
    }

    public List<LoanDocumentResponse> listOwnDocuments(String customerId, String applicationId) {
        requireOwnedBy(customerId, applicationId);
        return listByApplication(applicationId);
    }

    private LoanApplication requireOwnedBy(String customerId, String applicationId) {

        LoanApplication application = applicationService.getApplicationOrThrow(applicationId);

        Customer owner = application.getCustomer();

        if (owner == null || !owner.getCustomerId().equals(customerId)) {
            throw BusinessException.forbidden("This application belongs to another customer");
        }

        return application;
    }

    @Transactional(readOnly = true)
    public StoredDocument loadContent(String applicationId, Integer documentId) {

        LoanDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document not found: " + documentId));

        if (document.getApplication() == null
                || !document.getApplication().getApplicationId().equals(applicationId)) {
            throw BusinessException.notFound("Document not found: " + documentId);
        }

        Path path = storage.resolveStored(document.getFileUrl());

        return new StoredDocument(
                path,
                storage.contentTypeOf(path),
                document.getFileName() == null ? path.getFileName().toString() : document.getFileName());
    }

    public record StoredDocument(Path path, MediaType contentType, String fileName) {}

    public List<LoanDocumentResponse> listByApplication(String applicationId) {
        return documentRepository.findByApplication_ApplicationId(applicationId).stream()
                .map(mapper::toDocumentResponse)
                .collect(Collectors.toList());
    }
}
