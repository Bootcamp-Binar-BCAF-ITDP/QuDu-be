package com.delvin.loan.dto.response.plafond;

import com.delvin.loan.common.DocumentType;
import com.delvin.loan.model.PlafondRequestDocument;
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
public class PlafondRequestDocumentResponse {

    private Integer documentId;
    private String documentType;
    private String label;
    private String fileName;
    private LocalDateTime uploadedAt;

    public static PlafondRequestDocumentResponse from(PlafondRequestDocument document) {

        if (document == null) {
            return null;
        }

        return PlafondRequestDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .documentType(document.getDocumentType())
                .label(DocumentType.label(document.getDocumentType()))
                .fileName(document.getFileName())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
