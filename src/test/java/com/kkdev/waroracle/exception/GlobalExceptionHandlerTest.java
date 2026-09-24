package com.kkdev.waroracle.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.ResourceAccessException;

import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.common.ErrorCodes;

class GlobalExceptionHandlerTest
{

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp()
    {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleClashApiException()
    {
        ClashApiException ex = new ClashApiException(ErrorCodes.PLAYER_NOT_FOUND);
        ResponseEntity<ApiResponse> response = exceptionHandler.handleClashApiException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.PLAYER_NOT_FOUND.getErrorCode(), response.getBody().getErrorcode());
        assertNotNull(response.getBody().getTraceId());
    }

    @Test
    void testHandleClashApiExceptionWarAlreadyEnded()
    {
        ClashApiException ex = new ClashApiException(ErrorCodes.WAR_ALREADY_ENDED);
        ResponseEntity<ApiResponse> response = exceptionHandler.handleClashApiException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.WAR_ALREADY_ENDED.getErrorCode(), response.getBody().getErrorcode());
        assertNotNull(response.getBody().getTraceId());
    }

    @Test
    void testHandleValidationException()
    {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("testObject", "clanTag", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(fieldError));

        ResponseEntity<ApiResponse> response = exceptionHandler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(), response.getBody().getErrorcode());
        assertNotNull(response.getBody().getErrorField());
        assertEquals("must not be blank", response.getBody().getErrorField().get("clanTag"));
    }

    @Test
    void testHandleHttpMessageNotReadableException()
    {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON", (HttpInputMessage) null);
        ResponseEntity<ApiResponse> response = exceptionHandler.handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(), response.getBody().getErrorcode());
    }

    @Test
    void testHandleMethodNotSupportedException()
    {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");
        ResponseEntity<ApiResponse> response = exceptionHandler.handleHttpRequestMethodNotSupportedException(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.METHOD_NOT_ALLOWED.getErrorCode(), response.getBody().getErrorcode());
    }

    @Test
    void testHandleDataAccessException()
    {
        CannotAcquireLockException ex = new CannotAcquireLockException("Database lock acquisition failed");
        ResponseEntity<ApiResponse> response = exceptionHandler.handleDataAccessException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.DATABASE_ERROR.getErrorCode(), response.getBody().getErrorcode());
    }

    @Test
    void testHandleResourceAccessException()
    {
        ResourceAccessException ex = new ResourceAccessException("Connection timed out");
        ResponseEntity<ApiResponse> response = exceptionHandler.handleResourceAccessException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.SERVICE_UNAVAILABLE.getErrorCode(), response.getBody().getErrorcode());
    }

    @Test
    void testHandleGenericException()
    {
        NullPointerException ex = new NullPointerException("Simulated null pointer");
        ResponseEntity<ApiResponse> response = exceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertEquals(ErrorCodes.CLASH_API_ERROR.getErrorCode(), response.getBody().getErrorcode());
        assertNotNull(response.getBody().getTraceId());
    }
}
