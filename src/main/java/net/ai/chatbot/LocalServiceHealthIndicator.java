package net.ai.chatbot;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class LocalServiceHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {

        // Logic to check if this local instance should be considered "UP"
        boolean isLocalDatabaseUp = checkDatabase();

        if (!isLocalDatabaseUp) {
            return Health.down().withDetail("Reason", "Local DB is down").build();
        }

        return Health.up().build();
    }

    private boolean checkDatabase() {
        // Your logic here
        return true;
    }
}