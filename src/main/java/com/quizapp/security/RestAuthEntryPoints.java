package com.quizapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security rejects requests inside the filter chain, before any controller advice
 * runs, so 401s and 403s need to be written here to match the ErrorResponse shape used
 * everywhere else.
 */
public final class RestAuthEntryPoints {

    private RestAuthEntryPoints() {
    }

    private static void write(HttpServletResponse response, HttpServletRequest request,
                              ObjectMapper objectMapper, HttpStatus status, String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Returned when no valid token was supplied at all. */
    @Component
    public static class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             org.springframework.security.core.AuthenticationException authException)
                throws IOException {
            write(response, request, objectMapper, HttpStatus.UNAUTHORIZED,
                    "Authentication is required. Send a valid 'Authorization: Bearer <token>' header.");
        }
    }

    /** Returned when the token is valid but the role is insufficient. */
    @Component
    public static class JsonAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException accessDeniedException)
                throws IOException {
            write(response, request, objectMapper, HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action.");
        }
    }
}
