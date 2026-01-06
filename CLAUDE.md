# Slack Claude Agent - Project Status

## Overview
A Slack-integrated Claude Code orchestrator that lets you dispatch coding tasks from your phone via Slack commands.

## Current Deployment Status

**Railway URL:** `https://slack-claude-agent-production.up.railway.app`

### Completed Setup
- [x] Slack App created and configured
- [x] OAuth scopes: chat:write, commands, app_mentions:read, channels:history, channels:read
- [x] Slash commands: /agent-task, /agent-stop, /agent-status
- [x] Railway deployment working
- [x] Channel configured: `C0A5VC84DB9` (kehe-metrics-agent)
- [x] Git cloning working
- [x] Claude Code CLI installed and running (v2.0.76)

### Current State - TESTING IN PROGRESS
Claude Code is being invoked with:
```
claude -p --max-turns 10 <task description>
```

Last test showed:
- Repo cloned successfully
- Branch created
- Claude Code started running

**Waiting to see:** `[claude]` output lines in Railway logs showing Claude working

### Environment Variables Required (Railway)
- `SLACK_BOT_TOKEN` - Slack bot token (xoxb-...)
- `SLACK_SIGNING_SECRET` - Slack signing secret
- `ANTHROPIC_API_KEY` - Your Anthropic API key
- `GITHUB_TOKEN` - GitHub PAT with repo scope

### Test Command
```
/agent-task Add a README.md with the project description
```

### Check Railway Logs For
1. `executeTask started for: ...`
2. `Cloning repo: ...`
3. `Repo cloned successfully`
4. `Branch created`
5. `Calling Claude Code...`
6. `[claude] ...` (output from Claude)
7. `Claude Code completed, result length: X`
8. PR creation

### Known Issues Fixed
- Event handler loop causing rate limits (fixed)
- Git not cloning repo before branch creation (fixed)
- `--dangerously-skip-permissions` not allowed as root (removed)
- Test command defaulting to gradle (now auto-detects project type)

### Files Structure
```
config/channels/kehe-metrics.yaml  - Channel configuration
docker-entrypoint.sh               - Git/GH auth setup at runtime
Dockerfile                         - Multi-stage build with Claude CLI
```

### Next Steps If Task Succeeds
1. Check GitHub repo for new PR
2. Verify README was created
3. Check Slack thread for completion message

### Next Steps If Task Fails
1. Check Railway logs for error
2. May need to debug Claude Code invocation
3. Check if permissions are blocking file writes
