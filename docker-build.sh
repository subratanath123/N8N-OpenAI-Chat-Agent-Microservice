#!/bin/bash

# Helper script to build JAR and Docker image
# Usage: ./docker-build.sh [dev|qa|prod]

set -e

ENV=${1:-dev}
IMAGE_TAG="jade-ai-bot:${ENV}"

echo "Building JAR file..."
./gradlew bootJar

if [ ! -f "build/libs/JadeAiBot.jar" ]; then
    echo "Error: JAR file not found at build/libs/JadeAiBot.jar"
    echo "Build failed!"
    exit 1
fi

echo "JAR file built successfully!"
echo "Building Docker image: ${IMAGE_TAG}"

docker build -t ${IMAGE_TAG} .

echo "Docker image built successfully: ${IMAGE_TAG}"
echo ""
echo "To run with docker-compose:"
echo "  docker-compose -f docker-compose.${ENV}.yml up -d"

