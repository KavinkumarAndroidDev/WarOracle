package com.kkdev.waroracle.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.common.ErrorCodes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{

    @ExceptionHandler(ClashApiException.class)
    public ResponseEntity<ApiResponse> handleClashApiException(ClashApiException ex)
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        ErrorCodes errorCode = ex.getErrorCode();
        log.error("ClashApiException occurred: errorCode={}, message={}", errorCode, ex.getMessage());

        HttpStatus httpStatus = mapErrorCodeToHttpStatus(errorCode);
        ApiResponse response = ApiResponse.failure(errorCode.getErrorCode(), errorCode.getErrorDescription(), traceId);
        return new ResponseEntity<>(response, httpStatus);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse> handleResourceAccessException(ResourceAccessException ex)
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        log.error("Resource access/network error communicating with external services: {}", ex.getMessage(), ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorCode(),
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse> handleRestClientException(RestClientException ex)
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        log.error("REST client error communicating with external services: {}", ex.getMessage(), ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorCode(),
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException ex)
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        Map<String, String> errorField = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors())
        {
            errorField.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed: {}", errorField);
        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorDescription(),
                errorField,
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex)
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        log.error("Unhandled exception occurred: ", ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_ERROR.getErrorCode(),
                ErrorCodes.CLASH_API_ERROR.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private HttpStatus mapErrorCodeToHttpStatus(ErrorCodes errorCode)
    {
        if (errorCode == null)
        {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        switch (errorCode)
        {
            case PLAYER_NOT_FOUND:
            case CLAN_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CLAN_WAR_LOG_PRIVATE:
                return HttpStatus.FORBIDDEN;
            case CLASH_API_BAD_REQUEST:
                return HttpStatus.BAD_REQUEST;
            case CLASH_API_UNAUTHORIZED:
                return HttpStatus.UNAUTHORIZED;
            case CLASH_API_RATE_LIMITED:
                return HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case CLASH_API_ERROR:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}

