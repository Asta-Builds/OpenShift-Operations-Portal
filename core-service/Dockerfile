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

# Numeric non-root user so runAsNonRoot can be verified on plain Kubernetes; OpenShift assigns its own UID in group 0
RUN addgroup -S -g 1001 appgroup && adduser -S -u 1001 -G appgroup appuser

COPY --from=builder /workspace/target/*.jar app.jar

USER 1001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
