package net.ai.chatbot.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ValidationExceptionHandler {

    /**
     * Handle validation errors from Jakarta Validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatBotCreationResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("Validation failed: {}", errorMessage);
        
        return ResponseEntity.badRequest()
            .body(ChatBotCreationResponse.builder()
                .status("FAILED")
                .message("Validation failed: " + errorMessage)
                .build());
    }
    
    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ChatBotCreationResponse> handleConstraintViolationException(
            ConstraintViolationException ex) {
        
        String errorMessage = ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
        
        log.warn("Constraint violation: {}", errorMessage);
        
        return ResponseEntity.badRequest()
            .body(ChatBotCreationResponse.builder()
                .status("FAILED")
                .message("Validation failed: " + errorMessage)
                .build());
    }
}

