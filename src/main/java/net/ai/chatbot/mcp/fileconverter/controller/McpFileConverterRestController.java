package net.ai.chatbot.mcp.fileconverter.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.mcp.fileconverter.service.FileConverterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Server for file format conversion.
 * Converts text content to: txt, java, csv, docx, pdf, xlsx
 * Saves via AttachmentStorageService and returns download link (like AttachmentDownloadController).
 * Uses free libraries: Apache Commons CSV, Apache POI, Apache PDFBox
 * <p>
 * Endpoint: POST /mcp/file-converter (JSON-RPC 2.0)
 * Health: GET /mcp/file-converter/health
 */
@Slf4j
@RestController
@RequestMapping("/mcp/file-converter")
@RequiredArgsConstructor
public class McpFileConverterRestController {

    private final FileConverterService fileConverterService;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleJsonRpc(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String jsonrpc = (String) request.get("jsonrpc");
        String method = (String) request.get("method");
        Object id = request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        log.info("MCP File Converter JSON-RPC: method='{}', id={}", method, id);

        if (!"2.0".equals(jsonrpc)) {
            return ResponseEntity.badRequest()
                    .body(createJsonRpcError(id, -32600, "Invalid Request: jsonrpc must be '2.0'", null));
        }
        if (method == null || method.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(createJsonRpcError(id, -32600, "Invalid Request: method is required", null));
        }

        return switch (method) {
            case "tools/list", "tools.list" -> handleToolsList(id);
            case "tools/call", "tools.call" -> handleToolsCall(id, params, httpRequest);
            case "initialize" -> handleInitialize(id, params);
            default -> ResponseEntity.ok(
                    createJsonRpcError(id, -32601, "Method not found: " + method, null));
        };
    }

    private ResponseEntity<Map<String, Object>> handleToolsList(Object id) {
        log.info("MCP File Converter: Handling tools/list");

        Map<String, Object> contentProp = new java.util.HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "Text content to convert");
        contentProp.put("inputType", "string");

        Map<String, Object> extensionProp = new java.util.HashMap<>();
        extensionProp.put("type", "string");
        extensionProp.put("description", "Target file extension: txt, java, csv, docx, pdf, xlsx");
        extensionProp.put("inputType", "string");

        Map<String, Object> chatbotIdProp = new java.util.HashMap<>();
        chatbotIdProp.put("type", "string");
        chatbotIdProp.put("description", "Chatbot ID for storage (required for download link)");
        chatbotIdProp.put("inputType", "string");

        Map<String, Object> filenameProp = new java.util.HashMap<>();
        filenameProp.put("type", "string");
        filenameProp.put("description", "Optional output filename (without extension)");
        filenameProp.put("inputType", "string");

        Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("content", contentProp);
        properties.put("extension", extensionProp);
        properties.put("chatbotId", chatbotIdProp);
        properties.put("filename", filenameProp);

        Map<String, Object> inputSchema = new java.util.HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("content", "extension", "chatbotId"));

        Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("name", "convert_content_to_file");
        tool.put("description", "Convert text content to a file format. Supports: txt, java, csv, docx, pdf, xlsx. Saves file and returns download link.");
        tool.put("inputSchema", inputSchema);

        Map<String, Object> result = Map.of("tools", List.of(tool));
        return ResponseEntity.ok(createJsonRpcSuccess(id, result));
    }

    private ResponseEntity<Map<String, Object>> handleToolsCall(Object id, Map<String, Object> params,
                                                                 HttpServletRequest httpRequest) {
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        log.info("MCP File Converter: Calling tool '{}'", toolName);

        if (!"convert_content_to_file".equals(toolName)) {
            return ResponseEntity.ok(
                    createJsonRpcError(id, -32602, "Unknown tool: " + toolName,
                            Map.of("availableTools", List.of("convert_content_to_file"))));
        }

        String content = (String) arguments.get("content");
        String extension = (String) arguments.get("extension");
        String chatbotId = (String) arguments.get("chatbotId");
        String filename = (String) arguments.get("filename");

        if (extension == null || extension.isBlank()) {
            return ResponseEntity.ok(
                    createJsonRpcError(id, -32602, "Missing required argument: extension", null));
        }
        if (chatbotId == null || chatbotId.isBlank()) {
            return ResponseEntity.ok(
                    createJsonRpcError(id, -32602, "Missing required argument: chatbotId", null));
        }

        String baseUrl = configuredBaseUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = buildBaseUrlFromRequest(httpRequest);
        }

        try {
            AttachmentStorageResult result = fileConverterService.convertAndStore(
                    content, extension, filename, chatbotId, baseUrl);

            StringBuilder responseText = new StringBuilder();
            responseText.append("File converted and saved successfully!\n\n");
            responseText.append("Format: ").append(extension.toLowerCase()).append("\n");
            responseText.append("Filename: ").append(result.getFileName()).append("\n");
            responseText.append("Size: ").append(result.getFileSize()).append(" bytes\n\n");
            responseText.append("Download link:\n");
            responseText.append(result.getDownloadUrl());

            Map<String, Object> responseResult = new java.util.HashMap<>();
            responseResult.put("content", List.of(Map.of("type", "text", "text", responseText.toString())));
            responseResult.put("isError", false);
            responseResult.put("fileId", result.getFileId());
            responseResult.put("downloadUrl", result.getDownloadUrl());
            responseResult.put("filename", result.getFileName());
            responseResult.put("fileSizeBytes", result.getFileSize());

            return ResponseEntity.ok(createJsonRpcSuccess(id, responseResult));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(
                    createJsonRpcError(id, -32602, e.getMessage(), null));
        } catch (Exception e) {
            log.error("File conversion failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(
                    createJsonRpcError(id, -32603, "Conversion failed: " + e.getMessage(), null));
        }
    }

    private String buildBaseUrlFromRequest(HttpServletRequest request) {
        if (request == null) return "";
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        return scheme + "://" + serverName + (defaultPort ? "" : ":" + port) + contextPath;
    }

    private ResponseEntity<Map<String, Object>> handleInitialize(Object id, Map<String, Object> params) {
        log.info("MCP File Converter: Handling initialize");

        Map<String, Object> result = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false)
                ),
                "serverInfo", Map.of(
                        "name", "File Converter MCP Server",
                        "version", "1.0.0"
                )
        );
        return ResponseEntity.ok(createJsonRpcSuccess(id, result));
    }

    private Map<String, Object> createJsonRpcSuccess(Object id, Map<String, Object> result) {
        return Map.of(
                "jsonrpc", "2.0",
                "result", result,
                "id", id != null ? id : 0
        );
    }

    private Map<String, Object> createJsonRpcError(Object id, int code, String message, Object data) {
        Map<String, Object> error = data != null
                ? Map.of("code", code, "message", message, "data", data)
                : Map.of("code", code, "message", message);
        return Map.of(
                "jsonrpc", "2.0",
                "error", error,
                "id", id != null ? id : 0
        );
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "File Converter MCP Server",
                "availableTools", List.of("convert_content_to_file"),
                "supportedFormats", List.of("txt", "java", "csv", "docx", "pdf", "xlsx")
        ));
    }
}
