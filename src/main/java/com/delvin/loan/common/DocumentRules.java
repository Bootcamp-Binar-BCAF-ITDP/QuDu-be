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
            throw BusinessException.badRequest("File wajib dilampirkan");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw BusinessException.unprocessable(
                    "Ukuran berkas " + (file.getSize() / 1024) + " KB melebihi batas "
                            + maxFileSizeLabel() + ". Kompres atau potret ulang dengan resolusi lebih kecil.");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.badRequest(
                    "Format berkas " + (contentType == null ? "tidak dikenali" : contentType)
                            + " tidak didukung. Gunakan foto (JPG, PNG, WEBP) atau PDF.");
        }
    }
}
