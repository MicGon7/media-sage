FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :agent:shadowJar --no-daemon

FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl git python3 python3-pip && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends gh && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir mcp-atlassian

ENV DISABLE_AUTOUPDATER=1
ENV PYTHONUNBUFFERED=1
RUN npm install -g @anthropic-ai/claude-code

RUN useradd -m -s /bin/bash agent
USER agent
WORKDIR /home/agent

COPY --from=builder --chown=agent:agent /workspace/agent/build/libs/agent-all.jar app.jar
COPY --chown=agent:agent agent/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8081
ENTRYPOINT ["./entrypoint.sh"]
