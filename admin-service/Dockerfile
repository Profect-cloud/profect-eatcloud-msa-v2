FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 현재 디렉토리에서 빌드된 JAR 파일 복사
COPY build/libs/admin-service-*.jar app.jar

EXPOSE 8083

# 환경변수로 프로파일 설정
ENTRYPOINT ["java", "-jar", "app.jar"]