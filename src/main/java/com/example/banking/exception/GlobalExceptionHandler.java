package com.example.banking.exception;

import com.example.banking.config.RequestHeadersInterceptor;
import com.example.banking.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    // ---- Domain exceptions -------------------------------------------------

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return build(ErrorCode.ACCOUNT_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransferLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTransferLimitExceeded(TransferLimitExceededException ex) {
        return build(ErrorCode.TRANSFER_LIMIT_EXCEEDED, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return build(ErrorCode.INSUFFICIENT_FUNDS, ex.getMessage());
    }

    // ---- Required headers ---------------------------------------------------

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(ErrorCode.UNAUTHORIZED, ex.getMessage(), currentCorrelationId(), now()));
    }

    @ExceptionHandler(MissingCorrelationIdException.class)
    public ResponseEntity<ErrorResponse> handleMissingCorrelationId(MissingCorrelationIdException ex) {
        return build(ErrorCode.MISSING_CORRELATION_ID, ex.getMessage());
    }

    // ---- Request validation / parsing -------------------------------------

    /** Invalid @Valid @RequestBody (e.g. TransferRequest). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))   // stable order for clients/tests
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_FAILED, message);
    }

    /** Invalid constrained @PathVariable / @RequestParam (e.g. accountId = 0). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error))
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_FAILED, message);
    }

    /** Value that can't be converted to the parameter type (e.g. accountId = "abc"). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.INVALID_PARAMETER,
                "%s: invalid value '%s'".formatted(ex.getName(), ex.getValue()));
    }

    /** Missing or unparsable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_REQUEST, "Request body is missing or is not valid JSON");
    }

    // ---- Routing ------------------------------------------------------------

    /** Unknown URL. Without this, the catch-all below would turn it into a 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "No endpoint %s /%s".formatted(ex.getHttpMethod(), ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    // ---- Fallback -------------------------------------------------------------

    /** Anything unexpected: log the details, but never leak them to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Spring MVC client errors without a dedicated handler (e.g. 406) carry their own status.
        if (ex instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.of(status, springError.getBody().getDetail(), currentCorrelationId(), now()));
        }
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        // Explicit content type: errors stay JSON even if the client's Accept header asks for something else.
        return ResponseEntity.status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code, message, currentCorrelationId(), now()));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String currentCorrelationId() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? RequestHeadersInterceptor.correlationIdOf(attributes.getRequest())
                : null;
    }
}
