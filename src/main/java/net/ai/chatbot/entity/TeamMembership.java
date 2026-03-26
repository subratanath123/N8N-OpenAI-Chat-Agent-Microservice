package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Links a team member to an owner account. Members with ADMIN or EDITOR can configure
 * the owner's chatbots; VIEWER can view only.
 */
@Document(collection = "team_memberships")
@CompoundIndex(name = "owner_member_unique", def = "{'ownerEmail': 1, 'memberEmail': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMembership {

    @Id
    private String id;

    /** Normalized: lower-case owner email (matches chatbot.createdBy) */
    private String ownerEmail;

    /** Normalized: lower-case invitee email */
    private String memberEmail;

    /** ADMIN, EDITOR, or VIEWER */
    private String role;

    private Instant createdAt;
}
