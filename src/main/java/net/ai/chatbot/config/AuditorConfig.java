package net.ai.chatbot.config;

import net.ai.chatbot.utils.AuthUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditorConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(AuthUtils.getEmail())
                .or(() -> Optional.of("system"));
    }
}