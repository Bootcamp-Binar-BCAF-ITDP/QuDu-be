package com.delvin.loan.dto.response.customer;

import com.delvin.loan.common.DocumentType;
import com.delvin.loan.model.CustomerDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDocumentResponse {

    private Integer documentId;
    private String documentType;
    private String label;
    private String fileName;
    private String fileUrl;
    private LocalDateTime uploadedAt;

    public static CustomerDocumentResponse from(CustomerDocument document) {

        if (document == null) {
            return null;
        }

        return CustomerDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .documentType(document.getDocumentType())
                .label(DocumentType.label(document.getDocumentType()))
                .fileName(document.getFileName())
                .fileUrl(document.getFileUrl())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
