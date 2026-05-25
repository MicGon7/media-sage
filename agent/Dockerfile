FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :agent:shadowJar --no-daemon --settings-file settings-agent.gradle.kts

FROM eclipse-temurin:21-jdk-jammy

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl git python3 python3-pip && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends gh && \
    pip3 install --no-cache-dir mcp-atlassian==0.21.1 cryptography && \
    npm install -g @anthropic-ai/claude-code@2.1.139 && \
    rm -rf /var/lib/apt/lists/*

ENV DISABLE_AUTOUPDATER=1
ENV PYTHONUNBUFFERED=1

RUN useradd -m -s /bin/bash agent
USER agent
WORKDIR /home/agent

COPY --from=builder --chown=agent:agent /workspace/agent/build/libs/agent-all.jar app.jar
COPY --chown=agent:agent agent/entrypoint.sh entrypoint.sh
COPY --chown=agent:agent agent/get-github-token.py get-github-token.py
RUN chmod +x entrypoint.sh

EXPOSE 8081
ENTRYPOINT ["./entrypoint.sh"]
