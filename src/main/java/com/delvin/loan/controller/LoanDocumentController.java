package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.response.loanresp.LoanDocumentResponse;
import com.delvin.loan.service.LoanDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/loan-applications/{applicationId}/documents")
@RequiredArgsConstructor
public class LoanDocumentController {

    private final LoanDocumentService documentService;

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

    @GetMapping("/{documentId}/content")
    public ResponseEntity<Resource> content(@PathVariable String applicationId,
                                            @PathVariable Integer documentId) {

        LoanDocumentService.StoredDocument stored = documentService.loadContent(applicationId, documentId);

        return ResponseEntity.ok()
                .contentType(stored.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(stored.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(stored.path()));
    }
}
