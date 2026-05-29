# STAGE 1: Build (Maven with Java 25)
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# STAGE 2: Base Runtime Image (Lightweight JRE 25)
FROM eclipse-temurin:25-jre-alpine AS base
WORKDIR /app
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