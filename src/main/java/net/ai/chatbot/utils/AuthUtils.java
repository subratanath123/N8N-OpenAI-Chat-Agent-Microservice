package net.ai.chatbot.utils;

import net.ai.chatbot.dto.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class AuthUtils {

    public static String getEmail() {
        var context = SecurityContextHolder.getContext();
        if (context == null) {
            return null;
        }

        var authentication = context.getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken authenticationToken)) {
            return null;
        }

        String sub = (String) authenticationToken.getTokenAttributes().get("sub");

        return sub.contains("@")
                ? sub
                : (String) authenticationToken.getTokenAttributes().get("email");
    }

    public static boolean isAdmin() {
        return "shuvra.dev9@gmail.com".equals(getEmail());
    }

    public static User getUser() {
        JwtAuthenticationToken authenticationToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        String sub = (String) authenticationToken.getTokenAttributes().get("sub");

        String email = sub.contains("@")
                ? sub
                : (String) authenticationToken.getTokenAttributes().get("email");

        String name = (String) authenticationToken.getTokenAttributes().get("name");
        String picture = (String) authenticationToken.getTokenAttributes().get("picture");

        return User.builder()
                .userName(name)
                .picture(picture)
                .email(email)
                .build();


    }
}
