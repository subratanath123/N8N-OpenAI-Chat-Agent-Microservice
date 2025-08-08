package net.ai.chatbot.config;

import jakarta.servlet.http.HttpServletRequest;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.service.n8n.GenericN8NServiceImpl;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class N8NConfig {

    @Bean
    public GenericN8NService<Message, Object> n8nService() {
        return new GenericN8NServiceImpl<>();
    }

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
