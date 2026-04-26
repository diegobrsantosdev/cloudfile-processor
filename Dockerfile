# STAGE 1: Build (Maven with Java 21)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies to leverage Docker cache
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the JAR file
COPY src ./src
RUN mvn clean package -DskipTests

# STAGE 2: Base Runtime Image (Lightweight JRE 21)
FROM eclipse-temurin:21-jre-jammy AS base
WORKDIR /app
# Copy the generated JAR from the build stage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# STAGE 3: API Target
FROM base AS api
# Runs with default Spring profiles (Web server enabled)
ENTRYPOINT ["java", "-jar", "app.jar"]

# STAGE 4: Worker Target
FROM base AS worker
# Forces the 'worker' profile to disable the embedded Tomcat (web-application-type: none)
ENV SPRING_PROFILES_ACTIVE=worker
ENTRYPOINT ["java", "-jar", "app.jar"]