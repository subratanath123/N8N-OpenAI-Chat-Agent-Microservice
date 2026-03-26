package net.ai.chatbot.service.googlecalendar;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dao.TeamMembershipDao;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.TeamMembership;
import net.ai.chatbot.service.team.TeamService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Verifies chatbot ownership and team-based access.
 */
@Service
@Slf4j
public class ChatbotOwnershipService {

    private final ChatBotDao chatBotDao;
    private final TeamMembershipDao teamMembershipDao;

    public ChatbotOwnershipService(ChatBotDao chatBotDao, TeamMembershipDao teamMembershipDao) {
        this.chatBotDao = chatBotDao;
        this.teamMembershipDao = teamMembershipDao;
    }

    private static String norm(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean emailsEqual(String a, String b) {
        if (a == null || b == null) return false;
        return norm(a).equals(norm(b));
    }

    /**
     * Strict owner only — e.g. delete chatbot.
     */
    public void verifyOwnership(String chatbotId, String userEmail) {
        log.debug("Verifying chatbot ownership: chatbotId={}, userEmail={}", chatbotId, userEmail);

        ChatBot chatBot = chatBotDao.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + chatbotId));

        if (!emailsEqual(chatBot.getCreatedBy(), userEmail)) {
            log.warn("Ownership verification failed: chatbotId={}, userEmail={}, owner={}",
                    chatbotId, userEmail, chatBot.getCreatedBy());
            throw new SecurityException("User does not own this chatbot");
        }

        log.debug("Ownership verification successful: chatbotId={}, userEmail={}", chatbotId, userEmail);
    }

    /**
     * Owner or any team member (viewer can open dashboard / read).
     */
    public void verifyCanView(String chatbotId, String userEmail) {
        ChatBot chatBot = chatBotDao.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + chatbotId));

        if (emailsEqual(chatBot.getCreatedBy(), userEmail)) {
            return;
        }

        Optional<TeamMembership> tm = teamMembershipDao.findByOwnerEmailAndMemberEmail(
                TeamService.normalizeEmail(chatBot.getCreatedBy()),
                TeamService.normalizeEmail(userEmail)
        );
        if (tm.isPresent()) {
            return;
        }

        throw new SecurityException("User does not have access to this chatbot");
    }

    /**
     * Owner, or team member with Admin or Editor (configure chatbots, workflows, integrations).
     */
    public void verifyCanConfigure(String chatbotId, String userEmail) {
        ChatBot chatBot = chatBotDao.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + chatbotId));

        if (emailsEqual(chatBot.getCreatedBy(), userEmail)) {
            return;
        }

        TeamMembership tm = teamMembershipDao
                .findByOwnerEmailAndMemberEmail(
                        TeamService.normalizeEmail(chatBot.getCreatedBy()),
                        TeamService.normalizeEmail(userEmail)
                )
                .orElseThrow(() -> new SecurityException("User does not have access to this chatbot"));

        String role = tm.getRole();
        if ("ADMIN".equals(role) || "EDITOR".equals(role)) {
            return;
        }

        throw new SecurityException("Insufficient permissions to modify this chatbot");
    }

    /**
     * True if the user may edit settings, workflow, integrations (owner or team Admin/Editor).
     */
    public boolean canConfigureChatbot(String chatbotId, String userEmail) {
        try {
            verifyCanConfigure(chatbotId, userEmail);
            return true;
        } catch (IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    public boolean ownschatbot(String chatbotId, String userEmail) {
        try {
            verifyCanConfigure(chatbotId, userEmail);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
