package net.ai.chatbot.controller.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.workflow.WorkflowConfigRequest;
import net.ai.chatbot.dto.workflow.WorkflowConfigResponse;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/v1/api/chatbot/{chatbotId}/workflow")
@RequiredArgsConstructor
@Slf4j
public class WorkflowConfigController {

    private final WorkflowConfigService workflowConfigService;
    private static final int TEST_TIMEOUT_MS = 10_000;

    /** GET /v1/api/chatbot/{chatbotId}/workflow — authValue masked as ●●●●●● */
    @GetMapping
    public ResponseEntity<?> getWorkflow(@PathVariable String chatbotId) {
        try {
            return ResponseEntity.ok(workflowConfigService.getWorkflow(chatbotId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching workflow for {}: {}", chatbotId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /v1/api/chatbot/{chatbotId}/workflow
     * Creates or replaces workflow config.
     * Send authValue as plaintext — backend encrypts before storing.
     * Omit / send "••••••" to preserve existing credentials.
     */
    @PostMapping
    public ResponseEntity<?> saveWorkflow(@PathVariable String chatbotId,
                                          @RequestBody WorkflowConfigRequest request) {
        try {
            String ownerId = AuthUtils.getUserEmail();
            WorkflowConfigResponse response = workflowConfigService.saveWorkflow(chatbotId, ownerId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error saving workflow for {}: {}", chatbotId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /v1/api/chatbot/{chatbotId}/workflow/test
     * Server-side proxy for Workflow Test Console to avoid browser CORS limitations.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testWorkflowAction(@PathVariable String chatbotId,
                                                @RequestHeader(value = "userToken", required = false) String userTokenHeader,
                                                @RequestBody WorkflowTestRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Action URL is required"));
        }

        try {
            RestTemplate rt = new RestTemplate();
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(TEST_TIMEOUT_MS);
            factory.setReadTimeout(TEST_TIMEOUT_MS);
            rt.setRequestFactory(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            applyAuthHeaders(headers, request, userTokenHeader);

            HttpMethod method = HttpMethod.valueOf(request.getMethod() == null ? "POST" : request.getMethod());
            String payload = request.getBody() == null ? "" : request.getBody();
            HttpEntity<String> entity = method == HttpMethod.GET
                    ? new HttpEntity<>(headers)
                    : new HttpEntity<>(payload, headers);

            ResponseEntity<String> upstream = rt.exchange(request.getUrl(), method, entity, String.class);
            return ResponseEntity.ok(Map.of(
                    "status", upstream.getStatusCode().value(),
                    "ok", upstream.getStatusCode().is2xxSuccessful(),
                    "responseBody", upstream.getBody() == null ? "" : upstream.getBody()
            ));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.ok(Map.of(
                    "status", e.getStatusCode().value(),
                    "ok", false,
                    "responseBody", e.getResponseBodyAsString() == null ? "" : e.getResponseBodyAsString()
            ));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of(
                    "status", 504,
                    "ok", false,
                    "error", "Connection timed out after 10 seconds",
                    "responseBody", ""
            ));
        } catch (Exception e) {
            log.error("Workflow test proxy failed for chatbot {}: {}", chatbotId, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "status", 500,
                    "ok", false,
                    "error", "Proxy execution failed: " + e.getMessage(),
                    "responseBody", ""
            ));
        }
    }

    /**
     * Applies auth headers to the proxied request. Prefer {@code userToken} from the HTTP header when
     * substituting {@code {{userToken}}}; forwards a {@code userToken} header upstream when header or body token is set
     * (same contract as chat → N8N).
     */
    private void applyAuthHeaders(HttpHeaders headers, WorkflowTestRequest request, String userTokenHeader) {
        String effectiveUserToken = firstNonBlank(userTokenHeader, request.getUserToken());

        String authType = request.getAuthType() == null ? "none" : request.getAuthType();
        String authValue = request.getAuthValue() == null ? "" : request.getAuthValue();
        if ("{{userToken}}".equals(authValue.trim())) {
            authValue = effectiveUserToken == null ? "" : effectiveUserToken;
        }
        if (authValue.isBlank() || "none".equalsIgnoreCase(authType)) {
            // no auth headers from credential — still forward userToken for upstreams that read it
        } else {
            switch (authType.toLowerCase()) {
                case "bearer" -> headers.set("Authorization", "Bearer " + authValue);
                case "apikey" -> {
                    String headerName = (request.getApiKeyHeader() == null || request.getApiKeyHeader().isBlank())
                            ? "X-API-Key" : request.getApiKeyHeader();
                    headers.set(headerName, authValue);
                }
                case "basic" -> headers.set("Authorization", "Basic " + authValue);
                default -> {
                }
            }
        }

        if (effectiveUserToken != null && !effectiveUserToken.isBlank()) {
            headers.set("userToken", effectiveUserToken);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    public static class WorkflowTestRequest {
        private String url;
        private String method;
        private String authType;
        private String authValue;
        private String apiKeyHeader;
        private String body;
        private String userToken;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getAuthValue() {
            return authValue;
        }

        public void setAuthValue(String authValue) {
            this.authValue = authValue;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getUserToken() {
            return userToken;
        }

        public void setUserToken(String userToken) {
            this.userToken = userToken;
        }
    }
}
