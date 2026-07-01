FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :agentruntime:shadowJar --no-daemon --settings-file settings-agentruntime.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

RUN useradd -m -s /bin/bash agent
USER agent
WORKDIR /home/agent

COPY --from=builder --chown=agent:agent /workspace/agentruntime/build/libs/agentruntime-all.jar app.jar
COPY --chown=agent:agent agentruntime/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8081
ENTRYPOINT ["./entrypoint.sh"]
