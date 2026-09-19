package com.delvin.loan.exception;

import com.delvin.loan.common.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorControllerTest {

    private final ApiErrorController controller = new ApiErrorController();

    private MockHttpServletRequest errorRequest(Integer status, String message) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/x");
        return request;
    }

    @Test
    @DisplayName("404 says the endpoint was not found")
    void notFound() {
        ResponseEntity<ApiResponse<Void>> result = controller.error(errorRequest(404, null));

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody().getStatus()).isEqualTo(404);
        assertThat(result.getBody().getMessage()).isEqualTo("Endpoint not found");
    }

    @Test
    @DisplayName("a 4xx keeps the container's message")
    void clientErrorKeepsMessage() {
        ResponseEntity<ApiResponse<Void>> result =
                controller.error(errorRequest(400, "The request was rejected"));

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody().getMessage()).isEqualTo("The request was rejected");
    }

    @Test
    @DisplayName("a 4xx without a message falls back to the reason phrase")
    void clientErrorWithoutMessage() {
        ResponseEntity<ApiResponse<Void>> result = controller.error(errorRequest(405, ""));

        assertThat(result.getBody().getMessage()).isEqualTo("Method Not Allowed");
    }

    @Test
    @DisplayName("a 5xx never leaks its detail")
    void serverErrorHidesDetail() {
        ResponseEntity<ApiResponse<Void>> result =
                controller.error(errorRequest(500, "NullPointerException at Foo.java:12"));

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody().getMessage()).isEqualTo("Internal server error");
    }

    @Test
    @DisplayName("no status attribute is treated as 500")
    void missingStatus() {
        ResponseEntity<ApiResponse<Void>> result = controller.error(errorRequest(null, null));

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody().getMessage()).isEqualTo("Internal server error");
    }

    @Test
    @DisplayName("401 and 403 reuse the security messages")
    void securityStatuses() {
        assertThat(controller.error(errorRequest(401, null)).getBody().getMessage())
                .isEqualTo(RestAuthenticationEntryPoint.MESSAGE);
        assertThat(controller.error(errorRequest(403, null)).getBody().getMessage())
                .isEqualTo(RestAccessDeniedHandler.MESSAGE);
    }

    @Test
    @DisplayName("an unmapped URL is 404 from the advice, not the 500 catch-all")
    void unmappedUrlIs404() {
        ResponseEntity<ApiResponse<Void>> result = new GlobalExceptionHandler().handleNoEndpoint(
                new NoResourceFoundException(HttpMethod.GET, "/api/nope", "api/nope"));

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody().getMessage()).isEqualTo("Endpoint not found");
    }
}
