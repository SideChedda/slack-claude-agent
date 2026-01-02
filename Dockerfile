FROM eclipse-temurin:17-jdk as builder

WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

# Install Node.js for Claude Code CLI
RUN apt-get update && apt-get install -y curl gnupg git && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g @anthropic-ai/claude-code && \
    apt-get clean && rm -rf /var/lib/apt/lists/* && \
    which claude && claude --version || echo "Claude CLI installation may have failed"

# Install GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg && \
    chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null && \
    apt-get update && apt-get install -y gh && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy built JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Copy config
COPY config/ /app/config/

# Create data directory
RUN mkdir -p /app/data /app/workspaces

# Configure git for private repos (uses GITHUB_TOKEN at runtime)
RUN git config --global credential.helper store && \
    git config --global user.email "agent@slack-claude.app" && \
    git config --global user.name "Slack Claude Agent"

EXPOSE 8080

# Entrypoint script to configure git credentials at runtime
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
