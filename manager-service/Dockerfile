FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 프로젝트 루트에서 실행할 때의 상대 경로
COPY build/libs/manager-service-*.jar app.jar

EXPOSE 8084

# 환경변수로 프로파일 설정
ENTRYPOINT ["java", "-jar", "app.jar"]
