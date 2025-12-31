# MVP Mobile Agent Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform the existing prototype into a mobile-first "fire and forget" Slack agent that creates PRs.

**Architecture:** Channel-based configuration with async task execution. Each Slack channel maps to a repo via YAML config. Tasks run in threads with progress updates, questions, and final PR links.

**Tech Stack:** Spring Boot 3.2, Slack Bolt SDK, Claude Code CLI, GitHub CLI, Jackson YAML

---

## Task 1: Channel Configuration Model

**Files:**
- Create: `src/main/java/com/autonomous/agent/model/ChannelConfig.java`
- Test: `src/test/java/com/autonomous/agent/model/ChannelConfigTest.java`

**Step 1: Write the failing test**

```java
package com.autonomous.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelConfigTest {

    @Test
    void shouldHaveRequiredFields() {
        ChannelConfig config = new ChannelConfig();
        config.setChannelId("C0123ABCDEF");
        config.setChannelName("slack-agent");
        config.setRepo("git@github.com:user/repo.git");
        config.setClonePath("/app/workspaces/repo");

        assertEquals("C0123ABCDEF", config.getChannelId());
        assertEquals("slack-agent", config.getChannelName());
        assertEquals("git@github.com:user/repo.git", config.getRepo());
        assertEquals("/app/workspaces/repo", config.getClonePath());
    }

    @Test
    void shouldHaveDefaultValues() {
        ChannelConfig config = new ChannelConfig();

        assertEquals("main", config.getPrTarget());
        assertEquals("sonnet", config.getDefaultModel());
        assertEquals("ask", config.getOnFailure());
        assertEquals("ask", config.getOnConcurrent());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ChannelConfigTest -i`
Expected: FAIL with "cannot find symbol: class ChannelConfig"

**Step 3: Write minimal implementation**

```java
package com.autonomous.agent.model;

import lombok.Data;
import java.util.List;

@Data
public class ChannelConfig {
    private String channelId;
    private String channelName;
    private String repo;
    private String clonePath;

    // Git settings
    private String prTarget = "main";
    private String branchPrefix;

    // Behavior
    private String defaultModel = "sonnet";
    private String onFailure = "ask";      // ask | stop | retry | draft_pr
    private String onConcurrent = "ask";   // ask | queue | parallel | reject

    // Optional
    private List<String> setupCommands;
    private String profile;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ChannelConfigTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/autonomous/agent/model/ChannelConfig.java src/test/java/com/autonomous/agent/model/ChannelConfigTest.java
git commit -m "feat: add ChannelConfig model for per-channel project settings"
```

---

## Task 2: ConfigLoader Service

**Files:**
- Create: `src/main/java/com/autonomous/agent/service/ConfigLoaderService.java`
- Create: `src/test/java/com/autonomous/agent/service/ConfigLoaderServiceTest.java`
- Create: `config/channels/.gitkeep`

**Step 1: Write the failing test**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderServiceTest {

    private ConfigLoaderService configLoader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configLoader = new ConfigLoaderService();
        configLoader.setConfigPath(tempDir.toString());
    }

    @Test
    void shouldLoadConfigFromYaml() throws Exception {
        // Create test YAML file
        File configFile = tempDir.resolve("C0123ABC.yaml").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("channel_id: C0123ABC\n");
            writer.write("channel_name: test-project\n");
            writer.write("repo: git@github.com:user/test.git\n");
            writer.write("clone_path: /app/workspaces/test\n");
            writer.write("default_model: opus\n");
        }

        configLoader.loadConfigs();
        Optional<ChannelConfig> config = configLoader.getConfigForChannel("C0123ABC");

        assertTrue(config.isPresent());
        assertEquals("test-project", config.get().getChannelName());
        assertEquals("opus", config.get().getDefaultModel());
    }

    @Test
    void shouldReturnEmptyForUnknownChannel() {
        configLoader.loadConfigs();
        Optional<ChannelConfig> config = configLoader.getConfigForChannel("UNKNOWN");

        assertFalse(config.isPresent());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ConfigLoaderServiceTest -i`
Expected: FAIL with "cannot find symbol: class ConfigLoaderService"

**Step 3: Write minimal implementation**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfigLoaderService {

    @Value("${agent.config.path:config/channels}")
    private String configPath;

    private final Map<String, ChannelConfig> configs = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;

    public ConfigLoaderService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public void setConfigPath(String path) {
        this.configPath = path;
    }

    @PostConstruct
    public void loadConfigs() {
        configs.clear();
        File configDir = new File(configPath);

        if (!configDir.exists() || !configDir.isDirectory()) {
            System.out.println("Config directory not found: " + configPath);
            return;
        }

        File[] yamlFiles = configDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (yamlFiles == null) return;

        for (File file : yamlFiles) {
            try {
                ChannelConfig config = yamlMapper.readValue(file, ChannelConfig.class);
                if (config.getChannelId() != null) {
                    configs.put(config.getChannelId(), config);
                    System.out.println("Loaded config for channel: " + config.getChannelId());
                }
            } catch (Exception e) {
                System.err.println("Failed to load config from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public Optional<ChannelConfig> getConfigForChannel(String channelId) {
        return Optional.ofNullable(configs.get(channelId));
    }

    public Map<String, ChannelConfig> getAllConfigs() {
        return Map.copyOf(configs);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ConfigLoaderServiceTest -i`
Expected: PASS

**Step 5: Create config directory and commit**

```bash
mkdir -p config/channels
touch config/channels/.gitkeep
git add src/main/java/com/autonomous/agent/service/ConfigLoaderService.java src/test/java/com/autonomous/agent/service/ConfigLoaderServiceTest.java config/channels/.gitkeep
git commit -m "feat: add ConfigLoaderService for YAML channel configs"
```

---

## Task 3: Thread Manager Service

**Files:**
- Create: `src/main/java/com/autonomous/agent/service/ThreadManagerService.java`
- Create: `src/test/java/com/autonomous/agent/service/ThreadManagerServiceTest.java`

**Step 1: Write the failing test**

```java
package com.autonomous.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadManagerServiceTest {

    @Mock
    private SlackService slackService;

    @InjectMocks
    private ThreadManagerService threadManager;

    @Test
    void shouldCreateThreadAndReturnTimestamp() {
        when(slackService.postMessage(eq("C123"), anyString())).thenReturn("1234567890.123456");

        String threadTs = threadManager.createThread("C123", "test-project", "Add feature X");

        assertNotNull(threadTs);
        assertEquals("1234567890.123456", threadTs);
    }

    @Test
    void shouldPostUpdateToThread() {
        threadManager.postUpdate("C123", "1234567890.123456", "Working on step 1...");

        verify(slackService).postMessageInThread(eq("C123"), eq("1234567890.123456"), contains("Working on step 1"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ThreadManagerServiceTest -i`
Expected: FAIL with "cannot find symbol: class ThreadManagerService"

**Step 3: Update SlackService to support threads**

First modify `SlackService.java` to add thread support:

```java
package com.autonomous.agent.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlackService {

    @Value("${slack.bot.token:}")
    private String slackBotToken;

    private final Slack slack = Slack.getInstance();

    public void processEvent(Map<String, Object> payload) {
        Map<String, Object> event = (Map<String, Object>) payload.get("event");

        if (event != null) {
            String type = (String) event.get("type");

            if ("app_mention".equals(type) || "message".equals(type)) {
                String text = (String) event.get("text");
                String channel = (String) event.get("channel");
                String user = (String) event.get("user");
                String threadTs = (String) event.get("thread_ts");

                handleMessage(text, channel, user, threadTs);
            }
        }
    }

    private void handleMessage(String text, String channel, String user, String threadTs) {
        // Will be integrated with ClaudeAgentService
    }

    public String postMessage(String channel, String message) {
        try {
            MethodsClient methods = slack.methods(slackBotToken);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .text(message)
                .build();

            ChatPostMessageResponse response = methods.chatPostMessage(request);

            if (response.isOk()) {
                return response.getTs();
            } else {
                System.err.println("Failed to send message: " + response.getError());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String postMessageInThread(String channel, String threadTs, String message) {
        try {
            MethodsClient methods = slack.methods(slackBotToken);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .threadTs(threadTs)
                .text(message)
                .build();

            ChatPostMessageResponse response = methods.chatPostMessage(request);

            if (response.isOk()) {
                return response.getTs();
            } else {
                System.err.println("Failed to send thread message: " + response.getError());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendMessage(String channel, String message) {
        postMessage(channel, message);
    }
}
```

**Step 4: Write ThreadManagerService**

```java
package com.autonomous.agent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThreadManagerService {

    @Autowired
    private SlackService slackService;

    // Map of taskId -> threadTs
    private final Map<String, String> taskThreads = new ConcurrentHashMap<>();

    public String createThread(String channelId, String projectName, String taskDescription) {
        String message = String.format("*%s*\n\n%s", projectName, taskDescription);
        return slackService.postMessage(channelId, message);
    }

    public void postUpdate(String channelId, String threadTs, String message) {
        slackService.postMessageInThread(channelId, threadTs, message);
    }

    public void postStarting(String channelId, String threadTs, String model) {
        String message = String.format("*Starting task...*\nModel: %s", model);
        postUpdate(channelId, threadTs, message);
    }

    public void postProgress(String channelId, String threadTs, int step, int total, String description) {
        String message = String.format("*Working on step %d/%d...*\n%s", step, total, description);
        postUpdate(channelId, threadTs, message);
    }

    public void postQuestion(String channelId, String threadTs, String question, String... options) {
        StringBuilder message = new StringBuilder();
        message.append("*Question:*\n").append(question).append("\n\n");
        for (String option : options) {
            message.append("• ").append(option).append("\n");
        }
        postUpdate(channelId, threadTs, message.toString());
    }

    public void postCompletion(String channelId, String threadTs, String summary, String cost,
                                String filesChanged, String testResults, String prUrl) {
        StringBuilder message = new StringBuilder();
        message.append("*Task complete!*\n\n");
        message.append("*Summary:* ").append(summary).append("\n\n");
        message.append("*Cost:* ").append(cost).append("\n");
        message.append("*Changed:* ").append(filesChanged).append("\n");
        message.append("*Tests:* ").append(testResults).append("\n");
        message.append("*PR:* ").append(prUrl);
        postUpdate(channelId, threadTs, message.toString());
    }

    public void postFailure(String channelId, String threadTs, String error, String onFailure) {
        StringBuilder message = new StringBuilder();
        message.append("*Task failed*\n\n");
        message.append("*Error:* ").append(error).append("\n\n");

        if ("ask".equals(onFailure)) {
            message.append("What should I do?\n");
            message.append("• *retry* - Try again\n");
            message.append("• *stop* - Abandon task\n");
            message.append("• *draft* - Create draft PR anyway\n");
        }
        postUpdate(channelId, threadTs, message.toString());
    }

    public void registerThread(String taskId, String threadTs) {
        taskThreads.put(taskId, threadTs);
    }

    public String getThreadForTask(String taskId) {
        return taskThreads.get(taskId);
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests ThreadManagerServiceTest -i`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/autonomous/agent/service/SlackService.java src/main/java/com/autonomous/agent/service/ThreadManagerService.java src/test/java/com/autonomous/agent/service/ThreadManagerServiceTest.java
git commit -m "feat: add ThreadManagerService for Slack thread lifecycle"
```

---

## Task 4: Cost Tracker Service

**Files:**
- Create: `src/main/java/com/autonomous/agent/service/CostTrackerService.java`
- Create: `src/main/java/com/autonomous/agent/model/CostEntry.java`
- Create: `src/test/java/com/autonomous/agent/service/CostTrackerServiceTest.java`

**Step 1: Write CostEntry model**

```java
package com.autonomous.agent.model;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;

@Data
@Builder
public class CostEntry {
    private Instant timestamp;
    private String channelId;
    private String taskId;
    private String model;
    private long inputTokens;
    private long outputTokens;
    private double costUsd;
}
```

**Step 2: Write the failing test**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.CostEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CostTrackerServiceTest {

    private CostTrackerService costTracker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        costTracker = new CostTrackerService();
        costTracker.setDataPath(tempDir.toString());
        costTracker.setMonthlyBudget(500.0);
    }

    @Test
    void shouldCalculateCostForSonnet() {
        double cost = costTracker.calculateCost("sonnet", 10000, 5000);

        // Sonnet: $3/M input, $15/M output
        // (10000 * 3 / 1_000_000) + (5000 * 15 / 1_000_000) = 0.03 + 0.075 = 0.105
        assertEquals(0.105, cost, 0.001);
    }

    @Test
    void shouldCalculateCostForOpus() {
        double cost = costTracker.calculateCost("opus", 10000, 5000);

        // Opus: $15/M input, $75/M output
        // (10000 * 15 / 1_000_000) + (5000 * 75 / 1_000_000) = 0.15 + 0.375 = 0.525
        assertEquals(0.525, cost, 0.001);
    }

    @Test
    void shouldCalculateCostForHaiku() {
        double cost = costTracker.calculateCost("haiku", 10000, 5000);

        // Haiku: $0.25/M input, $1.25/M output
        // (10000 * 0.25 / 1_000_000) + (5000 * 1.25 / 1_000_000) = 0.0025 + 0.00625 = 0.00875
        assertEquals(0.00875, cost, 0.0001);
    }

    @Test
    void shouldTrackMonthlySpend() {
        costTracker.recordCost("C123", "task1", "sonnet", 10000, 5000);
        costTracker.recordCost("C123", "task2", "sonnet", 10000, 5000);

        double monthlySpend = costTracker.getMonthlySpend();
        assertEquals(0.21, monthlySpend, 0.001);
    }

    @Test
    void shouldCalculateBudgetPercentage() {
        costTracker.recordCost("C123", "task1", "opus", 100000, 50000);

        // Cost: (100000 * 15 / 1M) + (50000 * 75 / 1M) = 1.5 + 3.75 = 5.25
        double percentage = costTracker.getBudgetPercentage();
        assertEquals(1.05, percentage, 0.01); // 5.25 / 500 * 100 = 1.05%
    }

    @Test
    void shouldAlertAt80Percent() {
        costTracker.setMonthlyBudget(10.0);
        costTracker.recordCost("C123", "task1", "opus", 100000, 50000);

        // Cost is 5.25, budget is 10, so 52.5%
        assertFalse(costTracker.isOverBudgetThreshold());

        costTracker.recordCost("C123", "task2", "opus", 100000, 50000);
        // Now 10.5, which is > 80% of 10
        assertTrue(costTracker.isOverBudgetThreshold());
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests CostTrackerServiceTest -i`
Expected: FAIL with "cannot find symbol: class CostTrackerService"

**Step 4: Write minimal implementation**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.CostEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

@Service
public class CostTrackerService {

    @Value("${agent.data.path:data}")
    private String dataPath;

    @Value("${agent.monthly.budget:500.0}")
    private double monthlyBudget;

    private final ObjectMapper mapper;
    private final List<CostEntry> currentMonthCosts = Collections.synchronizedList(new ArrayList<>());

    // Pricing per million tokens (as of late 2024)
    private static final Map<String, double[]> MODEL_PRICING = Map.of(
        "haiku", new double[]{0.25, 1.25},      // input, output
        "sonnet", new double[]{3.0, 15.0},
        "opus", new double[]{15.0, 75.0}
    );

    public CostTrackerService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public void setDataPath(String path) {
        this.dataPath = path;
    }

    public void setMonthlyBudget(double budget) {
        this.monthlyBudget = budget;
    }

    @PostConstruct
    public void init() {
        loadCurrentMonthCosts();
    }

    public double calculateCost(String model, long inputTokens, long outputTokens) {
        double[] pricing = MODEL_PRICING.getOrDefault(model.toLowerCase(), MODEL_PRICING.get("sonnet"));
        double inputCost = (inputTokens * pricing[0]) / 1_000_000.0;
        double outputCost = (outputTokens * pricing[1]) / 1_000_000.0;
        return inputCost + outputCost;
    }

    public CostEntry recordCost(String channelId, String taskId, String model, long inputTokens, long outputTokens) {
        double cost = calculateCost(model, inputTokens, outputTokens);

        CostEntry entry = CostEntry.builder()
            .timestamp(Instant.now())
            .channelId(channelId)
            .taskId(taskId)
            .model(model)
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .costUsd(cost)
            .build();

        currentMonthCosts.add(entry);
        persistEntry(entry);

        return entry;
    }

    public double getMonthlySpend() {
        return currentMonthCosts.stream()
            .mapToDouble(CostEntry::getCostUsd)
            .sum();
    }

    public double getBudgetPercentage() {
        return (getMonthlySpend() / monthlyBudget) * 100.0;
    }

    public boolean isOverBudgetThreshold() {
        return getBudgetPercentage() >= 80.0;
    }

    public String formatCostSummary(CostEntry entry) {
        return String.format("$%.2f (%s, %dK tokens)",
            entry.getCostUsd(),
            entry.getModel(),
            (entry.getInputTokens() + entry.getOutputTokens()) / 1000);
    }

    public String formatBudgetStatus() {
        return String.format("$%.2f / $%.0f (%.0f%%)",
            getMonthlySpend(),
            monthlyBudget,
            getBudgetPercentage());
    }

    private void persistEntry(CostEntry entry) {
        try {
            Path costsFile = Paths.get(dataPath, "costs.jsonl");
            Files.createDirectories(costsFile.getParent());

            String json = mapper.writeValueAsString(entry);
            Files.writeString(costsFile, json + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to persist cost entry: " + e.getMessage());
        }
    }

    private void loadCurrentMonthCosts() {
        try {
            Path costsFile = Paths.get(dataPath, "costs.jsonl");
            if (!Files.exists(costsFile)) return;

            YearMonth currentMonth = YearMonth.now();

            Files.lines(costsFile).forEach(line -> {
                try {
                    CostEntry entry = mapper.readValue(line, CostEntry.class);
                    YearMonth entryMonth = YearMonth.from(entry.getTimestamp().atZone(java.time.ZoneOffset.UTC));
                    if (entryMonth.equals(currentMonth)) {
                        currentMonthCosts.add(entry);
                    }
                } catch (Exception e) {
                    // Skip malformed entries
                }
            });
        } catch (IOException e) {
            System.err.println("Failed to load costs: " + e.getMessage());
        }
    }
}
```

**Step 5: Add Jackson JSR310 dependency to build.gradle**

Add to dependencies in `build.gradle`:

```groovy
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests CostTrackerServiceTest -i`
Expected: PASS

**Step 7: Commit**

```bash
git add build.gradle src/main/java/com/autonomous/agent/model/CostEntry.java src/main/java/com/autonomous/agent/service/CostTrackerService.java src/test/java/com/autonomous/agent/service/CostTrackerServiceTest.java
git commit -m "feat: add CostTrackerService with model pricing and budget tracking"
```

---

## Task 5: Git Service for Branch and PR Creation

**Files:**
- Create: `src/main/java/com/autonomous/agent/service/GitService.java`
- Create: `src/test/java/com/autonomous/agent/service/GitServiceTest.java`

**Step 1: Write the failing test**

```java
package com.autonomous.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private GitService gitService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
    }

    @Test
    void shouldGenerateBranchName() {
        String branch = gitService.generateBranchName("slack-agent", "abc123");
        assertEquals("agent/slack-agent/abc123", branch);
    }

    @Test
    void shouldGenerateBranchNameWithCustomPrefix() {
        String branch = gitService.generateBranchName("my-project", "def456", "feature");
        assertEquals("feature/my-project/def456", branch);
    }

    @Test
    void shouldParseGitDiffStats() {
        String diffOutput = " 4 files changed, 234 insertions(+), 12 deletions(-)";
        String stats = gitService.parseDiffStats(diffOutput);
        assertEquals("4 files (+234 / -12)", stats);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests GitServiceTest -i`
Expected: FAIL with "cannot find symbol: class GitService"

**Step 3: Write minimal implementation**

```java
package com.autonomous.agent.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitService {

    private static final Pattern DIFF_STATS_PATTERN =
        Pattern.compile("(\\d+) files? changed(?:, (\\d+) insertions?\\(\\+\\))?(?:, (\\d+) deletions?\\(-\\))?");

    public String generateBranchName(String channelName, String taskId) {
        return generateBranchName(channelName, taskId, "agent");
    }

    public String generateBranchName(String channelName, String taskId, String prefix) {
        return String.format("%s/%s/%s", prefix, channelName, taskId);
    }

    public boolean createBranch(String repoPath, String branchName) {
        return runGitCommand(repoPath, "git", "checkout", "-b", branchName);
    }

    public boolean commitAll(String repoPath, String message) {
        runGitCommand(repoPath, "git", "add", "-A");
        return runGitCommand(repoPath, "git", "commit", "-m", message);
    }

    public boolean push(String repoPath, String branchName) {
        return runGitCommand(repoPath, "git", "push", "-u", "origin", branchName);
    }

    public String createPullRequest(String repoPath, String title, String body, String targetBranch) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "gh", "pr", "create",
                "--title", title,
                "--body", body,
                "--base", targetBranch
            );
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                // gh pr create outputs the PR URL
                return output.trim();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDiffStats(String repoPath, String baseBranch) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "diff", "--stat", baseBranch + "..HEAD"
            );
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            process.waitFor(30, TimeUnit.SECONDS);

            return parseDiffStats(output);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String parseDiffStats(String diffOutput) {
        Matcher matcher = DIFF_STATS_PATTERN.matcher(diffOutput);
        if (matcher.find()) {
            int files = Integer.parseInt(matcher.group(1));
            int insertions = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int deletions = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            return String.format("%d files (+%d / -%d)", files, insertions, deletions);
        }
        return "no changes";
    }

    public String runTests(String repoPath, String testCommand) {
        try {
            String[] cmdParts = testCommand.split("\\s+");
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                return "Tests timed out after 5 minutes";
            }

            if (process.exitValue() == 0) {
                return parseTestOutput(output);
            } else {
                return "Tests failed:\n" + output;
            }
        } catch (Exception e) {
            return "Failed to run tests: " + e.getMessage();
        }
    }

    private String parseTestOutput(String output) {
        // Try to extract test summary - this is a simplification
        // Real implementation would parse based on test framework
        if (output.contains("BUILD SUCCESSFUL") || output.contains("Tests passed")) {
            return "All tests passed";
        }
        return output.substring(0, Math.min(500, output.length()));
    }

    private boolean runGitCommand(String repoPath, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            readProcessOutput(process); // consume output
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests GitServiceTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/autonomous/agent/service/GitService.java src/test/java/com/autonomous/agent/service/GitServiceTest.java
git commit -m "feat: add GitService for branch creation and PR management"
```

---

## Task 6: Task Executor Service

**Files:**
- Create: `src/main/java/com/autonomous/agent/service/TaskExecutorService.java`
- Create: `src/main/java/com/autonomous/agent/model/TaskExecution.java`
- Create: `src/test/java/com/autonomous/agent/service/TaskExecutorServiceTest.java`

**Step 1: Write TaskExecution model**

```java
package com.autonomous.agent.model;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Data
@Builder
public class TaskExecution {
    private String taskId;
    private String channelId;
    private String description;
    private String model;
    private String threadTs;
    private String branchName;
    private Instant startedAt;
    private String status;  // PENDING, RUNNING, WAITING_RESPONSE, COMPLETED, FAILED, CANCELLED
    private Process process;
    private CompletableFuture<String> future;
}
```

**Step 2: Write the failing test**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import com.autonomous.agent.model.TaskExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskExecutorServiceTest {

    @Mock
    private ConfigLoaderService configLoader;

    @Mock
    private ThreadManagerService threadManager;

    private TaskExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = new TaskExecutorService(configLoader, threadManager);
    }

    @Test
    void shouldRejectTaskWhenChannelNotConfigured() {
        when(configLoader.getConfigForChannel("C123")).thenReturn(Optional.empty());

        String result = executor.submitTask("C123", "Do something", null);

        assertTrue(result.contains("not configured"));
    }

    @Test
    void shouldDetectConcurrentTask() {
        ChannelConfig config = new ChannelConfig();
        config.setChannelId("C123");
        config.setChannelName("test");
        config.setOnConcurrent("ask");

        when(configLoader.getConfigForChannel("C123")).thenReturn(Optional.of(config));
        when(threadManager.createThread(anyString(), anyString(), anyString())).thenReturn("thread123");

        // Submit first task
        executor.submitTask("C123", "First task", null);

        // Check if there's a running task
        assertTrue(executor.hasRunningTask("C123"));
    }

    @Test
    void shouldParseModelFromCommand() {
        assertEquals("opus", executor.parseModel("Add feature --model opus"));
        assertEquals("haiku", executor.parseModel("Fix bug --model haiku"));
        assertEquals("sonnet", executor.parseModel("Do something")); // default
    }

    @Test
    void shouldStripModelFromDescription() {
        assertEquals("Add feature", executor.stripModelFlag("Add feature --model opus"));
        assertEquals("Fix bug quickly", executor.stripModelFlag("Fix bug quickly --model haiku"));
        assertEquals("Do something", executor.stripModelFlag("Do something"));
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests TaskExecutorServiceTest -i`
Expected: FAIL with "cannot find symbol: class TaskExecutorService"

**Step 4: Write minimal implementation**

```java
package com.autonomous.agent.service;

import com.autonomous.agent.model.ChannelConfig;
import com.autonomous.agent.model.TaskExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskExecutorService {

    private static final Pattern MODEL_FLAG_PATTERN = Pattern.compile("--model\\s+(\\w+)");

    @Value("${claude.code.path:claude}")
    private String claudeCodePath;

    private final ConfigLoaderService configLoader;
    private final ThreadManagerService threadManager;

    @Autowired(required = false)
    private CostTrackerService costTracker;

    @Autowired(required = false)
    private GitService gitService;

    @Autowired(required = false)
    private SlackService slackService;

    private final Map<String, TaskExecution> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, Queue<TaskExecution>> taskQueues = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TaskExecutorService(ConfigLoaderService configLoader, ThreadManagerService threadManager) {
        this.configLoader = configLoader;
        this.threadManager = threadManager;
    }

    public String submitTask(String channelId, String command, String userId) {
        Optional<ChannelConfig> configOpt = configLoader.getConfigForChannel(channelId);

        if (configOpt.isEmpty()) {
            return "This channel is not configured. Add a config file in config/channels/";
        }

        ChannelConfig config = configOpt.get();
        String model = parseModel(command);
        if (model.equals("sonnet")) {
            model = config.getDefaultModel(); // Use channel default if not specified
        }
        String description = stripModelFlag(command);

        // Check for concurrent task
        if (hasRunningTask(channelId)) {
            TaskExecution running = runningTasks.get(channelId);
            String onConcurrent = config.getOnConcurrent();

            if ("ask".equals(onConcurrent)) {
                return String.format("I'm currently working on: *%s*\n\n" +
                    "What should I do with this new task?\n" +
                    "• Reply *queue* to run after current task\n" +
                    "• Reply *parallel* to run alongside (may conflict)\n" +
                    "• Reply *cancel* to stop current task and start this",
                    running.getDescription());
            } else if ("reject".equals(onConcurrent)) {
                return "A task is already running. Wait for it to complete.";
            } else if ("queue".equals(onConcurrent)) {
                queueTask(channelId, description, model, config);
                return "Task queued. Will run after current task completes.";
            }
            // "parallel" falls through to execute
        }

        return startTask(channelId, description, model, config);
    }

    private String startTask(String channelId, String description, String model, ChannelConfig config) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        String threadTs = threadManager.createThread(channelId, config.getChannelName(), description);

        if (threadTs == null) {
            return "Failed to create thread. Check Slack connection.";
        }

        TaskExecution execution = TaskExecution.builder()
            .taskId(taskId)
            .channelId(channelId)
            .description(description)
            .model(model)
            .threadTs(threadTs)
            .startedAt(Instant.now())
            .status("RUNNING")
            .build();

        runningTasks.put(channelId, execution);

        // Execute async
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
            executeTask(execution, config), executor);

        execution.setFuture(future);

        threadManager.postStarting(channelId, threadTs, model);

        return String.format("Starting task in thread. Model: %s", model);
    }

    private String executeTask(TaskExecution execution, ChannelConfig config) {
        try {
            String branchName = gitService.generateBranchName(
                config.getChannelName(),
                execution.getTaskId(),
                config.getBranchPrefix() != null ? config.getBranchPrefix() : "agent"
            );
            execution.setBranchName(branchName);

            // Create branch
            gitService.createBranch(config.getClonePath(), branchName);

            // Run setup commands if any
            if (config.getSetupCommands() != null) {
                for (String cmd : config.getSetupCommands()) {
                    runCommand(config.getClonePath(), cmd);
                }
            }

            // Call Claude Code
            String result = callClaudeCode(execution, config);

            // Get diff stats
            String diffStats = gitService.getDiffStats(config.getClonePath(), config.getPrTarget());

            // Run tests
            String testResults = gitService.runTests(config.getClonePath(), "./gradlew test");

            // Commit and push
            gitService.commitAll(config.getClonePath(), "feat: " + execution.getDescription());
            gitService.push(config.getClonePath(), branchName);

            // Create PR
            String prUrl = gitService.createPullRequest(
                config.getClonePath(),
                execution.getDescription(),
                "Automated PR from Slack agent\n\n" + result,
                config.getPrTarget()
            );

            // Record cost (estimate tokens from response length)
            long estimatedInputTokens = execution.getDescription().length() * 2;
            long estimatedOutputTokens = result.length();
            var costEntry = costTracker.recordCost(
                execution.getChannelId(),
                execution.getTaskId(),
                execution.getModel(),
                estimatedInputTokens,
                estimatedOutputTokens
            );

            // Post completion
            threadManager.postCompletion(
                execution.getChannelId(),
                execution.getThreadTs(),
                result.substring(0, Math.min(500, result.length())),
                costTracker.formatCostSummary(costEntry),
                diffStats,
                testResults,
                prUrl != null ? prUrl : "PR creation failed"
            );

            execution.setStatus("COMPLETED");
            return result;

        } catch (Exception e) {
            execution.setStatus("FAILED");
            threadManager.postFailure(
                execution.getChannelId(),
                execution.getThreadTs(),
                e.getMessage(),
                config.getOnFailure()
            );
            return "Task failed: " + e.getMessage();
        } finally {
            runningTasks.remove(execution.getChannelId());
            processQueue(execution.getChannelId());
        }
    }

    private String callClaudeCode(TaskExecution execution, ChannelConfig config) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(claudeCodePath);
        command.add("--print");
        command.add("--model");
        command.add(mapModelName(execution.getModel()));
        command.add(execution.getDescription());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(config.getClonePath()));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        execution.setProcess(process);

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Task timed out after 30 minutes");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Claude Code exited with error: " + output);
        }

        return output.toString();
    }

    private String mapModelName(String shortName) {
        return switch (shortName.toLowerCase()) {
            case "opus" -> "claude-3-opus-20240229";
            case "haiku" -> "claude-3-haiku-20240307";
            default -> "claude-3-5-sonnet-20241022";
        };
    }

    public boolean cancelTask(String channelId) {
        TaskExecution execution = runningTasks.get(channelId);
        if (execution == null) {
            return false;
        }

        execution.setStatus("CANCELLED");
        if (execution.getProcess() != null) {
            execution.getProcess().destroyForcibly();
        }
        if (execution.getFuture() != null) {
            execution.getFuture().cancel(true);
        }

        runningTasks.remove(channelId);
        threadManager.postUpdate(channelId, execution.getThreadTs(), "Task cancelled.");

        return true;
    }

    public boolean hasRunningTask(String channelId) {
        return runningTasks.containsKey(channelId);
    }

    public Optional<TaskExecution> getRunningTask(String channelId) {
        return Optional.ofNullable(runningTasks.get(channelId));
    }

    public String parseModel(String command) {
        Matcher matcher = MODEL_FLAG_PATTERN.matcher(command);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return "sonnet";
    }

    public String stripModelFlag(String command) {
        return MODEL_FLAG_PATTERN.matcher(command).replaceAll("").trim();
    }

    private void queueTask(String channelId, String description, String model, ChannelConfig config) {
        taskQueues.computeIfAbsent(channelId, k -> new ConcurrentLinkedQueue<>())
            .add(TaskExecution.builder()
                .taskId(UUID.randomUUID().toString().substring(0, 8))
                .channelId(channelId)
                .description(description)
                .model(model)
                .status("PENDING")
                .build());
    }

    private void processQueue(String channelId) {
        Queue<TaskExecution> queue = taskQueues.get(channelId);
        if (queue == null || queue.isEmpty()) return;

        TaskExecution next = queue.poll();
        if (next != null) {
            configLoader.getConfigForChannel(channelId)
                .ifPresent(config -> startTask(channelId, next.getDescription(), next.getModel(), config));
        }
    }

    private void runCommand(String workDir, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.directory(new File(workDir));
        Process process = pb.start();
        process.waitFor(5, TimeUnit.MINUTES);
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests TaskExecutorServiceTest -i`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/autonomous/agent/model/TaskExecution.java src/main/java/com/autonomous/agent/service/TaskExecutorService.java src/test/java/com/autonomous/agent/service/TaskExecutorServiceTest.java
git commit -m "feat: add TaskExecutorService with queue, cancellation, and async execution"
```

---

## Task 7: Update SlackController for New Flow

**Files:**
- Modify: `src/main/java/com/autonomous/agent/controller/SlackController.java`
- Create: `src/test/java/com/autonomous/agent/controller/SlackControllerTest.java`

**Step 1: Write the test**

```java
package com.autonomous.agent.controller;

import com.autonomous.agent.service.TaskExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SlackController.class)
class SlackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskExecutorService taskExecutor;

    @Test
    void shouldHandleAgentTaskCommand() throws Exception {
        when(taskExecutor.submitTask(eq("C123"), eq("Add feature X"), eq("U456")))
            .thenReturn("Starting task...");

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-task")
                .param("text", "Add feature X")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Starting task..."));
    }

    @Test
    void shouldHandleAgentStopCommand() throws Exception {
        when(taskExecutor.cancelTask("C123")).thenReturn(true);

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-stop")
                .param("text", "")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Task cancelled."));
    }

    @Test
    void shouldHandleAgentStatusCommand() throws Exception {
        when(taskExecutor.hasRunningTask("C123")).thenReturn(false);

        mockMvc.perform(post("/slack/slash-commands")
                .param("command", "/agent-status")
                .param("text", "")
                .param("user_id", "U456")
                .param("channel_id", "C123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("No task running in this channel."));
    }
}
```

**Step 2: Update SlackController**

```java
package com.autonomous.agent.controller;

import com.autonomous.agent.model.TaskExecution;
import com.autonomous.agent.service.CostTrackerService;
import com.autonomous.agent.service.SlackService;
import com.autonomous.agent.service.TaskExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/slack")
public class SlackController {

    @Autowired
    private SlackService slackService;

    @Autowired
    private TaskExecutorService taskExecutor;

    @Autowired
    private CostTrackerService costTracker;

    @PostMapping("/events")
    public ResponseEntity<?> handleSlackEvent(@RequestBody Map<String, Object> payload) {
        // Handle URL verification challenge
        if (payload.containsKey("challenge")) {
            return ResponseEntity.ok(Map.of("challenge", payload.get("challenge")));
        }

        // Process Slack events (thread replies, etc.)
        slackService.processEvent(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/slash-commands")
    public ResponseEntity<?> handleSlashCommand(@RequestParam Map<String, String> params) {
        String command = params.get("command");
        String text = params.getOrDefault("text", "");
        String userId = params.get("user_id");
        String channelId = params.get("channel_id");

        String response = switch (command) {
            case "/agent-task" -> taskExecutor.submitTask(channelId, text, userId);
            case "/agent-stop" -> handleStop(channelId);
            case "/agent-status" -> handleStatus(channelId);
            case "/agent-budget" -> handleBudget();
            default -> "Unknown command: " + command;
        };

        return ResponseEntity.ok(Map.of(
            "response_type", "in_channel",
            "text", response
        ));
    }

    private String handleStop(String channelId) {
        boolean cancelled = taskExecutor.cancelTask(channelId);
        return cancelled ? "Task cancelled." : "No task running to cancel.";
    }

    private String handleStatus(String channelId) {
        return taskExecutor.getRunningTask(channelId)
            .map(task -> String.format("Running: *%s*\nModel: %s\nStarted: %s",
                task.getDescription(),
                task.getModel(),
                task.getStartedAt()))
            .orElse("No task running in this channel.");
    }

    private String handleBudget() {
        return String.format("Monthly budget: %s\n%s",
            costTracker.formatBudgetStatus(),
            costTracker.isOverBudgetThreshold() ? "Warning: Over 80% of budget used!" : "");
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}
```

**Step 3: Run tests**

Run: `./gradlew test --tests SlackControllerTest -i`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/autonomous/agent/controller/SlackController.java src/test/java/com/autonomous/agent/controller/SlackControllerTest.java
git commit -m "refactor: update SlackController to use new TaskExecutorService"
```

---

## Task 8: Application Properties Update

**Files:**
- Modify: `src/main/resources/application.properties`

**Step 1: Update properties**

```properties
server.port=8080

# Slack Configuration
slack.bot.token=${SLACK_BOT_TOKEN:}
slack.signing.secret=${SLACK_SIGNING_SECRET:}

# Claude Code CLI Configuration
claude.code.path=${CLAUDE_CODE_PATH:claude}

# Agent Configuration
agent.config.path=${AGENT_CONFIG_PATH:config/channels}
agent.data.path=${AGENT_DATA_PATH:data}
agent.monthly.budget=${MONTHLY_BUDGET_USD:500.0}

# Logging
logging.level.com.autonomous.agent=DEBUG
```

**Step 2: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "config: update application.properties for new services"
```

---

## Task 9: Dockerfile for Railway

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

**Step 1: Create Dockerfile**

```dockerfile
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
    apt-get clean && rm -rf /var/lib/apt/lists/*

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

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: Create .dockerignore**

```
.git
.gradle
build
*.md
.idea
*.iml
```

**Step 3: Commit**

```bash
git add Dockerfile .dockerignore
git commit -m "build: add Dockerfile for Railway deployment"
```

---

## Task 10: Example Channel Config

**Files:**
- Create: `config/channels/example.yaml.template`

**Step 1: Create template**

```yaml
# Example channel configuration
# Copy this file and rename to your-channel-id.yaml

channel_id: C0123456789          # Your Slack channel ID
channel_name: my-project         # Human-readable name (used in threads)
repo: git@github.com:user/repo.git
clone_path: /app/workspaces/my-project

# Git settings
pr_target: main
branch_prefix: agent/my-project

# Behavior
default_model: sonnet           # sonnet | opus | haiku
on_failure: ask                 # ask | stop | retry | draft_pr
on_concurrent: ask              # ask | queue | parallel | reject

# Optional: setup commands run before each task
# setup_commands:
#   - npm install
#   - npm run build

# Optional: use a specific agent profile
# profile: api-specialist
```

**Step 2: Commit**

```bash
git add config/channels/example.yaml.template
git commit -m "docs: add example channel config template"
```

---

## Task 11: Integration Test

**Files:**
- Create: `src/test/java/com/autonomous/agent/integration/EndToEndFlowTest.java`

**Step 1: Write integration test**

```java
package com.autonomous.agent.integration;

import com.autonomous.agent.model.ChannelConfig;
import com.autonomous.agent.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EndToEndFlowTest {

    @Autowired
    private TaskExecutorService taskExecutor;

    @MockBean
    private SlackService slackService;

    @MockBean
    private ConfigLoaderService configLoader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(slackService.postMessage(anyString(), anyString())).thenReturn("thread123");
        when(slackService.postMessageInThread(anyString(), anyString(), anyString())).thenReturn("msg123");
    }

    @Test
    void shouldRejectUnconfiguredChannel() {
        when(configLoader.getConfigForChannel("UNKNOWN")).thenReturn(Optional.empty());

        String result = taskExecutor.submitTask("UNKNOWN", "Do something", "U123");

        assertTrue(result.contains("not configured"));
    }

    @Test
    void shouldParseModelFromCommand() {
        assertEquals("opus", taskExecutor.parseModel("--model opus Add feature"));
        assertEquals("haiku", taskExecutor.parseModel("Fix bug --model haiku"));
        assertEquals("sonnet", taskExecutor.parseModel("Simple task"));
    }
}
```

**Step 2: Run test**

Run: `./gradlew test --tests EndToEndFlowTest -i`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/com/autonomous/agent/integration/EndToEndFlowTest.java
git commit -m "test: add integration test for end-to-end flow"
```

---

## Task 12: Clean Up Old Code

**Files:**
- Delete: `src/main/java/com/autonomous/agent/service/ClaudeAgentService.java`
- Delete: `src/main/java/com/autonomous/agent/service/SelfImprovementService.java`

**Step 1: Remove deprecated services**

These are replaced by TaskExecutorService. Delete the files:

```bash
rm src/main/java/com/autonomous/agent/service/ClaudeAgentService.java
rm src/main/java/com/autonomous/agent/service/SelfImprovementService.java
```

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor: remove deprecated ClaudeAgentService and SelfImprovementService"
```

---

## Task 13: Final Build and Test

**Step 1: Run full test suite**

```bash
./gradlew clean test
```

Expected: All tests pass

**Step 2: Build JAR**

```bash
./gradlew bootJar
```

Expected: JAR created in `build/libs/`

**Step 3: Commit any fixes**

If any tests fail, fix and commit.

---

## Summary

| Task | Component | Purpose |
|------|-----------|---------|
| 1 | ChannelConfig | Model for per-channel project settings |
| 2 | ConfigLoaderService | Load YAML channel configs |
| 3 | ThreadManagerService | Manage Slack threads |
| 4 | CostTrackerService | Track costs and budget |
| 5 | GitService | Branch/PR management |
| 6 | TaskExecutorService | Async task execution with queue |
| 7 | SlackController | Updated endpoints |
| 8 | application.properties | Configuration |
| 9 | Dockerfile | Railway deployment |
| 10 | Example config | Template for users |
| 11 | Integration test | End-to-end validation |
| 12 | Cleanup | Remove old code |
| 13 | Final build | Verify everything works |

**Total: 13 tasks, ~45 steps**
