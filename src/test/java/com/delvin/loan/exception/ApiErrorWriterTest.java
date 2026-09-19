package com.delvin.loan.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorWriterTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final ApiErrorWriter writer = new ApiErrorWriter(mapper);

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("writes the ApiResponse shape: timestamp, status, message, no data")
    void writesApiResponseShape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = body(response);
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("message").asString()).isEqualTo("Invalid or expired token");
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("data")).isFalse();
        assertThat(json.has("success")).isFalse();
    }

    @Test
    @DisplayName("a message with quotes is escaped, not spliced into the JSON")
    void escapesMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.BAD_REQUEST, "say \"hi\"");

        assertThat(body(response).get("message").asString()).isEqualTo("say \"hi\"");
    }

    @Test
    @DisplayName("a committed response is left alone")
    void skipsCommittedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setCommitted(true);

        writer.write(response, HttpStatus.UNAUTHORIZED, "x");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("401 from the entry point carries a message")
    void entryPointHasMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(writer).commence(
                new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response).get("message").asString())
                .isEqualTo(RestAuthenticationEntryPoint.MESSAGE);
    }

    @Test
    @DisplayName("403 from the access-denied handler carries a message")
    void accessDeniedHasMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(writer).handle(
                new MockHttpServletRequest(), response,
                new AccessDeniedException("wrong role"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response).get("message").asString())
                .isEqualTo(RestAccessDeniedHandler.MESSAGE);
    }
}
