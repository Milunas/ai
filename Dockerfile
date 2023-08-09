# Use the official OpenJDK image as the base image
FROM adoptopenjdk/openjdk17:alpine

# Set the working directory in the container
WORKDIR /app

# Install Gradle
RUN apk update && apk add --no-cache wget unzip
RUN wget -q https://services.gradle.org/distributions/gradle-7.2-bin.zip && \
    unzip -q gradle-7.2-bin.zip && \
    rm gradle-7.2-bin.zip
ENV PATH="/app/gradle-7.2/bin:${PATH}"

# Copy the Spring Boot application files to the container
COPY . .

# Build the Spring Boot application with Gradle
RUN gradle clean build

# Expose the port that the Spring Boot app will run on
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "build/libs/your-spring-boot-app.jar"]
