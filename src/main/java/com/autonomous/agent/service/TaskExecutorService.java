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
            model = config.getDefaultModel();
        }
        String description = stripModelFlag(command);

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

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
            executeTask(execution, config), executor);

        execution.setFuture(future);

        threadManager.postStarting(channelId, threadTs, model);

        return String.format("Starting task in thread. Model: %s", model);
    }

    private String executeTask(TaskExecution execution, ChannelConfig config) {
        try {
            String branchName = gitService != null ? gitService.generateBranchName(
                config.getChannelName(),
                execution.getTaskId(),
                config.getBranchPrefix() != null ? config.getBranchPrefix() : "agent"
            ) : "agent/" + execution.getTaskId();
            execution.setBranchName(branchName);

            if (gitService != null) {
                gitService.createBranch(config.getClonePath(), branchName);

                if (config.getSetupCommands() != null) {
                    for (String cmd : config.getSetupCommands()) {
                        runCommand(config.getClonePath(), cmd);
                    }
                }
            }

            String result = callClaudeCode(execution, config);

            String diffStats = gitService != null ?
                gitService.getDiffStats(config.getClonePath(), config.getPrTarget()) : "unknown";

            String testResults = gitService != null ?
                gitService.runTests(config.getClonePath(), "./gradlew test") : "skipped";

            if (gitService != null) {
                gitService.commitAll(config.getClonePath(), "feat: " + execution.getDescription());
                gitService.push(config.getClonePath(), branchName);
            }

            String prUrl = gitService != null ? gitService.createPullRequest(
                config.getClonePath(),
                execution.getDescription(),
                "Automated PR from Slack agent\n\n" + result,
                config.getPrTarget()
            ) : null;

            if (costTracker != null) {
                long estimatedInputTokens = execution.getDescription().length() * 2;
                long estimatedOutputTokens = result.length();
                var costEntry = costTracker.recordCost(
                    execution.getChannelId(),
                    execution.getTaskId(),
                    execution.getModel(),
                    estimatedInputTokens,
                    estimatedOutputTokens
                );

                threadManager.postCompletion(
                    execution.getChannelId(),
                    execution.getThreadTs(),
                    result.substring(0, Math.min(500, result.length())),
                    costTracker.formatCostSummary(costEntry),
                    diffStats,
                    testResults,
                    prUrl != null ? prUrl : "PR creation failed"
                );
            }

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
