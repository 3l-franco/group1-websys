# Build a self-contained Java application with both local SQLite and hosted PostgreSQL JDBC drivers.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/enrollease-1.0.0.jar /app/enrollease.jar
COPY web ./web
EXPOSE 10000
CMD ["java", "-jar", "/app/enrollease.jar"]
