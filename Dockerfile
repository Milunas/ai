FROM eclipse-temurin:17-jdk-alpine
COPY build/libs/ai-0.1-all.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE 8080