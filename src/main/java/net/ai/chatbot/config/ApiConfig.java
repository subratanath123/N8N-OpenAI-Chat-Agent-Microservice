package net.ai.chatbot.config;

import jakarta.servlet.http.HttpServletRequest;
import net.ai.chatbot.service.openai.DomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class ApiConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DomainService domainService) throws Exception {
        http
                .authorizeHttpRequests(authorize
                                -> authorize
//                        .requestMatchers("/v1/**")
//                        .authenticated()
                                .anyRequest()
                                .permitAll()
                ).cors(cors -> cors.configurationSource(corsConfigurationSource(domainService)))
                .oauth2ResourceServer(oauth2
                        -> oauth2.authenticationManagerResolver(authenticationManagerResolver())
                );

        return http.build();
    }
    
    @Bean
    public JwtIssuerAuthenticationManagerResolver authenticationManagerResolver() {
        return JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers("https://accounts.google.com");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(DomainService domainService) {
        return new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration config = new CorsConfiguration();
                String origin = request.getHeader("Origin");

                if (origin != null && domainService.isAllowedOrigin(origin)) {
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
