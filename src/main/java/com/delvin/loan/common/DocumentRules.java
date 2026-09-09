package com.delvin.loan.common;

import com.delvin.loan.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public final class DocumentRules {

    private DocumentRules() {}

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    public static final long MAX_REQUEST_BYTES = 2 * MAX_FILE_BYTES;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
            "application/pdf"
    );

    public static String maxFileSizeLabel() {
        return (MAX_FILE_BYTES / (1024 * 1024)) + " MB";
    }

    public static void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("A file is required");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw BusinessException.unprocessable(
                    "The file is " + (file.getSize() / 1024) + " KB, over the "
                            + maxFileSizeLabel() + " limit. Compress it or retake the photo at a lower resolution.");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.badRequest(
                    "File format " + (contentType == null ? "unknown" : contentType)
                            + " is not supported. Use a photo (JPG, PNG, WEBP) or a PDF.");
        }
    }
}
