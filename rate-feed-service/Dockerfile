FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_FILE=build/libs/rate-feed-service-0.1.0.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
