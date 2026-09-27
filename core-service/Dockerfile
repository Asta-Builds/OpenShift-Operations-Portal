# Stage 1: Build the Application
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Copy build descriptor and source code
COPY pom.xml .
COPY src src

# Run build
RUN mvn clean package -DskipTests

# Stage 2: Lightweight, Air-Gapped Production Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for OpenShift security context compliance
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/target/*.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
