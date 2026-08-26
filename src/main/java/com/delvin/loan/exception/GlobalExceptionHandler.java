package com.delvin.loan.exception;

import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseUtil.error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSort(PropertyReferenceException e) {
        return ResponseUtil.error(HttpStatus.BAD_REQUEST,
                "Invalid sort property: " + e.getPropertyName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
}