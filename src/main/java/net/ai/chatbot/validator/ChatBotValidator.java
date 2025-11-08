package net.ai.chatbot.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.ChatBot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Custom Jakarta validation annotations and validators
 */
@Slf4j
public class ChatBotValidator {
    
    /**
     * Custom validation annotation for chatbot names
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ChatBotNameValidator.class)
    public @interface ValidChatBotName {
        String message() default "Invalid chatbot name";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validator for chatbot names
     */
    public static class ChatBotNameValidator implements ConstraintValidator<ValidChatBotName, String> {
        private static final int MAX_NAME_LENGTH = 100;
        
        @Override
        public boolean isValid(String name, ConstraintValidatorContext context) {
            if (name == null || name.trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Chatbot name is required")
                    .addConstraintViolation();
                return false;
            }
            
            if (name.length() > MAX_NAME_LENGTH) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Chatbot name must not exceed " + MAX_NAME_LENGTH + " characters")
                    .addConstraintViolation();
                return false;
            }
            
            if (!name.matches("^[a-zA-Z0-9_\\s-]+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Chatbot name contains invalid characters")
                    .addConstraintViolation();
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * Custom validation for Q&A pairs
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = QAPairValidator.class)
    public @interface ValidQAPairs {
        String message() default "Invalid Q&A pairs";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validator for Q&A pairs
     */
    public static class QAPairValidator implements ConstraintValidator<ValidQAPairs, List<ChatBot.QAPair>> {
        private static final int MAX_PAIRS = 100;
        private static final int MAX_QUESTION_LENGTH = 500;
        private static final int MAX_ANSWER_LENGTH = 2000;
        
        @Override
        public boolean isValid(List<ChatBot.QAPair> qaPairs,
                             ConstraintValidatorContext context) {
            if (qaPairs == null) {
                return true; // Optional field
            }
            
            if (qaPairs.size() > MAX_PAIRS) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Maximum of " + MAX_PAIRS + " Q&A pairs allowed")
                    .addConstraintViolation();
                return false;
            }
            
            for (int i = 0; i < qaPairs.size(); i++) {
                ChatBot.QAPair pair = qaPairs.get(i);
                if (pair == null) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Q&A pair at index " + i + " cannot be null")
                        .addConstraintViolation();
                    return false;
                }
                
                if (pair.getQuestion() == null || pair.getQuestion().trim().isEmpty()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Question at index " + i + " is required")
                        .addConstraintViolation();
                    return false;
                }
                
                if (pair.getQuestion().length() > MAX_QUESTION_LENGTH) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Question at index " + i + " exceeds " + MAX_QUESTION_LENGTH + " characters")
                        .addConstraintViolation();
                    return false;
                }
                
                if (pair.getAnswer() == null || pair.getAnswer().trim().isEmpty()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Answer at index " + i + " is required")
                        .addConstraintViolation();
                    return false;
                }
                
                if (pair.getAnswer().length() > MAX_ANSWER_LENGTH) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Answer at index " + i + " exceeds " + MAX_ANSWER_LENGTH + " characters")
                        .addConstraintViolation();
                    return false;
                }
            }
            
            return true;
        }
    }
    
    /**
     * Custom validation for URLs
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = UrlListValidator.class)
    public @interface ValidUrlList {
        String message() default "Invalid URL list";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validator for URL lists
     */
    public static class UrlListValidator implements ConstraintValidator<ValidUrlList, List<String>> {
        private static final int MAX_URLS = 20;
        
        @Override
        public boolean isValid(List<String> urls, ConstraintValidatorContext context) {
            if (urls == null) {
                return true; // Optional field
            }
            
            if (urls.size() > MAX_URLS) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Maximum of " + MAX_URLS + " website URLs allowed")
                    .addConstraintViolation();
                return false;
            }
            
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                if (url == null || url.trim().isEmpty()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Website URL at index " + i + " cannot be empty")
                        .addConstraintViolation();
                    return false;
                }
                
                try {
                    new java.net.URL(url);
                } catch (java.net.MalformedURLException e) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Invalid URL at index " + i + ": " + url)
                        .addConstraintViolation();
                    return false;
                }
            }
            
            return true;
        }
    }
    
    /**
     * Custom validation for text content
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = TextListValidator.class)
    public @interface ValidTextList {
        String message() default "Invalid text content";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validator for text content lists
     */
    public static class TextListValidator implements ConstraintValidator<ValidTextList, List<String>> {
        private static final int MAX_TEXTS = 50;
        private static final int MAX_TEXT_LENGTH = 10000;
        
        @Override
        public boolean isValid(List<String> texts, ConstraintValidatorContext context) {
            if (texts == null) {
                return true; // Optional field
            }
            
            if (texts.size() > MAX_TEXTS) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Maximum of " + MAX_TEXTS + " text items allowed")
                    .addConstraintViolation();
                return false;
            }
            
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                if (text == null || text.trim().isEmpty()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Text content at index " + i + " cannot be empty")
                        .addConstraintViolation();
                    return false;
                }
                
                if (text.length() > MAX_TEXT_LENGTH) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("Text content at index " + i + " must not exceed " + MAX_TEXT_LENGTH + " characters")
                        .addConstraintViolation();
                    return false;
                }
            }
            
            return true;
        }
    }
    
    /**
     * Custom validation for file lists
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = FileListValidator.class)
    public @interface ValidFileList {
        String message() default "Invalid file list";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    
    /**
     * Validator for file lists
     */
    public static class FileListValidator implements ConstraintValidator<ValidFileList, List<String>> {
        private static final int MAX_FILES = 20;
        
        @Override
        public boolean isValid(List<String> uploadedFiles, ConstraintValidatorContext context) {
            if (uploadedFiles == null) {
                return true; // Optional field
            }
            
            if (uploadedFiles.size() > MAX_FILES) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Maximum of " + MAX_FILES + " uploaded files allowed")
                    .addConstraintViolation();
                return false;
            }
            
            for (int i = 0; i < uploadedFiles.size(); i++) {
                String fileName = uploadedFiles.get(i);
                if (fileName == null || fileName.trim().isEmpty()) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("File name at index " + i + " cannot be empty")
                        .addConstraintViolation();
                    return false;
                }
            }
            
            return true;
        }
    }
}

