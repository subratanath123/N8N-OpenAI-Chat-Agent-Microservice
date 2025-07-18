package net.ai.chatbot.service.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DomainService {

    public static boolean isAllowedOrigin(String origin) {
        log.info("Request comes from origin {}", origin);
        return getAllowedOrigins().contains(origin);
    }

    public static List<String> getAllowedOrigins() {
        return List.of("http://localhost:3000", "http://localhost:3002", "http://localhost:8000");
    }

}
