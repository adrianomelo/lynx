FROM maven:3.6-jdk-10-slim as build
WORKDIR /lynx
COPY pom.xml pom.xml
COPY src src
COPY conf conf
RUN mvn package

FROM openjdk:10-jdk-slim
WORKDIR /lynx
COPY --from=build /lynx/target/lynx-1.0.0.jar app.jar
COPY conf conf
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
