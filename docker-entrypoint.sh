#!/bin/bash

# Configure git credentials if GITHUB_TOKEN is set
if [ -n "$GITHUB_TOKEN" ]; then
    echo "https://x-access-token:${GITHUB_TOKEN}@github.com" > ~/.git-credentials
    git config --global credential.helper store
fi

# Configure gh CLI if GITHUB_TOKEN is set
if [ -n "$GITHUB_TOKEN" ]; then
    echo "$GITHUB_TOKEN" | gh auth login --with-token 2>/dev/null || true
fi

# Verify Claude CLI is available
echo "Checking Claude CLI..."
if which claude > /dev/null 2>&1; then
    echo "Claude CLI found at: $(which claude)"
    claude --version 2>&1 || echo "Could not get version"
else
    echo "WARNING: Claude CLI not found in PATH"
    echo "PATH is: $PATH"
fi

# Check required env vars
echo "ANTHROPIC_API_KEY set: $([ -n \"$ANTHROPIC_API_KEY\" ] && echo 'yes' || echo 'NO')"
echo "GITHUB_TOKEN set: $([ -n \"$GITHUB_TOKEN\" ] && echo 'yes' || echo 'NO')"

# Start the application
exec java -jar /app/app.jar
