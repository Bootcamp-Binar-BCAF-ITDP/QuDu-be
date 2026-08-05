package com.delvin.loan.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private LocalDateTime timestamp;

    private Integer status;

    private Boolean success;

    private String message;

    private T data;
}