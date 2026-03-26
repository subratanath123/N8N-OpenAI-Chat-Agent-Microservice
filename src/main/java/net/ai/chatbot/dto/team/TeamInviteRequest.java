package net.ai.chatbot.dto.team;

import lombok.Data;

@Data
public class TeamInviteRequest {
    /** Invitee email address */
    private String email;
    /** Admin, Editor, or Viewer (case-insensitive) */
    private String role;
}
