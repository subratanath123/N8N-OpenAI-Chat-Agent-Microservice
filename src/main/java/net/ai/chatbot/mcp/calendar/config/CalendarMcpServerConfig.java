package net.ai.chatbot.mcp.calendar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Calendar MCP Server
 * 
 * This configuration enables the MCP server and calendar tools.
 * Spring AI MCP Server will automatically discover @McpTool annotated methods
 * and expose them via the MCP protocol.
 * 
 * Configuration properties (application.yml):
 * spring:
 *   ai:
 *     mcp:
 *       server:
 *         enabled: true
 *         transport: http
 *         base-path: /mcp
 */
@Slf4j
@Configuration
public class CalendarMcpServerConfig {

    public CalendarMcpServerConfig() {
        log.info("Calendar MCP Server Configuration initialized");
        log.info("MCP tools will be available at /mcp endpoint");
    }
}

