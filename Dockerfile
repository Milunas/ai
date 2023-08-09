FROM gradle:7.2.0-jdk17 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the Spring Boot application files to the container
COPY . .

# Build the Spring Boot application with Gradle
RUN gradle clean build

# Use the official OpenJDK image as the runtime stage
FROM openjdk:17-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the built Spring Boot JAR from the build stage
COPY --from=build /app/build/libs/your-spring-boot-app.jar .

# Expose the port that the Spring Boot app will run on
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "your-spring-boot-app.jar"]
