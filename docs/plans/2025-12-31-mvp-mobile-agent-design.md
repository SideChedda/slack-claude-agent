# MVP Design: Slack-Claude Agent for Mobile Control

**Date:** 2025-12-31
**Status:** Approved
**Goal:** Enable "fire and forget" task dispatch from phone via Slack

---

## Core Flow

```
You (phone) → Slack message → Agent (Railway) → Claude Code CLI → Git → PR
     ↑                                                                  │
     └──────────── Slack thread updates ←───────────────────────────────┘
```

**Key interactions:**
1. Send task in project channel → Agent creates thread named after project
2. Agent works, posts progress updates in thread
3. If stuck → asks question in thread, waits for your reply
4. On completion → Summary + diff + test results + PR link in thread
5. On failure → asks what to do (configurable per project)

**Commands:**
- `/agent-task <description>` - Start a task (optional: `--model opus` or `--model haiku`)
- `/agent-stop` - Cancel current task
- `/agent-status` - What's the agent doing?

---

## Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Notifications | Slack threads by project | Clean, easy to find on mobile |
| When stuck | Ask in thread, wait | Stay in control |
| Hosting | Railway (~$12/month) | Simplicity over cost |
| Project context | One channel = one repo | No ambiguity |
| Default model | Sonnet | Best cost/quality balance |
| Cost control | Soft warnings at 80% | Learn usage first, tune later |
| Cancellation | `/agent-stop` | Simple slash command |
| Security | Anyone in channel | Trust channel membership |
| Concurrent tasks | Ask user | Flexible in the moment |
| Branch naming | `agent/<channel>/<task-id>` | Predictable, traceable |
| PR target | Always `main` | Simple, manual merge |
| Channel setup | YAML config files | Explicit, no UI needed |
| On failure | Configurable per project | Different projects, different needs |

---

## Project Configuration

**Channel config files** (`config/channels/<channel-id>.yaml`):

```yaml
# config/channels/C0123ABCDEF.yaml
channel_id: C0123ABCDEF
channel_name: slack-claude-agent  # For thread naming
repo: git@github.com:user/slack-claude-agent.git
clone_path: /app/workspaces/slack-claude-agent

# Git settings
pr_target: main
branch_prefix: agent/slack-claude-agent

# Behavior
default_model: sonnet
on_failure: ask        # ask | stop | retry | draft_pr
on_concurrent: ask     # ask | queue | parallel | reject

# Optional setup commands (run before each task)
setup_commands:
  - ./gradlew build --quiet

# Optional profile override
profile: api-specialist  # or leave blank for default
```

---

## Cost Tracking

**Per-task tracking:**
- Count tokens from Claude Code CLI output
- Calculate cost based on model used
- Store in append-only log (`data/costs.jsonl`)

**Budget:**
- Monthly budget: $500 (configurable)
- Warning threshold: 80%
- No hard stop - just warnings

**Task completion display:**
```
✅ Task complete: Add user authentication

📊 Cost: $2.34 (Sonnet, 47K tokens)
📈 Monthly: $89.50 / $500 (18%)

📝 Summary: Added JWT-based auth with login/logout endpoints...
📁 Changed: 4 files (+234 / -12)
✅ Tests: 23 passed, 0 failed
🔗 PR: github.com/user/repo/pull/42
```

---

## Thread Interaction

**Thread lifecycle:**
```
┌─────────────────────────────────────────────────────────────┐
│ 🧵 Thread: slack-claude-agent                               │
├─────────────────────────────────────────────────────────────┤
│ You: Add rate limiting to the API endpoints                 │
│                                                             │
│ Agent: 🚀 Starting task...                                  │
│        Model: Sonnet | Estimated: 5-15 min                  │
│                                                             │
│ Agent: 📋 Plan:                                             │
│        1. Add rate limit dependency                         │
│        2. Create RateLimitFilter                            │
│        3. Configure limits per endpoint                     │
│        4. Add tests                                         │
│        5. Run full test suite                               │
│                                                             │
│ Agent: ⏳ Working on step 1/5...                            │
│                                                             │
│ Agent: ❓ Question:                                         │
│        What rate limit do you want?                         │
│        • 100 req/min per user (typical)                     │
│        • 1000 req/min per user (high traffic)               │
│        • Custom - tell me the numbers                       │
│                                                             │
│ You: 100 per minute is fine                                 │
│                                                             │
│ Agent: 👍 Got it. Continuing...                             │
│                                                             │
│ Agent: ✅ Complete!                                         │
│        [full summary with cost, diff, tests, PR link]       │
└─────────────────────────────────────────────────────────────┘
```

**Concurrent task prompt:**
```
Agent: ⚠️ I'm currently working on "Add rate limiting"

       What should I do with "Fix login bug"?
       • Queue - run after current task
       • Parallel - run alongside (may conflict)
       • Cancel current - stop rate limiting, start this
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Railway                                  │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │ Spring Boot App │───▶│  Claude Code    │                     │
│  │                 │    │  CLI (subprocess)│                    │
│  │ • SlackController    └────────┬────────┘                     │
│  │ • TaskExecutor               │                               │
│  │ • CostTracker                ▼                               │
│  │ • ConfigLoader        ┌─────────────┐                        │
│  └────────┬──────────    │ Git (clone) │                        │
│           │              │ /app/workspaces/                     │
│           │              └──────┬──────┘                        │
│           │                     │                               │
├───────────┼─────────────────────┼───────────────────────────────┤
│           ▼                     ▼                               │
│    ┌─────────────┐       ┌─────────────┐                        │
│    │ Slack API   │       │ GitHub API  │                        │
│    │ (webhooks)  │       │ (PRs)       │                        │
│    └─────────────┘       └─────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

**Components:**

| Component | Status | Work Needed |
|-----------|--------|-------------|
| SlackController | Exists | Add thread management |
| ClaudeAgentService | Exists | Fix model selection, add progress callbacks |
| TaskExecutor | New | Queue management, cancellation |
| ThreadManager | New | Create/update Slack threads |
| CostTracker | New | Token counting, budget alerts |
| ConfigLoader | New | YAML channel configs |
| GitService | New | Branch creation, PR creation via `gh` CLI |

---

## Deployment

**Railway container:**

```dockerfile
FROM eclipse-temurin:17-jdk

# Install Claude Code CLI
RUN npm install -g @anthropic-ai/claude-code

# Install GitHub CLI
RUN apt-get update && apt-get install -y gh git

# App
COPY build/libs/slack-claude-agent.jar /app/app.jar
COPY config/ /app/config/

WORKDIR /app
CMD ["java", "-jar", "app.jar"]
```

**Environment variables:**

| Variable | Purpose |
|----------|---------|
| `SLACK_BOT_TOKEN` | Slack bot OAuth token |
| `SLACK_SIGNING_SECRET` | Verify Slack requests |
| `ANTHROPIC_API_KEY` | Claude API access |
| `GITHUB_TOKEN` | Push branches, create PRs |
| `MONTHLY_BUDGET_USD` | Budget limit (default: 500) |

**Persistent storage (Railway volumes):**
- `/app/workspaces/` - Git clones
- `/app/data/` - Cost logs, task history
- `/app/config/` - Channel configs (baked into container)

---

## Scope

### IN for MVP

| Feature | Why |
|---------|-----|
| Slack thread per task | Core UX for mobile |
| Model selection (`--model`) | Cost control |
| Cost tracking + 80% warning | Stay under $500 |
| Ask on stuck/failure | Stay in control |
| Branch naming convention | Traceability |
| PR creation with summary | The output you want |
| `/agent-stop` cancellation | Escape hatch |
| Concurrent task handling (ask) | Multiple ideas workflow |
| YAML channel config | Multi-project support |

### OUT for MVP (YAGNI)

| Feature | Why Not Now |
|---------|-------------|
| User authentication/roles | Trust channel membership |
| Database persistence | JSONL files fine for now |
| Web dashboard | Slack is your dashboard |
| Hot-reload configs | Restart is fine |
| Retry logic | Ask user instead |
| Conversation memory | Each task is independent |
| Self-improvement/learning | Get basics working first |
| Multiple concurrent agents | One per channel is enough |
