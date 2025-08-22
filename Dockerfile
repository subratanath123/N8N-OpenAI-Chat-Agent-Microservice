FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/JadeAiBot.jar app.jar

# Create a non-root user for security
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser
USER appuser

# Expose the port dynamically
EXPOSE 8000

# Set environment variables for Render.com
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check - use dynamic port
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://0.0.0.0:8000/actuator/health || exit 1

# Run the application - let Spring Boot read PORT from environment
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]