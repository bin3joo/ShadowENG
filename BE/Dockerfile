FROM amazoncorretto:21-alpine AS build
WORKDIR /app

RUN apk add --no-cache dos2unix

COPY . .

RUN dos2unix gradlew && chmod +x gradlew
RUN ./gradlew bootJar --no-daemon -x test --info


FROM amazoncorretto:21-alpine
WORKDIR /app

# spring user 생성
RUN addgroup -S spring && adduser -S spring -G spring

# logs 폴더 생성 + 권한 부여
RUN mkdir -p /app/logs && chown -R spring:spring /app

# jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]