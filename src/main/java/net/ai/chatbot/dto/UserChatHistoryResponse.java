package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserChatHistoryResponse {
    
    private boolean success;
    private String message;
    private List<UserChatHistory> data;
    private String errorMessage;
    private long timestamp;
    private int totalCount;
    private int currentPage;
    private int pageSize;
    
    // Success response with data
    public static UserChatHistoryResponse success(List<UserChatHistory> data) {
        return UserChatHistoryResponse.builder()
                .success(true)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .totalCount(data != null ? data.size() : 0)
                .build();
    }
    
    // Success response with pagination
    public static UserChatHistoryResponse success(List<UserChatHistory> data, int totalCount, int currentPage, int pageSize) {
        return UserChatHistoryResponse.builder()
                .success(true)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .totalCount(totalCount)
                .currentPage(currentPage)
                .pageSize(pageSize)
                .build();
    }
    
    // Success response with message
    public static UserChatHistoryResponse success(String message) {
        return UserChatHistoryResponse.builder()
                .success(true)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    // Error response
    public static UserChatHistoryResponse error(String errorMessage) {
        return UserChatHistoryResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
