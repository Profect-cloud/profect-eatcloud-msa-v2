FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 프로젝트 루트에서 실행할 때의 상대 경로
COPY build/libs/auth-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
