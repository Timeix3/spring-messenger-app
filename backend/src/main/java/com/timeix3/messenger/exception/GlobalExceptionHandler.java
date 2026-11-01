package com.timeix3.messenger.exception;

import com.timeix3.messenger.dto.ErrorResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(UsernameAlreadyExistsException.class)
  public ResponseEntity<Map<String, Object>> handleUsernameAlreadyExists(
      UsernameAlreadyExistsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Map<String, Object>> handleBadCredentials(
      BadCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Invalid username or password"));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(Instant.now(), exception.getMessage(), Map.of()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(Instant.now(), exception.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException exception) {

    Map<String, String> fieldErrors = new HashMap<>();

    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.badRequest()
        .body(Map.of("error", "Validation failed", "fields", fieldErrors));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException exception) {
    ErrorResponse response = new ErrorResponse(Instant.now(), exception.getMessage(), Map.of());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
  }
}
