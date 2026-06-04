FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :agent:shadowJar --no-daemon --settings-file settings-agent.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

RUN useradd -m -s /bin/bash agent
USER agent
WORKDIR /home/agent

COPY --from=builder --chown=agent:agent /workspace/agent/build/libs/agent-all.jar app.jar
COPY --chown=agent:agent agent/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8081
ENTRYPOINT ["./entrypoint.sh"]
