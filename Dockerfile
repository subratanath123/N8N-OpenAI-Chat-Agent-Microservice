FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN ./gradlew build

FROM openjdk:17.0.1-jdk-slim
COPY --from=build build/libs/JadeAiBot.jar JadeAiBot.jar
EXPOSE 8000
ENTRYPOINT ["java","-jar","JadeAiBot.jar"]