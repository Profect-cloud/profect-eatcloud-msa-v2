# ==== runtime only (jar is built by GitHub Actions) ====
FROM eclipse-temurin:21-jre-alpine

# timezone/healthcheck용 패키지 + 비루트 계정
RUN apk add --no-cache tzdata curl \
 && addgroup -S spring && adduser -S spring -G spring

ENV TZ=Asia/Seoul
WORKDIR /app

# GitHub Actions에서 생성한 JAR을 컨텍스트에서 복사
COPY build/libs/*.jar app.jar

# (선택) JVM 옵션은 ENTRYPOINT에 직접 박아서 환경변수 의존 줄임
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

USER spring
ENTRYPOINT ["sh","-c","java -XX:+UseZGC -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -jar app.jar"]
