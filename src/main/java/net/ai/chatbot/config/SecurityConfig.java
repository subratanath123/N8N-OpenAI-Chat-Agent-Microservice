package net.ai.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize
                                -> authorize
//                        .requestMatchers("/v1/**")
//                        .authenticated()
                                .anyRequest()
                                .permitAll()
                )
                .oauth2ResourceServer(oauth2
                        -> oauth2.authenticationManagerResolver(authenticationManagerResolver())
                );

        return http.build();
    }

    @Bean
    public JwtIssuerAuthenticationManagerResolver authenticationManagerResolver() {
        return JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers("https://accounts.google.com");
    }
}
