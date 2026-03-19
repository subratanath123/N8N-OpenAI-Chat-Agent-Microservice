package net.ai.chatbot.dto.mediaasset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for delete operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteResponse {
    private boolean success;
}
