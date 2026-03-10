FROM amazoncorretto:21-alpine AS build
WORKDIR /app

RUN apk add --no-cache dos2unix

COPY . .

RUN dos2unix gradlew && chmod +x gradlew

RUN ./gradlew bootJar --no-daemon -x test --info

FROM amazoncorretto:21-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]