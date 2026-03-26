package net.ai.chatbot.dto.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberResponse {
    private String id;
    private String email;
    private String name;
    private String role;
    private String joinedDate;
}
