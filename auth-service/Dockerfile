FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S commerce && adduser -S commerce -G commerce
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
USER commerce
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
