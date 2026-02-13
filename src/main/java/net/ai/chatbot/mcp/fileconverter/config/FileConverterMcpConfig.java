package net.ai.chatbot.mcp.fileconverter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for File Converter MCP Server.
 * Endpoint: POST /mcp/file-converter (JSON-RPC 2.0)
 * Tool: convert_content_to_file - converts text to txt, java, csv, docx, pdf, xlsx
 */
@Slf4j
@Configuration
public class FileConverterMcpConfig {

    public FileConverterMcpConfig() {
        log.info("File Converter MCP Server initialized at /mcp/file-converter");
        log.info("Supported formats: txt, java, csv, docx, pdf, xlsx");
    }
}
