FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy pre-built JAR from host
# The JAR should be built on the host using: ./gradlew bootJar
# Make sure to build the JAR before building the Docker image
COPY build/libs/JadeAiBot.jar JadeAiBot.jar

# Create a non-root user for security (Alpine uses addgroup/adduser)
#RUN addgroup -g 1001 -S appgroup && \
#    adduser -u 1001 -S -G appgroup appuser && \
#    chown -R appuser:appgroup /app
#USER appuser

# Expose the port
EXPOSE 8080

# Set default environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar JadeAiBot.jar"]