# Multi-stage Dockerfile for NexusFlow Stream Processor
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S nexusgroup && adduser -S nexususer -G nexusgroup

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R nexususer:nexusgroup /app

USER nexususer
EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
