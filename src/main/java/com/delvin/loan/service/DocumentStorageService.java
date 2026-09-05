package com.delvin.loan.service;

import com.delvin.loan.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class DocumentStorageService {

    private final String loanStoragePath;
    private final String customerStoragePath;

    public DocumentStorageService(
            @Value("${loan.documents.storage-path:uploads/loan-documents}") String loanStoragePath,
            @Value("${customer.documents.storage-path:uploads/customer-documents}") String customerStoragePath) {
        this.loanStoragePath = loanStoragePath;
        this.customerStoragePath = customerStoragePath;
    }

    public String storeLoanDocument(String applicationId, MultipartFile file) {
        return store(loanStoragePath, applicationId, file);
    }

    public String storeCustomerDocument(String customerId, MultipartFile file) {
        return store(customerStoragePath, customerId, file);
    }

    public Path resolveStored(String storedPath) {

        if (storedPath == null || storedPath.isBlank()) {
            throw BusinessException.notFound("Document has no file on record");
        }

        Path candidate = Paths.get(storedPath).toAbsolutePath().normalize();

        boolean insideAllowedRoot = Stream.of(loanStoragePath, customerStoragePath)
                .map(root -> Paths.get(root).toAbsolutePath().normalize())
                .anyMatch(candidate::startsWith);

        if (!insideAllowedRoot) {
            log.warn("Refused to read {} - outside the configured document roots", candidate);
            throw BusinessException.notFound("Document file not found");
        }

        if (!Files.isReadable(candidate)) {
            throw BusinessException.notFound("Document file is no longer on disk");
        }

        return candidate;
    }

    public MediaType contentTypeOf(Path path) {

        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (name.endsWith(".heic")) return MediaType.parseMediaType("image/heic");
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String store(String root, String key, MultipartFile file) {
        try {
            Path dir = Paths.get(root, key);
            Files.createDirectories(dir);

            String extension = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                extension = original.substring(original.lastIndexOf('.'));
            }

            Path target = dir.resolve(UUID.randomUUID() + extension);
            file.transferTo(target);

            return target.toString();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store document: " + e.getMessage());
        }
    }
}
