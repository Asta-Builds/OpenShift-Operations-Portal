# Stage 1: Build the Agent
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn clean package -DskipTests

# Stage 2: Lightweight, Air-Gapped Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Numeric user so runAsNonRoot can be verified on plain Kubernetes; OpenShift assigns its own UID anyway
RUN addgroup -S -g 1001 appgroup && adduser -S -u 1001 -G appgroup appuser

COPY --from=builder /workspace/target/*.jar app.jar

USER 1001
EXPOSE 8080

# Half the container memory for the heap: the JVM's own metaspace, code cache and threads take about 120 MB
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=50.0", "-jar", "app.jar"]
