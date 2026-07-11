# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B --no-transfer-progress package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN useradd -r -u 1001 spring
WORKDIR /app
COPY --from=build /app/target/paper-desk-backend-*.jar app.jar
USER spring
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
