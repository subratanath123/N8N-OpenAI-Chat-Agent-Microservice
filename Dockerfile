FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Install dependencies for Playwright
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# No need to install Node.js or npm - Playwright Java bundles everything it needs
# Playwright Java will automatically download and manage browsers at runtime

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
