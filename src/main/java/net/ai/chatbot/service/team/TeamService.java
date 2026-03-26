package net.ai.chatbot.service.team;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.TeamMembershipDao;
import net.ai.chatbot.dto.team.TeamInviteRequest;
import net.ai.chatbot.dto.team.TeamMemberResponse;
import net.ai.chatbot.entity.TeamMembership;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamMembershipDao teamMembershipDao;

    public static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public List<TeamMemberResponse> listMembersForOwner(String ownerEmailRaw) {
        String ownerEmail = normalizeEmail(ownerEmailRaw);
        return teamMembershipDao.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TeamMemberResponse invite(String ownerEmailRaw, TeamInviteRequest request) {
        String ownerEmail = normalizeEmail(ownerEmailRaw);
        String memberEmail = normalizeEmail(request.getEmail());
        if (memberEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (memberEmail.equals(ownerEmail)) {
            throw new IllegalArgumentException("You cannot invite yourself");
        }

        String role = normalizeRole(request.getRole());

        TeamMembership existing = teamMembershipDao.findByOwnerEmailAndMemberEmail(ownerEmail, memberEmail)
                .orElse(null);
        if (existing != null) {
            existing.setRole(role);
            teamMembershipDao.save(existing);
            log.info("Updated team membership role for {} -> {}", ownerEmail, memberEmail);
            return toResponse(existing);
        }

        TeamMembership created = TeamMembership.builder()
                .id(UUID.randomUUID().toString())
                .ownerEmail(ownerEmail)
                .memberEmail(memberEmail)
                .role(role)
                .createdAt(Instant.now())
                .build();
        teamMembershipDao.save(created);
        log.info("Created team membership {} -> {} as {}", ownerEmail, memberEmail, role);
        return toResponse(created);
    }

    public void removeMember(String ownerEmailRaw, String membershipId) {
        String ownerEmail = normalizeEmail(ownerEmailRaw);
        TeamMembership m = teamMembershipDao.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        if (!ownerEmail.equals(m.getOwnerEmail())) {
            throw new SecurityException("Not allowed to remove this membership");
        }
        teamMembershipDao.delete(m);
        log.info("Removed team membership {} for owner {}", membershipId, ownerEmail);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "VIEWER";
        }
        String u = role.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "ADMIN", "ADMINISTRATOR" -> "ADMIN";
            case "EDITOR", "EDIT" -> "EDITOR";
            case "VIEWER", "READ", "READONLY" -> "VIEWER";
            default -> throw new IllegalArgumentException("Invalid role. Use Admin, Editor, or Viewer.");
        };
    }

    private TeamMemberResponse toResponse(TeamMembership m) {
        String displayRole = switch (m.getRole()) {
            case "ADMIN" -> "Admin";
            case "EDITOR" -> "Editor";
            default -> "Viewer";
        };
        String local = m.getMemberEmail();
        int at = local.indexOf('@');
        if (at > 0) {
            local = local.substring(0, at);
        }
        return TeamMemberResponse.builder()
                .id(m.getId())
                .email(m.getMemberEmail())
                .name(local)
                .role(displayRole)
                .joinedDate(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "")
                .build();
    }
}
