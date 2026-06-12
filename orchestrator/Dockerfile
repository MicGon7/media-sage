FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :orchestrator:shadowJar --no-daemon --settings-file settings-orchestrator.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

RUN useradd -m -s /bin/bash agent
USER agent
WORKDIR /home/agent

COPY --from=builder --chown=agent:agent /workspace/orchestrator/build/libs/orchestrator-all.jar app.jar
COPY --chown=agent:agent orchestrator/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8081
ENTRYPOINT ["./entrypoint.sh"]
