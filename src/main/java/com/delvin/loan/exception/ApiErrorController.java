package com.delvin.loan.exception;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.ResponseUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@Slf4j
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {

        HttpStatus status = statusOf(request);

        if (status.is5xxServerError()) {
            Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("Error outside the controllers on {}: {}",
                    request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), cause);
            return ResponseUtil.error(status, "Internal server error");
        }

        return ResponseUtil.error(status, messageFor(status, request));
    }

    private HttpStatus statusOf(HttpServletRequest request) {

        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (code instanceof Integer value) {
            HttpStatus resolved = HttpStatus.resolve(value);
            if (resolved != null && resolved.isError()) {
                return resolved;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String messageFor(HttpStatus status, HttpServletRequest request) {

        return switch (status) {
            case UNAUTHORIZED -> RestAuthenticationEntryPoint.MESSAGE;
            case FORBIDDEN -> RestAccessDeniedHandler.MESSAGE;
            case NOT_FOUND -> "Endpoint not found";
            default -> {
                Object detail = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
                yield detail instanceof String text && !text.isBlank()
                        ? text
                        : status.getReasonPhrase();
            }
        };
    }
}
