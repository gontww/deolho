# =============================================
# DeOlho — Smart Error Monitor
# =============================================
# Build:  ./mvnw clean package -DskipTests
# Run:    ./mvnw spring-boot:run
# Docker: docker compose up
# =============================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -B

# --- Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
