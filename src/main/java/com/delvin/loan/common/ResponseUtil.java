package com.delvin.loan.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class ResponseUtil {

    private ResponseUtil() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(
            String message,
            T data
    ) {

        ApiResponse<T> response = new ApiResponse<>(
                LocalDateTime.now(),
                HttpStatus.OK.value(),
                true,
                message,
                data
        );

        return ResponseEntity.ok(response);
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(
            String message,
            T data
    ) {

        ApiResponse<T> response = new ApiResponse<>(
                LocalDateTime.now(),
                HttpStatus.CREATED.value(),
                true,
                message,
                data
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(
            HttpStatus status,
            String message
    ) {

        ApiResponse<T> response = new ApiResponse<>(
                LocalDateTime.now(),
                status.value(),
                false,
                message,
                null
        );

        return ResponseEntity.status(status).body(response);
    }
}