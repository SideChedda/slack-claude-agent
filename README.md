# Slack Claude Agent

An autonomous AI agent for Slack powered by Claude with self-improvement capabilities.

## Setup Instructions

### Prerequisites
- Java 17+
- Gradle 7.6+
- Claude Code CLI installed locally
- Slack Workspace (with admin access)

### 1. Slack App Setup

1. Go to [api.slack.com/apps](https://api.slack.com/apps) and click "Create New App"
2. Choose "From scratch" and name your app "Claude Agent"
3. Select your workspace

#### Configure OAuth & Permissions:
Navigate to "OAuth & Permissions" and add these Bot Token Scopes:
- `app_mentions:read` - Read messages that mention your bot
- `channels:history` - View messages in public channels
- `channels:read` - View basic channel info
- `chat:write` - Send messages
- `commands` - Add slash commands
- `groups:history` - View messages in private channels
- `groups:read` - View basic private channel info
- `im:history` - View direct messages
- `im:read` - View basic DM info
- `im:write` - Send direct messages
- `users:read` - View users

Install the app to your workspace and copy the **Bot User OAuth Token** (starts with `xoxb-`)

#### Configure Event Subscriptions:
1. Go to "Event Subscriptions" and enable events
2. Set Request URL: `https://YOUR_DOMAIN/slack/events`
3. Subscribe to bot events:
   - `app_mention`
   - `message.channels`
   - `message.groups`
   - `message.im`

#### Configure Slash Commands:
Add these commands in "Slash Commands":
- `/agent-start [profile]` - Start an agent with specified profile
- `/agent-stop` - Stop the current agent
- `/agent-status` - Check agent status
- `/agent-task [description]` - Assign a task to the agent

For each command, set the Request URL to: `https://YOUR_DOMAIN/slack/slash-commands`

### 2. Claude Code CLI Setup

1. Install Claude Code CLI:
```bash
# Install via npm (if available)
npm install -g @anthropic/claude-code

# Or download from Anthropic
# Follow instructions at anthropic.com/claude-code
```

2. Authenticate Claude Code:
```bash
claude auth login
```

### 3. Application Configuration

Update `src/main/resources/application.properties`:

```properties
# Slack Credentials
slack.bot.token=xoxb-YOUR-BOT-TOKEN
slack.signing.secret=YOUR-SIGNING-SECRET

# Claude Code CLI Configuration
claude.code.path=/usr/local/bin/claude
claude.code.workspace=/tmp/claude-workspace
```

### 4. Build and Run

```bash
# Install dependencies and build
./gradlew build

# Run the application
./gradlew bootRun

# Or run with Java
java -jar build/libs/slack-claude-agent-1.0.0.jar
```

### 5. Deploy Options

#### Local Development with ngrok:
```bash
# Install ngrok
brew install ngrok  # macOS
# or download from ngrok.com

# Run your app on port 8080
./gradlew bootRun

# Expose your local server
ngrok http 8080

# Use the ngrok URL in Slack app configuration
```

#### Production Deployment:

**Option 1: Heroku**
```bash
# Create Heroku app
heroku create your-slack-agent

# Set environment variables
heroku config:set SLACK_BOT_TOKEN=xoxb-your-token
heroku config:set CLAUDE_CODE_PATH=/app/bin/claude

# Deploy
git push heroku main
```

**Option 2: Docker**
```bash
# Build Docker image
docker build -t slack-claude-agent .

# Run container
docker run -p 8080:8080 \
  -e SLACK_BOT_TOKEN=xoxb-your-token \
  -e CLAUDE_CODE_PATH=/usr/local/bin/claude \
  -v ~/.claude:/root/.claude \
  slack-claude-agent
```

## Usage

### Starting an Agent

In any Slack channel:
```
/agent-start default
```

Or with a specific profile:
```
/agent-start api-specialist
```

### Interacting with the Agent

**Via mention:**
```
@ClaudeAgent help me write a Python script
```

**Via slash command:**
```
/agent-task Create a REST API endpoint for user management
```

### Agent Profiles

Profiles are defined in `agent-profiles/` directory:
- `default.yaml` - General purpose assistant
- `api-specialist.yaml` - Specialized in API development

Create custom profiles by adding new YAML files.

## Architecture

```
slack-claude-agent/
├── src/main/java/com/autonomous/agent/
│   ├── controller/
│   │   └── SlackController.java       # Slack webhook handler
│   ├── service/
│   │   ├── ClaudeAgentService.java    # Claude API integration
│   │   ├── SlackService.java          # Slack messaging
│   │   ├── ProfileService.java        # Agent profile management
│   │   └── SelfImprovementService.java # Performance optimization
│   └── model/
│       ├── AgentInstance.java         # Agent runtime state
│       ├── AgentProfile.java          # Agent configuration
│       └── Task.java                   # Task tracking
└── agent-profiles/                     # Agent configurations
```

## Self-Improvement Features

The agent automatically:
- Tracks task completion metrics
- Adjusts temperature based on success rate
- Optimizes token usage
- Learns from interaction patterns
- Updates its approach based on feedback

## Security Considerations

1. **Never commit credentials** - Use environment variables
2. **Verify Slack requests** - The app validates signing secrets
3. **Rate limiting** - Implement rate limits for production
4. **Access control** - Restrict who can start/stop agents
5. **Audit logging** - Track all agent actions

## Monitoring

Check agent logs:
```bash
tail -f logs/slack-claude-agent.log
```

View metrics:
```
/agent-status
```

## Troubleshooting

### Bot not responding:
1. Check Slack app is installed in workspace
2. Verify bot has been invited to channel: `/invite @ClaudeAgent`
3. Check Event Subscriptions URL is verified
4. Review application logs for errors

### Claude Code errors:
1. Verify Claude Code CLI is installed and in PATH
2. Check authentication: `claude auth status`
3. Ensure workspace directory has write permissions

### Connection issues:
1. For local dev, ensure ngrok is running
2. Check firewall/security group settings
3. Verify SSL certificates for production

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

MIT License - See LICENSE file for details

## Support

- GitHub Issues: [github.com/SideChedda/slack-claude-agent/issues](https://github.com/SideChedda/slack-claude-agent/issues)
- Documentation: [github.com/SideChedda/slack-claude-agent/wiki](https://github.com/SideChedda/slack-claude-agent/wiki)