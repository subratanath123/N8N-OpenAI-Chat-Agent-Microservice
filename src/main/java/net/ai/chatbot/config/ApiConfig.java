package net.ai.chatbot.config;

import jakarta.servlet.http.HttpServletRequest;
import net.ai.chatbot.service.openai.DomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class ApiConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DomainService domainService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(domainService)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/api/n8n/anonymous/**", "/v1/api/public/**", "/mcp/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwkSetUri("https://ruling-ferret-57.clerk.accounts.dev/.well-known/jwks.json")
                        )
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(DomainService domainService) {
        return new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration config = new CorsConfiguration();
                String origin = request.getHeader("Origin");

                if (origin != null && DomainService.isAllowedOrigin(origin)) {
                    config.addAllowedOrigin(origin);
                    config.setAllowCredentials(true);
                    config.addAllowedHeader("*");
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                    config.addAllowedMethod("*");
                }

                return config;
            }
        };
    }
}
