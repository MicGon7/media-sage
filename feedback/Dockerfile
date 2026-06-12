FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :feedback:shadowJar --no-daemon --settings-file settings-feedback.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

RUN useradd -m -s /bin/bash analyst
USER analyst
WORKDIR /home/analyst

COPY --from=builder --chown=analyst:analyst /workspace/feedback/build/libs/feedback-all.jar app.jar
COPY --chown=analyst:analyst feedback/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8082
ENTRYPOINT ["./entrypoint.sh"]
