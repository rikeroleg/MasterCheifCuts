package com.masterchefcuts.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntime_returns400WithErrorMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleRuntime(new RuntimeException("something went wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("error", "something went wrong")
                .doesNotContainKey("timestamp")
                .doesNotContainKey("status");
    }

    @Test
    void handleValidation_returns422WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "email", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody())
                .containsEntry("error", "Validation failed")
                .containsKey("fields")
                .doesNotContainKey("timestamp")
                .doesNotContainKey("status");

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("email", "must not be blank");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .containsEntry("error", "Access denied")
                .doesNotContainKey("timestamp")
                .doesNotContainKey("status");
    }
}
