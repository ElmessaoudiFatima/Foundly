package com.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des exceptions — toutes les erreurs retournent un ApiError.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -----------------------------------------------------------------------
    // 422 VALIDATION_ERROR
    // -----------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        ApiError error = new ApiError(422, "VALIDATION_ERROR",
                "Données de la requête invalides.", request.getRequestURI());

        List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        error.setFieldErrors(fieldErrors);

        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest request) {
        ApiError error = new ApiError(422, "VALIDATION_ERROR",
                "Corps de la requête absent ou malformé.", request.getRequestURI());
        return ResponseEntity.status(422).body(error);
    }

    // -----------------------------------------------------------------------
    // 409 CONFLICT
    // -----------------------------------------------------------------------

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyUsed(EmailAlreadyUsedException ex,
                                                           HttpServletRequest request) {
        ApiError error = new ApiError(409, "EMAIL_ALREADY_USED",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        ApiError error = new ApiError(409, "CONFLICT",
                "Conflit de données : la ressource existe déjà.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // -----------------------------------------------------------------------
    // 401 UNAUTHORIZED
    // -----------------------------------------------------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest request) {
        ApiError error = new ApiError(401, "INVALID_CREDENTIALS",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                              HttpServletRequest request) {
        ApiError error = new ApiError(401, "INVALID_REFRESH_TOKEN",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        ApiError error = new ApiError(404, "NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "Ressource introuvable.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ApiError error = new ApiError(400, "BAD_REQUEST",
                ex.getMessage() != null ? ex.getMessage() : "Requête invalide.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // -----------------------------------------------------------------------
    // 500 INTERNAL_ERROR (catch-all, sans fuite de stacktrace)
    // -----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        // Log interne sans exposer le détail au client
        ex.printStackTrace(); // en production, utiliser un logger structuré
        ApiError error = new ApiError(500, "INTERNAL_ERROR",
                "Une erreur interne est survenue. Veuillez réessayer.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
