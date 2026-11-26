# CD 워크플로가 ./gradlew bootJar로 만든 jar를 담는다. 빌드는 이미지 밖에서 한다.
FROM eclipse-temurin:21-jre

WORKDIR /app

# root가 아닌 사용자로 실행한다.
RUN useradd --system --uid 1001 morak
# -plain.jar는 .dockerignore에서 제외하므로 실행 가능한 jar 하나만 남는다.
COPY build/libs/*.jar app.jar
USER morak

EXPOSE 8080

# t3.micro(메모리 1GB)에서 Redis와 함께 돌리므로 힙을 컨테이너 메모리의 70%로 제한한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
