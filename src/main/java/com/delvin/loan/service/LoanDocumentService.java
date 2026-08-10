package com.delvin.loan.service;

import com.delvin.loan.dto.response.loanresp.LoanDocumentResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanDocument;
import com.delvin.loan.repository.LoanDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LoanDocumentService {

    private final LoanDocumentRepository documentRepository;
    private final LoanApplicationService applicationService;
    private final LoanMapper mapper;

    @Value("${loan.documents.storage-path:uploads/loan-documents}")
    private String storagePath;

    public LoanDocumentService(LoanDocumentRepository documentRepository,
                                LoanApplicationService applicationService,
                                LoanMapper mapper) {
        this.documentRepository = documentRepository;
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    /**
     * Step 2: customer uploads a supporting document (KTP, KK, selfie, etc)
     * for an application they've already created.
     */
    @Transactional
    public LoanDocumentResponse uploadDocument(String applicationId, String documentType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("File is required");
        }

        LoanApplication application = applicationService.getApplicationOrThrow(applicationId);
        String storedFileName = storeFile(applicationId, file);

        LoanDocument document = new LoanDocument();
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setFileName(file.getOriginalFilename());
        document.setFileUrl(storedFileName);
        document.setUploadedAt(LocalDate.now());

        documentRepository.save(document);
        return mapper.toDocumentResponse(document);
    }

    public List<LoanDocumentResponse> listByApplication(String applicationId) {
        return documentRepository.findByApplication_ApplicationId(applicationId).stream()
                .map(mapper::toDocumentResponse)
                .collect(Collectors.toList());
    }

    private String storeFile(String applicationId, MultipartFile file) {
        try {
            Path dir = Paths.get(storagePath, applicationId);
            Files.createDirectories(dir);

            String extension = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                extension = original.substring(original.lastIndexOf('.'));
            }
            String storedName = UUID.randomUUID() + extension;
            Path target = dir.resolve(storedName);
            file.transferTo(target);

            return target.toString();
        } catch (IOException e) {
            throw new BusinessException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store document: " + e.getMessage());
        }
    }
}
