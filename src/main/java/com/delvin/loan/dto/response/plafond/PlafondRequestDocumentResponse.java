package com.delvin.loan.dto.response.plafond;

import com.delvin.loan.common.DocumentType;
import com.delvin.loan.model.PlafondRequestDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A document attached to a limit-increase request, as the branch manager's
 * review page sees it.
 *
 * No {@code fileUrl}: that column holds a filesystem path, and handing it to a
 * browser only ever produced a broken link. The bytes come from
 * {@code GET /api/bm/plafond-requests/{id}/documents/{documentId}/content}.
 */
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
