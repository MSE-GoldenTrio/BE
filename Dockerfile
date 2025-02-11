# jdk17 Image Start
FROM openjdk:17

# 작업 디렉토리 설정
WORKDIR /app

# 인자 설정 - JAR_File
ARG JAR_FILE=build/libs/*.jar

# jar 파일 복제
COPY ${JAR_FILE} app.jar

# Firebase 서비스 계정 파일 복사
COPY ./src/main/resources/iplan-firebase.json /app/iplan-firebase.json

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]

ENV FIREBASE_CONFIG_PATH=/app/iplan-firebase.json