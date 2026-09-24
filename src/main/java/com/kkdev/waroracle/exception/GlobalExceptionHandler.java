package com.kkdev.waroracle.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.common.ErrorCodes;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{

    @ExceptionHandler(ClashApiException.class)
    public ResponseEntity<ApiResponse> handleClashApiException(ClashApiException ex)
    {
        String traceId = resolveTraceId();
        ErrorCodes errorCode = ex.getErrorCode();
        log.error("[{}] ClashApiException occurred: errorCode={}, message={}", traceId, errorCode, ex.getMessage());

        HttpStatus httpStatus = mapErrorCodeToHttpStatus(errorCode);
        ApiResponse response = ApiResponse.failure(errorCode.getErrorCode(), errorCode.getErrorDescription(), traceId);
        return new ResponseEntity<>(response, httpStatus);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException ex)
    {
        String traceId = resolveTraceId();
        Map<String, String> errorFields = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors())
        {
            errorFields.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("[{}] Request validation failed: {}", traceId, errorFields);
        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorDescription(),
                errorFields,
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolationException(ConstraintViolationException ex)
    {
        String traceId = resolveTraceId();
        Map<String, String> errorFields = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations())
        {
            String propertyPath = violation.getPropertyPath().toString();
            errorFields.put(propertyPath, violation.getMessage());
        }

        log.warn("[{}] Constraint violation failed: {}", traceId, errorFields);
        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                "Invalid request constraint parameters",
                errorFields,
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse> handleHandlerMethodValidationException(HandlerMethodValidationException ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] Handler method validation failed: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] Malformed JSON request body or unreadable parameters: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                "Malformed JSON request body or unreadable parameters",
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex)
    {
        String traceId = resolveTraceId();
        String paramName = ex.getName();
        String message = String.format("Parameter '%s' expected type '%s'", paramName,
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("[{}] Argument type mismatch: {}", traceId, message);

        Map<String, String> errorFields = new HashMap<>();
        errorFields.put(paramName, message);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                message,
                errorFields,
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex)
    {
        String traceId = resolveTraceId();
        String paramName = ex.getParameterName();
        String message = String.format("Required query parameter '%s' is missing", paramName);
        log.warn("[{}] Missing request parameter: {}", traceId, message);

        Map<String, String> errorFields = new HashMap<>();
        errorFields.put(paramName, message);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                message,
                errorFields,
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] HTTP request method not supported: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.METHOD_NOT_ALLOWED.getErrorCode(),
                ex.getMessage(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] HTTP media type not supported: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.UNSUPPORTED_MEDIA_TYPE.getErrorCode(),
                ex.getMessage(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiResponse> handleNotFoundException(Exception ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] Resource endpoint not found: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.RESOURCE_NOT_FOUND.getErrorCode(),
                "The requested endpoint resource does not exist",
                traceId);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse> handleResourceAccessException(ResourceAccessException ex)
    {
        String traceId = resolveTraceId();
        log.error("[{}] Network/timeout communication failure with external services: {}", traceId, ex.getMessage(), ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorCode(),
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse> handleRestClientException(RestClientException ex)
    {
        String traceId = resolveTraceId();
        log.error("[{}] REST client communication failure with external services: {}", traceId, ex.getMessage(), ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorCode(),
                ErrorCodes.SERVICE_UNAVAILABLE.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse> handleDataAccessException(DataAccessException ex)
    {
        String traceId = resolveTraceId();
        log.error("[{}] Database access error occurred: {}", traceId, ex.getMessage(), ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.DATABASE_ERROR.getErrorCode(),
                ErrorCodes.DATABASE_ERROR.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<ApiResponse> handleBusinessLogicException(RuntimeException ex)
    {
        String traceId = resolveTraceId();
        log.warn("[{}] Business constraint or state violation: {}", traceId, ex.getMessage());

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_BAD_REQUEST.getErrorCode(),
                ex.getMessage() != null ? ex.getMessage() : ErrorCodes.CLASH_API_BAD_REQUEST.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex)
    {
        String traceId = resolveTraceId();
        log.error("[{}] Uncaught internal server error: ", traceId, ex);

        ApiResponse response = ApiResponse.failure(
                ErrorCodes.CLASH_API_ERROR.getErrorCode(),
                ErrorCodes.CLASH_API_ERROR.getErrorDescription(),
                traceId);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String resolveTraceId()
    {
        String traceId = LogTagInterceptor.getCurrentTraceId();
        if (traceId == null || traceId.trim().isEmpty())
        {
            return "ERR_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return traceId;
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
            case RESOURCE_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CLAN_WAR_LOG_PRIVATE:
                return HttpStatus.FORBIDDEN;
            case CLASH_API_BAD_REQUEST:
            case WAR_ALREADY_ENDED:
                return HttpStatus.BAD_REQUEST;
            case CLASH_API_UNAUTHORIZED:
                return HttpStatus.UNAUTHORIZED;
            case CLASH_API_RATE_LIMITED:
                return HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case METHOD_NOT_ALLOWED:
                return HttpStatus.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE:
                return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case DATABASE_ERROR:
            case CLASH_API_ERROR:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
