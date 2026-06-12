FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :analyst:shadowJar --no-daemon --settings-file settings-analyst.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

RUN useradd -m -s /bin/bash analyst
USER analyst
WORKDIR /home/analyst

COPY --from=builder --chown=analyst:analyst /workspace/analyst/build/libs/analyst-all.jar app.jar
COPY --chown=analyst:analyst analyst/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8082
ENTRYPOINT ["./entrypoint.sh"]
