package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.*;
import net.ai.chatbot.service.dashboard.DashboardService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get comprehensive dashboard statistics
     * GET /v1/api/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        try {
            log.info("Fetching dashboard stats for user: {}", AuthUtils.getEmail());
            DashboardStatsResponse stats = dashboardService.getDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching dashboard stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get overall statistics
     * GET /v1/api/dashboard/stats/overall
     */
    @GetMapping("/stats/overall")
    public ResponseEntity<OverallStats> getOverallStats() {
        try {
            log.info("Fetching overall stats for user: {}", AuthUtils.getEmail());
            OverallStats stats = dashboardService.getOverallStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching overall stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get chatbot statistics
     * GET /v1/api/dashboard/stats/chatbots
     */
    @GetMapping("/stats/chatbots")
    public ResponseEntity<ChatBotStats> getChatBotStats() {
        try {
            log.info("Fetching chatbot stats for user: {}", AuthUtils.getEmail());
            ChatBotStats stats = dashboardService.getChatBotStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching chatbot stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get conversation statistics
     * GET /v1/api/dashboard/stats/conversations
     */
    @GetMapping("/stats/conversations")
    public ResponseEntity<ConversationStats> getConversationStats() {
        try {
            log.info("Fetching conversation stats for user: {}", AuthUtils.getEmail());
            ConversationStats stats = dashboardService.getConversationStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching conversation stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage statistics
     * GET /v1/api/dashboard/stats/usage
     */
    @GetMapping("/stats/usage")
    public ResponseEntity<UsageStats> getUsageStats() {
        try {
            log.info("Fetching usage stats for user: {}", AuthUtils.getEmail());
            UsageStats stats = dashboardService.getUsageStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching usage stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage over time (time series data)
     * GET /v1/api/dashboard/stats/usage-over-time?days=30
     */
    @GetMapping("/stats/usage-over-time")
    public ResponseEntity<List<TimeSeriesData>> getUsageOverTime(
            @RequestParam(defaultValue = "30") int days) {
        try {
            log.info("Fetching usage over time for {} days for user: {}", days, AuthUtils.getEmail());
            List<TimeSeriesData> data = dashboardService.getUsageOverTime(days);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching usage over time", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top chatbots by activity
     * GET /v1/api/dashboard/top/chatbots?limit=10
     */
    @GetMapping("/top/chatbots")
    public ResponseEntity<List<TopChatBot>> getTopChatBots(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching top {} chatbots for user: {}", limit, AuthUtils.getEmail());
            List<TopChatBot> topChatBots = dashboardService.getTopChatBots(limit);
            return ResponseEntity.ok(topChatBots);
        } catch (Exception e) {
            log.error("Error fetching top chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top active users
     * GET /v1/api/dashboard/top/users?limit=10
     */
    @GetMapping("/top/users")
    public ResponseEntity<List<UserActivity>> getTopActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching top {} active users for user: {}", limit, AuthUtils.getEmail());
            List<UserActivity> topUsers = dashboardService.getTopActiveUsers(limit);
            return ResponseEntity.ok(topUsers);
        } catch (Exception e) {
            log.error("Error fetching top active users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

