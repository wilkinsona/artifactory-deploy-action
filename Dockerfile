FROM ghcr.io/bell-sw/liberica-openjdk-debian:21.0.2-14 AS build
COPY gradle /app/gradle/
COPY src /app/src/
COPY gradlew build.gradle settings.gradle /app/
RUN cd /app && ./gradlew bootJar

FROM ghcr.io/bell-sw/liberica-openjdk-debian:21.0.2-14
COPY --from=build /app/build/libs/artifactory-github-action-0.0.1-SNAPSHOT.jar /opt/action/artifactory.jar
ENTRYPOINT ["java", "-jar", "/opt/action/artifactory.jar"]
