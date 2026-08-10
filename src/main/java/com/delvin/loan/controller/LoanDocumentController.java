package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.response.loanresp.LoanDocumentResponse;
import com.delvin.loan.service.LoanDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/loan-applications/{applicationId}/documents")
public class LoanDocumentController {

    private final LoanDocumentService documentService;

    public LoanDocumentController(LoanDocumentService documentService) {
        this.documentService = documentService;
    }

    /** Customer uploads a supporting document (KTP, KK, selfie photo, etc). */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<LoanDocumentResponse>> upload(
            @PathVariable String applicationId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) {
        return ResponseUtil.created("Document uploaded",
                documentService.uploadDocument(applicationId, documentType, file));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanDocumentResponse>>> list(@PathVariable String applicationId) {
        return ResponseUtil.success("Documents retrieved", documentService.listByApplication(applicationId));
    }
}
