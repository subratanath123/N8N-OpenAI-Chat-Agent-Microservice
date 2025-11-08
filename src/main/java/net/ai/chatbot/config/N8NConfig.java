package net.ai.chatbot.config;

import jakarta.servlet.http.HttpServletRequest;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class N8NConfig {

    @Bean
    public RestTemplate n8nRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        restTemplate.getInterceptors().add((request, body, execution) -> {

            HttpServletRequest currentRequest = getCurrentHttpRequest();
            if (currentRequest != null) {
                String authHeader = currentRequest.getHeader("Authorization");

                if (authHeader != null) {
                    request.getHeaders().add("authenticated", "true");
                    request.getHeaders().add("email", AuthUtils.getEmail());
                    request.getHeaders().add("conversationid", currentRequest.getHeader("sessionId"));
                }
            }

            return execution.execute(request, body);
        });

        return restTemplate;
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
