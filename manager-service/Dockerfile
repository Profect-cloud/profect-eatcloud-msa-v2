FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 멀티모듈 빌드 결과 복사
COPY manager-service/build/libs/manager-service-*.jar app.jar

EXPOSE 8084

# 환경변수로 프로파일 설정
ENTRYPOINT ["java", "-jar", "app.jar"]