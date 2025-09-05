# Build
FROM gradle:8.9-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle --no-daemon clean test shadowJar

# Run (use JDK so JDI is available)
FROM eclipse-temurin:17
WORKDIR /app
COPY --from=build /app/build/libs/java-debugging-system-all.jar /app/app.jar
COPY config/breakpoints.json /app/config/breakpoints.json
ENTRYPOINT ["java","-jar","/app/app.jar","--launch","com.example.sample.ExampleApp","--bp","/app/config/breakpoints.json"]
