FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY eureka-server/build/libs/eureka-server-*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]