# jdk17 Image Start
FROM openjdk:17

# 작업 디렉토리 설정
WORKDIR /app

# JSON 키 파일들 복사
COPY src/main/resources/iplan-firebase.json /app/
COPY src/main/resources/iplan-google.json /app/

# 인자 설정 - JAR_File
ARG JAR_FILE=build/libs/*.jar

# jar 파일 복제
COPY ${JAR_FILE} app.jar

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]