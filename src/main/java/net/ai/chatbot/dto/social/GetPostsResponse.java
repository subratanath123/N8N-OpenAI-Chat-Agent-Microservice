package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GetPostsResponse {
    private List<SocialPostResponse> posts;
    private int totalCount;
}
